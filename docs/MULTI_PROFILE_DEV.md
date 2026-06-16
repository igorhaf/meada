# Multi-perfil — desenvolvimento local (camada 7.0)

Meada é um monolito que se apresenta como N produtos verticais ("perfis"). Em produção cada
perfil tem seu subdomínio; em dev local você simula isso com `/etc/hosts` + subdomínios do
`meadadigital.local`.

## Perfis vigentes

| profile_id | Produto       | Subdomínio | Paleta padrão  |
|------------|---------------|------------|----------------|
| `generic`  | Meada         | `meada`    | `meada-default`|
| `legal`    | ProcessoBot   | `processo` | `indigo`       |
| `dental`   | DentalBot     | `dental`   | `celeste`      |
| `sushi`    | SushiBot      | `sushi`    | `tijolo`       |

Fonte de verdade: `src/main/java/com/meada/whatsapp/profiles/ProfileType.java` +
`frontend/lib/profiles/profile-type.ts` (paridade garantida pelo `ProfileTypeParityTest`).

## `/etc/hosts` (uma vez)

```
127.0.0.1 meadadigital.local
127.0.0.1 meada.meadadigital.local
127.0.0.1 processo.meadadigital.local
127.0.0.1 dental.meadadigital.local
127.0.0.1 sushi.meadadigital.local
```

(no WSL/Linux: `sudo nano /etc/hosts`.)

## Como rodar

- `cd frontend && npm run dev` (porta 3000).
- Acesse:
  - `http://meadadigital.local:3000` ou `http://localhost:3000` → **genérico** (login universal).
  - `http://processo.meadadigital.local:3000` → **ProcessoBot** (login valida perfil `legal`).
  - `http://dental.meadadigital.local:3000` → **DentalBot** (perfil `dental`).
  - `http://sushi.meadadigital.local:3000` → **SushiBot** (perfil `sushi`).
- O backend (porta 8095) é o mesmo para todos os subdomínios — o perfil viaja no header
  `X-Meada-Subdomain` (apiFetch) e é resolvido por `companies.profile_id`.

## Comportamento de match (login)

- **Subdomínio universal** (`meada`/`localhost`): qualquer usuário entra.
- **Subdomínio de produto** (`processo`/`dental`/`sushi`): após autenticar, o frontend chama
  `GET /admin/me/profile-match?subdomain=...`. Se o perfil da empresa do usuário **não** bate,
  faz `signOut` e mostra "Você é cliente do <Produto>. Acesse <subdomínio>.meadadigital.local".
- **Super-admin** sempre casa (acessa qualquer subdomínio).

## Tenants de teste (perfis)

| Email                | Senha            | Empresa                       | Perfil  |
|----------------------|------------------|-------------------------------|---------|
| `igorhaf3@gmail.com` | `bofo-meca-oleo` | Escritório Modelo Advocacia   | legal   |
| `igorhaf4@gmail.com` | `bofo-meca-oleo` | Clínica Modelo Odonto         | dental  |
| `igorhaf5@gmail.com` | `bofo-meca-oleo` | Sushi Modelo                  | sushi   |

(Seed em `/tmp/seed-multi-profile.sql` — não versionado; roda via psql.)

## Adicionar um perfil novo

1. Adicione a entrada no enum `ProfileType.java` **e** no const `profile-type.ts`.
2. Crie uma migration que altere a CHECK constraint de `companies.profile_id`.
3. Adicione a entrada de `/etc/hosts` do novo subdomínio.
4. `mvn -B test` (paridade Java↔TS) + `npm run build`.
