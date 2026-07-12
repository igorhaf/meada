#!/usr/bin/env bash
# =============================================================================
# Migração do muda no SERVIDOR: monorepo → repositório próprio + homolog.
#
# CONTEXTO DO PERIGO (leia antes de rodar):
#   O commit `ea9fc4c` do meada tira os 192 arquivos do muda do índice. No servidor,
#   o `git reset --hard origin/main` do deploy vai APAGAR esses arquivos rastreados.
#   Sem `docker/php/Dockerfile`, o build do muda-app falha; e como o Caddy tem
#   `depends_on: muda-nginx`, o `docker compose up -d --build` aborta e derruba o
#   meadadigital.com INTEIRO — não só o muda.
#
#   Este script fecha essa janela: logo após o reset, restaura os MESMOS arquivos a
#   partir do repositório novo. O que NÃO está em git (o .env, as 52 imagens de
#   produto, o public/build, o vendor/ e o node_modules/) SOBREVIVE — o `reset --hard`
#   não toca em arquivo untracked/ignored.
#
#   O perigo existe UMA VEZ SÓ: depois disso a pasta é ignorada e o reset nunca mais
#   a alcança.
#
# PRÉ-REQUISITOS (nesta ordem):
#   1. O auto-deploy do meada tem que estar DESLIGADO (senão o push dispara o deploy
#      destrutivo antes deste script rodar).
#   2. O commit ea9fc4c do meada já empurrado para origin/main.
#   3. O repo github.com/igorhaf/muda existe com as branches main e homolog.
#
# USO:  bash scripts/migrar-muda-servidor.sh
# =============================================================================
set -euo pipefail

MEADA_DIR=/opt/meada
MUDA_REPO=https://github.com/igorhaf/muda.git
PROD_DIR="$MEADA_DIR/external_projects/muda"
HOMOLOG_DIR="$MEADA_DIR/external_projects/mudahomolog"
BACKUP="/root/muda-backup-$(date +%Y%m%d-%H%M%S)"

echo "==> [0/6] Backup completo da pasta atual (rede de segurança)"
cp -a "$PROD_DIR" "$BACKUP"
echo "    backup em: $BACKUP"
echo "    imagens no backup: $(find "$BACKUP/storage/app/public" -type f 2>/dev/null | grep -vc gitignore || echo 0)"

echo "==> [1/6] Inventário do que NÃO está em git (tem que sobreviver)"
IMGS_ANTES=$(find "$PROD_DIR/storage/app/public" -type f 2>/dev/null | grep -vc gitignore || echo 0)
echo "    imagens de produto: $IMGS_ANTES"
[ -f "$PROD_DIR/.env" ] && echo "    .env: presente"
[ -d "$PROD_DIR/public/build" ] && echo "    public/build: presente"

echo "==> [2/6] Meada: puxa o commit que remove o muda do índice (JANELA DE RISCO ABRE)"
cd "$MEADA_DIR"
git fetch origin main
git reset --hard origin/main
echo "    (os arquivos rastreados do muda foram apagados agora — restaurando já)"

echo "==> [3/6] Muda: a pasta vira um CLONE do repositório novo (JANELA DE RISCO FECHA)"
cd "$PROD_DIR"
if [ ! -d .git ]; then
  git init -b main -q
  git remote add origin "$MUDA_REPO"
fi
git fetch origin main -q
git reset --hard origin/main
git branch --set-upstream-to=origin/main main -q 2>/dev/null || true
echo "    HEAD do muda: $(git log --oneline -1)"

echo "==> [4/6] Confere que o que não estava em git sobreviveu"
IMGS_DEPOIS=$(find "$PROD_DIR/storage/app/public" -type f 2>/dev/null | grep -vc gitignore || echo 0)
echo "    imagens de produto: $IMGS_DEPOIS (antes: $IMGS_ANTES)"
if [ "$IMGS_DEPOIS" != "$IMGS_ANTES" ]; then
  echo "    !! DIVERGÊNCIA nas imagens — restaure do backup: $BACKUP"
  exit 1
fi
[ -f "$PROD_DIR/.env" ] || { echo "    !! .env SUMIU — restaure de $BACKUP/.env"; exit 1; }
[ -f "$PROD_DIR/public/build/manifest.json" ] || echo "    (aviso) public/build ausente — rebuildar assets"
echo "    OK: .env, imagens e assets intactos"

echo "==> [5/6] Homolog: clona a branch homolog (ambiente novo)"
if [ ! -d "$HOMOLOG_DIR/.git" ]; then
  git clone -q -b homolog "$MUDA_REPO" "$HOMOLOG_DIR"
  echo "    clonado em $HOMOLOG_DIR"
else
  cd "$HOMOLOG_DIR" && git fetch origin homolog -q && git reset --hard origin/homolog -q
  echo "    já existia — atualizado"
fi
if [ ! -f "$HOMOLOG_DIR/.env" ]; then
  cp "$HOMOLOG_DIR/.env.example" "$HOMOLOG_DIR/.env"
  echo "    !! .env de homolog criado do example — VOCÊ PRECISA PREENCHER:"
  echo "       APP_URL=https://mudahomolog.meadadigital.com"
  echo "       DB_HOST=mudahomolog-postgres  DB_DATABASE/USERNAME/PASSWORD do homolog"
  echo "       APP_KEY (php artisan key:generate), Mercado Pago (sandbox), Google OAuth, MelhorEnvio"
  echo "    >> Preencha o .env e rode este script de novo (ele é idempotente daqui pra frente)."
  exit 0
fi

echo "==> [6/6] Sobe os serviços (muda + homolog). O resto da stack não é derrubado."
cd "$MEADA_DIR"
docker run --rm -v "$HOMOLOG_DIR":/app -w /app node:20-alpine sh -lc "npm ci --no-audit --no-fund && npm run build"
docker compose -f docker-compose.yml up -d --build \
  muda-app muda-nginx mudahomolog-postgres mudahomolog-app mudahomolog-nginx caddy
docker compose -f docker-compose.yml exec -T mudahomolog-app php artisan migrate --force
docker compose -f docker-compose.yml exec -T mudahomolog-app php artisan storage:link || true

echo
echo "==> PRONTO. Verifique:"
echo "    curl -I https://muda.meadadigital.com          # prod intacto"
echo "    curl -I https://mudahomolog.meadadigital.com  # homolog no ar (precisa do DNS)"
echo "    curl -I https://meadadigital.com               # meada intacto"
echo
echo "    Backup preservado em: $BACKUP (só apague depois de validar)"
echo "    NÃO ESQUEÇA de religar o auto-deploy do meada."
