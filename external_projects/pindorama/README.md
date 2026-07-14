# Pindorama — terapias, eventos e cursos

Marketplace para divulgação, agendamento, matrícula, pagamento e controle de acesso de práticas terapêuticas, atendimentos, eventos e cursos.

Cada profissional possui uma landing page pública parametrizada. O cliente agenda ou se matricula e paga dentro do site; após a confirmação recebe um passaporte digital com QR code. A casa administra profissionais, eventos, calendário, financeiro, comissões e repasses.

O estado funcional e a validação mais recente estão em [RELATORIO_ADERENCIA_PRODUTO.md](RELATORIO_ADERENCIA_PRODUTO.md).

## Stack

| Camada | Tecnologia |
|---|---|
| Backend | Laravel 13 / PHP 8.3 |
| Banco | PostgreSQL 16 |
| Interface | Blade SSR, Vue 3 e Tailwind CSS 4 |
| Build | Vite 8 |
| Pagamento | Mercado Pago Checkout Transparente |
| Infraestrutura | Docker Compose do projeto Meada |

## Papéis

- **Cliente/aluno:** pesquisa, agenda, matricula-se, paga, acompanha reservas e usa o passaporte.
- **Profissional/instrutor:** administra perfil, serviços, valores, locais e agenda; opera os eventos aos quais foi vinculado.
- **Root:** cria profissionais e eventos, administra toda a operação, calendário global, financeiro, CMS e regras da casa.

Somente o root cria contas profissionais e eventos. O profissional é ativado por convite e não pode promover uma conta de cliente.

## Desenvolvimento

O Pindorama usa os serviços `pindorama-postgres`, `pindorama-app` e `pindorama-nginx` do Compose localizado na raiz do Meada.

```bash
# na raiz do meada
cp external_projects/pindorama/.env.example external_projects/pindorama/.env
docker compose build pindorama-app
docker compose up -d pindorama-postgres pindorama-app pindorama-nginx
docker compose exec pindorama-app php artisan key:generate
docker compose exec pindorama-app php artisan migrate --seed

# frontend
cd external_projects/pindorama
npm install
npm run build
```

O script de inicialização do container mantém o PHP-FPM e o scheduler do Laravel em execução. Reconstrua a imagem sempre que `docker/php/start.sh` ou o Dockerfile mudar.

## Testes

Dentro do container, use o hostname interno do PostgreSQL:

```bash
docker exec \
  -e DB_HOST=pindorama-postgres \
  -e DB_PORT=5432 \
  pindorama-app php artisan test
```

Validação de 14/07/2026: **18 testes, 69 asserções**, build de produção aprovado e 166 rotas registradas.

## Produção

Antes da publicação, configure Mercado Pago, webhook assinado, Google, e-mail, HTTPS, banco, backup e monitoramento. Faça a homologação de pagamentos, recusas, PIX, reenvios de webhook, cancelamentos e estornos.

Consulte também [docs/INTEGRACOES.md](docs/INTEGRACOES.md) e [docs/PINDORAMA.md](docs/PINDORAMA.md).
