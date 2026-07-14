<claude-mem-context>
# Memory Context

# [pindorama] recent context, 2026-07-14 8:38am GMT-3

Legend: 🎯session 🔴bugfix 🟣feature 🔄refactor ✅change 🔵discovery ⚖️decision
Format: ID TIME TYPE TITLE
Fetch details: get_observations([IDs]) | Search: mem-search skill

Stats: 50 obs (13,697t read) | 162,173t work | 92% savings

### Jul 10, 2026
S262 Pindorama booking system — resuming queued prompts, implementing SlotPicker island + appointment views, and designing 3 new epics (commission/rent, platform billing, events) (Jul 10, 8:36 AM)
4072 8:54a 🟣 View admin/professionals criada para listagem e verificação de terapeutas
4073 " 🟣 View admin/practices/index criada com listagem hierárquica de práticas
4074 " 🟣 View admin/practices/form criada como form unificado de criação e edição de práticas
4075 8:55a 🔄 Views admin/featured e admin/payments migradas de Product/Order para Service/Appointment
4076 8:58a ⚖️ Infraestrutura monorepo descartada — usar Docker existente do meada
4077 8:59a 🟣 Migration de billing adicionada à tabela users — projeto pindorama
4078 " 🟣 Tabela platform_charges criada — cobranças da plataforma ao terapeuta
4079 9:00a 🟣 Model PlatformCharge criado — Eloquent model para cobranças da plataforma
4080 " 🟣 BillingService implementado — lógica de cobrança da plataforma ao terapeuta
4081 " ✅ Campos billing_* adicionados ao fillable do Model User — pindorama
4082 " ✅ Casts de billing adicionados ao Model User — pindorama
4083 9:01a ✅ Relação charges() adicionada ao Model User — pindorama
4084 " 🟣 Rotas admin de cobrança (Epic B) adicionadas ao web.php — pindorama
4085 " 🟣 Rotas de cobranças no painel do terapeuta adicionadas — pindorama Epic B
4086 " 🟣 Admin\ProfessionalController expandido com actions de billing — pindorama Epic B
4087 9:02a 🟣 Professional\ChargeController criado — terapeuta visualiza e paga cobranças da plataforma
4088 " 🟣 View admin/professional-show criada — painel de gerenciamento de cobrança do terapeuta
4089 " 🟣 View professional/charges/index criada — terapeuta visualiza e paga suas cobranças
4090 " ✅ Aba "Cobranças" adicionada ao nav do painel do terapeuta — pindorama
4091 9:03a ✅ Link "gerenciar" adicionado na listagem de terapeutas do admin — pindorama
4092 " 🔵 Epic B billing validado end-to-end — todos os cenários passaram
4093 " 🔵 Suite de testes e route:list OK após Epic B — pindorama
4094 9:04a 🟣 Migration da tabela events criada — eventos do terapeuta (Epic C)
4095 9:05a 🟣 Migration event_registrations criada — inscrições em eventos com pagamento e controle de vagas
4096 " 🟣 Model Event criado — Eloquent model para eventos do terapeuta com controle de vagas
4097 " 🟣 Model EventRegistration criado — inscrição em evento com status de presença e pagamento MP
4098 " 🟣 EventFullException criada — exceção tipada para evento sem vagas (→ 409)
4099 9:06a 🟣 EventService implementado — inscrição transacional com advisory lock PostgreSQL para controle de vagas
4100 " ✅ Relação events() adicionada ao Model User — pindorama Epic C
4101 " 🟣 Rotas públicas de eventos adicionadas ao web.php — pindorama Epic C
4102 " 🟣 Rotas autenticadas de inscrição em eventos adicionadas — pindorama Epic C
4103 9:07a 🟣 Rotas CRUD de eventos no painel do terapeuta adicionadas — pindorama Epic C
4104 " 🟣 EventController (storefront) criado — listagem pública, inscrição e pagamento de inscrição
4105 " 🟣 Professional\EventController criado — CRUD de eventos do terapeuta com slug único e herança de timezone
4106 " 🟣 View events/index criada — listagem pública de eventos em grid com vagas e preço
4107 9:08a 🟣 View events/show criada — página pública do evento com formulário de inscrição adaptativo
4108 " 🟣 View events/registration criada — comprovante de inscrição com botão de pagamento condicional
4109 " 🟣 View professional/events/index criada — painel de eventos do terapeuta com contador de inscritos
4110 " 🟣 View professional/events/form criada — formulário único de criar/editar evento com datetime no timezone do terapeuta
4111 9:09a 🟣 View professional/events/registrations criada — lista de inscritos por evento para o terapeuta
4112 " 🟣 EventReminderMail criado — mailable de lembrete de evento para inscritos
4113 " 🟣 Sistema de lembretes de eventos implementado — email template + command events:remind
4114 9:10a ✅ Aba "Eventos" adicionada ao nav do painel do terapeuta — pindorama Epic C
4115 " ✅ Link "Eventos" adicionado ao footer do storefront — pindorama Epic C
4116 " ✅ Seed de eventos adicionado ao DatabaseSeeder — dois eventos demo para teste do Epic C
4117 " 🔵 Epic C validado — migrations, lint, páginas públicas de eventos e contagem de vagas OK
4118 9:11a 🔵 Epic C validado end-to-end — inscrição, capacidade, cálculo de desconto e lembretes todos passaram
4119 " 🔴 Filtro de janela do events:remind corrigido — lógica diffInHours substituída por lessThanOrEqualTo
4120 9:12a 🔵 Filtro de janela do events:remind validado — envia apenas para eventos dentro da janela, ignora fora
4121 9:13a 🔵 Padrão de integração de external_projects no meada docker-compose descoberto via projeto muda
S263 Implementação completa do projeto pindorama (marketplace de terapias) — Epics B (cobrança da plataforma) e C (eventos) + integração Docker no meada (Jul 10, 9:13 AM)
**Investigated**: Padrão de integração de external_projects no meada docker-compose foi investigado via projeto muda: 3 serviços por projeto (postgres, app PHP-FPM, nginx) + entrada no Caddyfile por subdomínio. Estrutura de rotas, models e controllers já existentes no pindorama foram revisados para extensão.

**Learned**: - Padrão Docker do meada para external_projects: {projeto}-postgres + {projeto}-app (PHP-FPM com volume mount) + {projeto}-nginx (nginx com docker/nginx/default.conf) + entrada Caddy por subdomínio
    - Wildcard cert no Caddyfile.prod quebra subdomínios específicos (erro 525 Cloudflare) — cada projeto precisa de entrada individual
    - pindorama usa PHP attribute #[Fillable] em vez de $fillable tradicional do Laravel
    - EventService usa pg_advisory_xact_lock para controle de vagas transacional no PostgreSQL
    - BillingService é idempotente: gera mensalidade uma vez por mês por terapeuta
    - Model Event usa cast UtcDateTime customizado para starts_at/ends_at
    - isPaid() em PlatformCharge verifica 'paid'/'waived'; em EventRegistration verifica 'approved'/'authorized' (status nativos do Mercado Pago)

**Completed**: EPIC B — Cobrança da plataforma ao terapeuta:
    - Migration add_billing_to_users (billing_monthly_fee, discount_percent, billing_free, billing_active, billing_day)
    - Migration create_platform_charges_table (subscription/registration/featured, pending/paid/waived)
    - Model PlatformCharge com constantes TYPES/STATUSES, isPaid(), accessors de label
    - BillingService com generateMonthly() idempotente e createCharge() avulso
    - User model atualizado: campos billing_* no fillable, casts e relação charges()
    - Admin\ProfessionalController expandido com show(), updateBilling(), generateMonthly(), createCharge(), chargeStatus()
    - Professional\ChargeController com index() e pay() (simulado via MP)
    - View admin/professional-show.blade.php (config billing + geração + histórico com ações inline)
    - View professional/charges/index.blade.php
    - Rotas admin e tenant para billing
    - Nav do dashboard: aba "Cobranças"
    - Validado: R$100−10%=R$90 pending; gratuidade→R$0 waived; pagamento simulado→paid

    EPIC C — Eventos (rodas/cursos/certificações):
    - Migration create_events_table (type/modality/status/capacity/price/reminder_hours)
    - Migration create_event_registrations_table (status com 'attended', payment MP, reminded)
    - Model Event com scopes published()/upcoming(), spots_left accessor, isFull(), UtcDateTime cast
    - Model EventRegistration com STATUSES incluindo 'attended'
    - EventFullException (→ 409 sold_out)
    - EventService com register() transacional (pg_advisory_xact_lock) e price()
    - EventController (storefront): index, show, register, registration, pay
    - Professional\EventController: CRUD completo + registrations() + uniqueSlug()
    - Views: events/index, events/show, events/registration, professional/events/index, professional/events/form, professional/events/registrations
    - EventReminderMail + emails/event-reminder.blade.php
    - Command events:remind com filtro de janela corrigido (lessThanOrEqualTo)
    - Seed de 2 eventos demo (roda gratuita Ana, curso pago Bruno)
    - Nav dashboard: aba "Eventos"; footer: link /eventos
    - Rotas públicas e autenticadas de inscrição; rotas tenant e admin de gestão
    - Validado: gratuito→confirmed/approved; R$150−10%=R$135; vaga única bloqueia 2º (sold_out); lembrete enviado apenas dentro da janela
    - Suite PHPUnit: 10/10 passou após todos os epics

**Next Steps**: Decisão pendente do usuário sobre:
    1. Wiring no docker-compose do meada + Caddy (P11) — espelhando padrão do muda com serviços pindorama-postgres, pindorama-app, pindorama-nginx + entrada Caddyfile. Não aplicado sem aprovação.
    2. Commit do pindorama no monorepo meada (nada commitado ainda, diretório untracked).
    Usuário foi perguntado se quer (a) docker + commit, (b) só commit, ou (c) deixar com ele.


Access 162k tokens of past work via get_observations([IDs]) or mem-search skill.
</claude-mem-context>