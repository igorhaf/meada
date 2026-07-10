# 🍬 Semente Doce — Doceria & Salgaderia artesanal

Loja virtual de uma **doceria & salgaderia** (não é marketplace: uma loja, uma cozinha). Vitrine
de doces e salgados, sacola, checkout com **Mercado Pago**, **entrega local por bairro** (estilo
iFood) e dois recursos que são o coração do negócio:

- 🎂 **Encomendas** — o cliente descreve o que quer (bolo de aniversário, mesa de salgados),
  agenda a data, e a doceria **orça e confirma** num Kanban no painel.
- 🎁 **Montagem de Kits** — no painel, a doceria monta kits/cestas a partir dos produtos do
  cardápio (o "Montador"), define o preço e publica na vitrine.

## Stack

| Camada | Tecnologia |
|--------|-----------|
| Backend | **Laravel 13** (PHP 8.3) |
| Banco | **PostgreSQL 16** |
| Renderização | **SSR com Blade** (bom para SEO) |
| Interatividade | **Vue 3** como _islands_ dentro do Blade |
| Estilo | **Tailwind CSS v4** (paleta framboesa + caramelo + pistache) |
| Build | **Vite 8** |
| Pagamento | **Mercado Pago** — Checkout Transparente (PIX, cartão, boleto) |
| Orquestração | **Docker Compose** (postgres + php-fpm + nginx) |

Mesma fundação do projeto irmão `muda` (SSR + ilhas Vue), sem a camada de marketplace.

## Como rodar

O semente-doce **não tem stack própria**: ele roda como um serviço do ambiente Docker do
**meada** (`semente-app` + `semente-nginx` + `semente-postgres`), na mesma rede e atrás do
proxy `meada-caddy` — do mesmo jeito que o `muda` e o `conexao-municipio`. Não há portas
expostas no host; o acesso é pelo subdomínio.

```bash
# (1x) apontar o subdomínio no /etc/hosts do host
echo "127.0.0.1 semente-doce.meadadigital.local" | sudo tee -a /etc/hosts

# 1. Env do app (DB_HOST=semente-postgres já vem certo)
cp .env.example .env

# 2. Subir TODO o ambiente do meada — o semente sobe junto
cd /home/igorhaf/meada && ./scripts/meada-up.sh

# 3. App key + assets + migração (dentro do container semente-app)
docker compose exec semente-app php artisan key:generate
docker compose exec semente-app sh -c "npm install && npm run build"
docker compose exec semente-app php artisan migrate:fresh --seed
docker compose exec semente-app php artisan storage:link
```

Acesse **http://semente-doce.meadadigital.local**.

Banco dedicado no container `semente-postgres` (db `semente_doce`, **sem porta no host** —
igual ao muda). O proxy `meada-caddy` (portas 80/443) roteia o subdomínio para o `semente-nginx`.

### Contas de demonstração (senha: `password`)

| Papel | E-mail | Acesso |
|-------|--------|--------|
| **root** (a doceria) | `root@sementedoce.com.br` | Painel `/painel` — cardápio, kits, encomendas, pedidos |
| **cliente** | `cliente@sementedoce.com.br` | Sacola, meus pedidos, minhas encomendas |

## Domínio

```
Category (árvore) ─┬─ Product (unidade/cento/dúzia; pronta-entrega × sob encomenda; opções iFood)
                   │     └─ ProductOptionGroup → ProductOption (recheio, cobertura, tamanho…)
                   └─ Kit (montado no painel) → KitItem (componentes com snapshot)
Order → OrderItem (produto OU kit; snapshots)      DeliveryZone (bairro → taxa + ETA)
CustomOrder (encomenda: solicitada → orçada → confirmada → produção → pronta → entregue)
Banner · SiteSetting · Page · ContactMessage
```

## Pagamento (Mercado Pago)

Checkout Transparente (Payment Brick) idêntico ao `muda`: preços **recalculados no servidor**,
webhook servidor-a-servidor que revalida o pagamento na API do MP. Configure as credenciais de
**teste** em `.env` (`MP_ENABLED=true`, `MP_ACCESS_TOKEN`, `MP_PUBLIC_KEY`). Sem credenciais, o
checkout cai no **pagamento simulado** para a demo funcionar.

## Entrega local

Nada de frete nacional: retirada na loja (grátis) ou entrega por **bairro** (tabela de zonas no
painel, com fallback para a taxa padrão), pedido mínimo, entrega grátis acima de um valor e
janela de ETA. Tudo em `config/delivery.php`.

## Recursos-estrela

### 🎂 Encomendas
Formulário público em `/encomendas` (o cliente não precisa estar logado). A encomenda nasce
`solicitada`; no painel (`/painel/encomendas`) a doceria vê um **Kanban**, define o **orçamento**
e avança o status até `entregue`.

### 🎁 Montador de Kits
Em `/painel/kits`, a doceria escolhe produtos do cardápio, define quantidades (o Montador soma o
valor dos componentes e sugere o preço), arbitra o preço final do kit e publica. Na vitrine o kit
vira um card comprável — no checkout ele reaproveita a sacola e o Mercado Pago sem mexer em nada.

## Próximas fases (sugeridas)

1. **Upload de imagens** (hoje o cadastro usa URL / placeholder SVG).
2. **Notificação por WhatsApp** ao mudar status de pedido/encomenda.
3. **Cupons e fidelidade** (cartãozinho de selos da doceria).
4. **Avaliações** de produtos e kits.
5. **Agenda de produção** (capacidade por dia para encomendas).
6. Verificação de e-mail e recuperação de senha.
