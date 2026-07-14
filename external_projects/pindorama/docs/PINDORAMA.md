# Pindorama — Especificação do sistema

> **Atualização de 14/07/2026:** a implementação atual foi ampliada para governança exclusiva do
> root, eventos com múltiplos instrutores e encontros, agenda global, conflitos por profissional e
> sala, checkout real de eventos, razão financeiro unificado, repasses, passaporte/QR, check-in,
> notificações e direitos de privacidade. O relatório vigente e validado é
> [`RELATORIO_ADERENCIA_PRODUTO.md`](../RELATORIO_ADERENCIA_PRODUTO.md). As seções históricas abaixo
> permanecem como registro da especificação de origem e não devem ser usadas para inferir lacunas no
> estado atual.

> **Recuperado em 2026-07-10** das sessões próprias do projeto pindorama
> (`~/.claude/projects/-home-igorhaf-meada-external-projects-pindorama/`), depois que o
> prompt-mestre e a fila de prompts que definiam o sistema quebraram no meio da execução.
> Fontes: o **prompt fundador** (abaixo, verbatim) + o **contrato do workflow
> `pindorama-storefront-rewire`** (que rodou e completou) + as observações do claude-mem
> da sessão de 2026-07-10 (08:40–11:07). Este arquivo é a fonte de verdade do domínio —
> se algo divergir do código, o código foi consertado depois; atualize aqui.

## O que é

**Marketplace multi-tenant de serviços de saúde integrativa.** NÃO é loja virtual / e-commerce
(isso é o muda, de onde o pindorama foi clonado em stack). Cada **tenant é um terapeuta /
profissional de saúde** (acupuntura, reiki, ayurveda, Medicina Tradicional Chinesa, e demais
práticas integrativas) que oferece **serviços** agendáveis, tem suas **especialidades**, seus
**locais de atendimento** e uma **identidade própria** (banner + landing page individual).

O eixo do produto é o **calendário/agendamento** (não o carrinho): o cliente reserva uma
consulta; o profissional gerencia sua agenda, disponibilidade e bloqueios. A plataforma cobra
**comissão** sobre os agendamentos e emite **cobranças** ao profissional.

### Prompt fundador (verbatim, linha 7 da sessão `cb5fd6b2`)

> vamos criar um projeto muito semelhante em termos de stack, ao ~/meada/external_projects/muda
> sendo que esse não será uma loja virtual comum, será no mesmo esquema arquitetônico, porém será
> um tipo de marketplace de serviços, com calendário, e tenants, cada tenant tem seus serviços
> (práticas integrativas, terapias tradicionais chinesa e etc) pra cada tenant, teremos um banner
> superior, meio que um pouco de identidade própria para cada terapeuta usando uma landing page ou
> algo do tipo, o calendário obviamente, servirá tanto para consulta para reserva do usuário, quanto
> para consulta do tenant (neste caso, profissionais de saúde deste seguimento e suas especialidades)
> use o mesmo esquema de pagamentos do muda tb

## Papéis (roles)

- **Cliente/usuário** (público): busca terapeutas/serviços, vê a landing do terapeuta, agenda e paga.
- **Profissional/terapeuta** (tenant): gerencia serviços, especialidades, locais, disponibilidade,
  bloqueios, agenda, eventos; vê e paga as cobranças da plataforma.
- **Root/admin** (plataforma): categorias de serviço, banners, salas, regras de comissão,
  pagamentos/relatórios, configuração do site, mensagens de contato.

## Modelo de dados (migrations recuperadas)

| Tabela | Papel |
|--------|-------|
| `users` (+ role, + professional_fields, + social_login, + billing) | clientes, profissionais e root; campos de profissional (slug, verificação) e de billing |
| `service_categories` | categorias hierárquicas de serviço (práticas), slug único |
| `services` | serviços agendáveis do profissional (preço, `compare_at_price`, `bookings_count`) |
| `service_images` | imagens do serviço |
| `professional_specialties` | especialidades do profissional |
| `attendance_locations` + `attendance_location_service` | locais de atendimento e o pivô local↔serviço |
| `professional_availabilities` | janelas de disponibilidade do profissional |
| `availability_blocks` | bloqueios pontuais na agenda |
| `appointments` | agendamentos (o núcleo); guarda o split financeiro congelado |
| `rooms` | salas físicas (usadas na precedência de comissão) |
| `commission_rules` | regras de comissão por escopo, com precedência |
| `platform_charges` | cobranças da plataforma ao profissional |
| `events` + `event_registrations` | eventos do terapeuta e inscrições |
| `banners`, `site_settings`, `pages`, `contact_messages` | CMS/institucional (herdados do muda) |

## Financeiro

- **Comissão** (`CommissionService` + `commission_rules`): a taxa da plataforma resolve por
  **precedência de escopo** (ex.: regra da sala vence a regra default). No momento do agendamento
  o **split é congelado** no `appointment` (valor da plataforma × líquido do profissional).
- **Cobrança da plataforma** (`BillingService` + `PlatformCharge` + `Professional\ChargeController`):
  a plataforma gera cobranças ao profissional; ele visualiza e paga em `/cobrancas`.
- **Pagamento do cliente** = **mesmo esquema do muda**: Mercado Pago Checkout Transparente
  (Payment Brick, cartão/PIX na própria página). Ver `docs/INTEGRACOES.md`.

## Superfície (rotas principais)

- **Público:** `/`, `/busca`, `/api/busca/sugestoes`, `/agendar`, `/evento/{slug}`,
  `/evento/{slug}/inscrever`, `/entrar`, `/cadastrar`, `/auth/google/{redirect,callback}`,
  `/contato`, `/central-de-ajuda`.
- **Agenda (profissional):** `/agenda`, `/api/agenda/{horarios,eventos}`, `/disponibilidade`,
  `/bloqueios`, `/agendamentos/{id}/{confirmar,concluir,cancelar}`, `/locais[/novo|/{id}/editar]`,
  `/eventos[/novo|/{id}/editar|/{id}/inscritos]`, `/cobrancas`, `/cobrancas/{charge}/{pagar,status}`.
- **Admin/root:** `/comissao`, `/banners[...]`, `/destaques` (+ toggle), `/config`, `/mensagens`
  (+ `/{id}/lida`), categorias de serviço, pagamentos.

## Stack (idêntica ao muda)

Laravel 13 (PHP 8.3) · PostgreSQL 16 · Blade SSR + Vue 3 "islands" (montados via `resources/js/app.js`
lendo `[data-island]` + `data-props`) · Tailwind CSS v4 · Vite 8 · pt-BR. Reusa as classes utilitárias
do muda (`container-muda`, `card`, `chip`, `btn-brand`, `btn-outline`, `menu-item`, `row-track`…).

- **APP_NAME:** `Pindorama` · **DB:** `pindorama` · **porta interna:** `8096`.

## Integração no meada (estado-alvo)

Decisão cravada na sessão (obs #4076 do claude-mem e prompt "não é pra criar essa infra monorepo,
já vamos usar a do docker de meada"): o pindorama roda no **docker-compose do meada**, como projeto
externo, acessível via Caddy em `pindorama.meadadigital.local` (dev) e `pindorama.meadadigital.com`
(prod) — mesmo padrão do muda / semente-doce (php-fpm + nginx + Postgres dedicado, sem portas no host).

## Estado — FECHADO em 2026-07-10

**Base construída na sessão original:** domínio migrado no model/DB; workflow
`pindorama-storefront-rewire` completou (Home/vitrine reescritas — "Explore por prática",
"Terapeutas em destaque"); comissão, billing, appointments, eventos, especialidades.

**Fechamento (recuperação):**
- [x] Integrado no docker-compose do meada + rota no Caddy (dev/prod) + subdomínio próprio
      (`pindorama-postgres`/`-app`/`-nginx`). Verificado: `pindorama.meadadigital.local` → 200,
      title "Terapias integrativas e práticas de bem-estar".
- [x] Resíduo do muda limpo: README reescrito; `docs/INTEGRACOES.md` sem a seção de
      frete/Melhor Envio; `ngrok-muda.sh` removido (era do muda — mirava o container
      `muda-app`, túnel/domínio ngrok próprios).
- [x] Suíte phpunit **verde**: 10 passed / 15 assertions, incluindo `BookingEngineTest`
      (materialização de `end_at`, conflito de slot, back-to-back, janela de horário, aceite
      gate, blocks). Os "erros anteriores" NÃO deixaram o código quebrado.

**Polimento futuro (não bloqueante):** a terminologia herdada do muda (`INTEGRACOES.md` e
views de pagamento) ainda diz "loja/pedido" — trocar por "agendamento/consulta" quando
conveniente. O `phpunit.xml` aponta o DB de teste pra `127.0.0.1:5441` (setup antigo); no
docker do meada a suíte roda com override `DB_HOST=pindorama-postgres DB_PORT=5432
DB_DATABASE=pindorama_test`.
