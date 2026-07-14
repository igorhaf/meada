#!/usr/bin/env bash
# =============================================================================
# Migração do muda no SERVIDOR: monorepo → repositório próprio + ambiente mudahomolog.
#
# PRINCÍPIO DESTE SCRIPT: **NADA É APAGADO.**
#
#   O perigo original: o commit do meada tira os 192 arquivos do muda do índice, e o
#   `git reset --hard` do deploy APAGARIA esses arquivos rastreados do disco.
#
#   Aqui isso não acontece. Em vez de deixar o git apagar e depois restaurar, a pasta
#   inteira é MOVIDA para fora do alcance do git (`mv`, atômico, mesma partição), o git
#   sincroniza (sem ter o que apagar — o caminho nem existe), e a pasta é MOVIDA DE VOLTA,
#   byte a byte idêntica. As imagens e o .env não são tocados em momento algum.
#
#   Bônus: os bind mounts do Docker seguem o inode, não o caminho — os containers
#   continuam servindo o site durante o `mv`. Downtime esperado: ZERO.
#
# O QUE É SAGRADO E COMO ESTÁ PROTEGIDO:
#   · IMAGENS (storage/app/public) — nunca tocadas pelo git (são ignoradas). Ainda assim:
#     tarball + manifest md5 antes, e CONFERÊNCIA por contagem E checksum depois. O script
#     ABORTA se um único byte divergir.
#   · BANCO — hoje vive no Supabase (mudaprod/Frankfurt). Este script NÃO ENCOSTA no banco.
#     Nenhum migrate, nenhum drop, nenhum restore em produção.
#   · .env de produção — nunca tocado (é ignorado pelo git). Backup mesmo assim.
#
# PRÉ-REQUISITOS:
#   1. Auto-deploy do meada DESLIGADO (senão o deploy faz `compose down` + `up --build` e,
#      se algo falhar no build, a stack inteira fica no chão).
#   2. Commit do meada já em origin/main.
#   3. DNS mudahomolog.meadadigital.com em DNS-only (nuvem CINZA) na Cloudflare.
#
# USO:  bash migrar-muda-servidor.sh
# =============================================================================
set -euo pipefail

MEADA_DIR=/opt/meada
MUDA_REPO=git@github-muda:igorhaf/muda.git   # deploy key read-only no servidor (~/.ssh/config)
PROD_DIR="$MEADA_DIR/external_projects/muda"
HOMOLOG_DIR="$MEADA_DIR/external_projects/mudahomolog"
TS=$(date +%Y%m%d-%H%M%S)
PARKING="/opt/muda-live-$TS"        # mesma partição que /opt/meada → mv é atômico e instantâneo
BACKUP="/root/muda-backup-$TS"

abortar() { echo "!! ABORTADO: $1"; echo "   A pasta original está em: $PARKING (se o mv já rodou)"; exit 1; }

echo "==> [1/7] Fotografia do que é SAGRADO (antes de qualquer coisa)"
cd "$PROD_DIR"
IMGS=$(find storage/app/public -type f ! -name '.gitignore' | wc -l)
find storage/app/public -type f ! -name '.gitignore' -exec md5sum {} \; | sort -k2 > "/tmp/manifest-antes-$TS.txt"
SUM_ANTES=$(md5sum "/tmp/manifest-antes-$TS.txt" | cut -d' ' -f1)
echo "    imagens: $IMGS arquivos | checksum do conjunto: $SUM_ANTES"
[ -f .env ] || abortar ".env de produção não encontrado"
echo "    .env: presente | public/build: $([ -d public/build ] && echo presente || echo AUSENTE)"

echo "==> [2/7] Backup independente (tarball das imagens + cópia da pasta + .env)"
tar czf "/root/muda-images-$TS.tar.gz" storage/app/public
cp -a "$PROD_DIR" "$BACKUP"
cp .env "/root/muda-env-$TS"
echo "    /root/muda-images-$TS.tar.gz ($(du -h "/root/muda-images-$TS.tar.gz" | cut -f1))"
echo "    $BACKUP (cópia completa) | /root/muda-env-$TS"

echo "==> [3/7] Pasta sai do alcance do git (MOVE — não apaga). Containers seguem servindo."
mv "$PROD_DIR" "$PARKING"
[ -d "$PARKING" ] || abortar "o mv falhou"
echo "    $PROD_DIR → $PARKING"

echo "==> [4/7] Meada sincroniza (não há o que apagar: o caminho não existe mais)"
cd "$MEADA_DIR"
git fetch origin main
git reset --hard origin/main
echo "    meada em: $(git log --oneline -1)"

echo "==> [5/7] Pasta VOLTA (idêntica) e passa a ser um clone do repo do muda"
mv "$PARKING" "$PROD_DIR"
cd "$PROD_DIR"
if [ ! -d .git ]; then
  git init -b main -q
fi
# set-url SEMPRE (não só na criação): se uma execução anterior deixou um remote antigo
# (ex.: HTTPS, que não autentica no repo privado), ele é corrigido aqui. Idempotente.
git remote add origin "$MUDA_REPO" 2>/dev/null || git remote set-url origin "$MUDA_REPO"
git fetch origin main -q
git reset --hard origin/main          # alinha só os FONTES; .env/imagens são ignorados e ficam
git branch --set-upstream-to=origin/main main -q 2>/dev/null || true
echo "    muda agora em: $(git log --oneline -1)"

echo "==> [6/7] CONFERÊNCIA do que é sagrado (aborta se divergir 1 byte)"
IMGS_DEPOIS=$(find storage/app/public -type f ! -name '.gitignore' | wc -l)
find storage/app/public -type f ! -name '.gitignore' -exec md5sum {} \; | sort -k2 > "/tmp/manifest-depois-$TS.txt"
SUM_DEPOIS=$(md5sum "/tmp/manifest-depois-$TS.txt" | cut -d' ' -f1)
echo "    imagens: $IMGS_DEPOIS (antes: $IMGS) | checksum: $SUM_DEPOIS"
[ "$IMGS_DEPOIS" = "$IMGS" ]       || abortar "contagem de imagens mudou! restaure de $BACKUP"
[ "$SUM_DEPOIS" = "$SUM_ANTES" ]   || abortar "checksum das imagens mudou! restaure de $BACKUP"
[ -f .env ]                        || abortar ".env sumiu! restaure de /root/muda-env-$TS"
grep -q "^DB_HOST=aws-0-eu-central-1" .env || echo "    (aviso) o .env não aponta pro Supabase de Frankfurt"
echo "    ✔ imagens intactas (contagem E checksum), .env intacto"

echo "==> [7/7] Homolog: clona a branch e sobe. NENHUMA operação de banco em produção."
if [ ! -d "$HOMOLOG_DIR/.git" ]; then
  git clone -q -b homolog "$MUDA_REPO" "$HOMOLOG_DIR"
fi
if [ ! -f "$HOMOLOG_DIR/.env" ]; then
  echo "    !! falta o .env de mudahomolog — crie-o e rode de novo (o script é idempotente)."
  echo "       Ele deve apontar para o Supabase 'mudahomolog' (Frankfurt), NUNCA para o de prod."
  exit 0
fi
docker run --rm -v "$HOMOLOG_DIR":/app -w /app node:20-alpine sh -lc "npm ci --no-audit --no-fund && npm run build"
cd "$MEADA_DIR"
# `up -d` SEM `down`: não derruba a stack. Se um build falhar, o que está no ar continua no ar.
docker compose -f docker-compose.yml up -d --build \
  muda-app muda-nginx mudahomolog-postgres mudahomolog-app mudahomolog-nginx
docker compose -f docker-compose.yml restart caddy      # recarrega o Caddyfile (vhost novo)
docker compose -f docker-compose.yml exec -T mudahomolog-app php artisan storage:link || true

echo
echo "==> PRONTO. Verifique:"
echo "    curl -I https://muda.meadadigital.com         # prod (não deve ter mudado nada)"
echo "    curl -I https://mudahomolog.meadadigital.com  # homolog (exige DNS em nuvem CINZA)"
echo "    curl -I https://meadadigital.com              # meada intacto"
echo
echo "    Backups: $BACKUP · /root/muda-images-$TS.tar.gz · /root/muda-env-$TS"
echo "    Só apague depois de validar. E RELIGUE o auto-deploy do meada."
