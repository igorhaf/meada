# Relatório de implementação e aderência do produto Pindorama

**Data da atualização:** 14/07/2026  
**Escopo:** produto público, clientes, profissionais, eventos, agenda, pagamentos, passaportes, financeiro, administração, notificações, privacidade e operação.

## 1. Resumo executivo

As lacunas identificadas na auditoria original foram implementadas. O Pindorama agora possui o fluxo operacional completo para divulgar, agendar, matricular, cobrar, controlar acesso e apurar financeiramente serviços terapêuticos e eventos.

O formato das páginas individuais é uma **landing page**. Profissionais, serviços e eventos possuem páginas públicas próprias e parametrizadas, mantendo a identidade visual global do site.

No código atual:

- somente o root cria profissionais e eventos;
- o profissional administra perfil, serviços, valores, locais, disponibilidade e agenda;
- eventos aceitam vários instrutores e vários encontros;
- consultas e encontros compartilham o mesmo motor de conflitos;
- cliente, profissional e root podem realizar as operações permitidas para cada papel;
- consultas e eventos pagos usam o checkout do Mercado Pago;
- pagamentos alimentam um razão financeiro unificado;
- confirmações geram passaporte digital seguro com QR code;
- root e instrutores autorizados podem fazer check-in;
- root possui calendário e financeiro globais;
- os fluxos de consentimento, exportação e exclusão de dados foram incorporados.

O que ainda existe fora do código é a **ativação operacional de produção**: credenciais reais, homologação da conta Mercado Pago, HTTPS e URL pública definitivos, configuração de e-mail, textos jurídicos aprovados e rotinas de monitoramento/backup.

## 2. Matriz final de aderência

| Área | Estado atual | Implementação |
|---|---|---|
| Vitrine pública | Implementado | Home, busca, práticas, profissionais, serviços e eventos |
| Landing do profissional | Implementado | Banner, foto, nome, bio, práticas, serviços, locais, redes sociais e eventos vinculados |
| Identidade visual | Implementado | Paleta e fontes globais; profissional não altera tokens visuais |
| Profissionais somente pelo root | Implementado | Criação administrativa, ativação/desativação e convite seguro para definição de senha |
| Administração global do profissional | Implementado | Root edita perfil, especialidades, serviços, locais, disponibilidade, bloqueios e agenda no contexto escolhido |
| Serviços e valores | Implementado | Cadastro próprio pelo profissional ou root, preço, duração, modalidade, parcelas e locais |
| Agenda individual | Implementado | Visões diária, semanal e mensal com bloqueios, consultas e eventos vinculados |
| Agendamento pelo cliente | Implementado | Escolha de horário, conflito transacional, pagamento, reagendamento e cancelamento |
| Agendamento administrativo | Implementado | Root e profissional localizam ou convidam cliente e reservam em nome dele |
| Eventos somente pelo root | Implementado | CRUD, publicação, cancelamento, capa, local, sala, capacidade e programação exclusivos do root |
| Múltiplos instrutores | Implementado | Relação muitos-para-muitos, papéis, ordem e permissões por evento |
| Cursos com encontros | Implementado | Um evento pode possuir várias sessões com início, fim, sala e instrutores |
| Inscrição do cliente | Implementado | Inscrição, vagas, desconto, checkout, cancelamento e área “Minhas inscrições” |
| Inscrição administrativa | Implementado | Root e instrutor autorizado matriculam cliente e registram pagamento manual quando necessário |
| Instrutor e participantes | Implementado | Instrutor vinculado visualiza inscritos, pagamento e presença conforme permissão |
| Checkout de serviços | Implementado | Cartão/PIX pelo Mercado Pago, webhook verificado e confirmação automática |
| Checkout de eventos | Implementado | Mesmo checkout real, sem a antiga aprovação simulada |
| Passaporte digital | Implementado | Código aleatório, token assinado, QR code, validade, cancelamento e check-in auditado |
| Calendário global | Implementado | Visões semanal/mensal e filtros por profissional, sala, tipo e período |
| Conflitos unificados | Implementado | Profissional, instrutor e sala não podem ser ocupados simultaneamente por consulta/evento |
| Financeiro do profissional | Implementado | Receitas de consultas/eventos, valores brutos, casa, líquido, status e período |
| Financeiro root | Implementado | Razão global, filtros, exportação CSV/PDF, divisões, repasses e saldos |
| Cancelamentos e estornos | Implementado | Invalidação de passaporte, estorno pelo provedor e sinalização de devolução manual |
| Notificações | Implementado | Criação, confirmação, pagamento, passe, alterações, cancelamentos e lembretes |
| LGPD | Implementado | Aceite de termos/privacidade, consentimentos, exportação e anonimização de conta |
| Auditoria | Implementado | Registro de ações sensíveis como check-in e operações administrativas |

## 3. Papéis e permissões finais

### Cliente/aluno

- cria somente conta de cliente;
- consulta profissionais, serviços e eventos;
- agenda serviço e se matricula em evento/curso;
- paga no checkout interno;
- acompanha agendamentos e inscrições;
- acessa passaporte/QR após confirmação;
- solicita reagendamento, cancelamento e operações de privacidade.

### Profissional/instrutor

- acessa painel próprio ativado por convite do root;
- edita perfil, banner, foto, redes sociais e práticas;
- administra serviços, preços, locais, disponibilidade e bloqueios;
- consulta agenda com atendimentos e encontros vinculados;
- agenda cliente em nome dele;
- vê inscritos, pagamentos e faz presença nos eventos em que possui permissão;
- consulta seu financeiro e repasses;
- não cria nem programa eventos.

### Root

- cria, convida, edita, ativa e desativa profissionais;
- administra integralmente o contexto de qualquer profissional;
- cria e programa eventos, encontros, salas e instrutores;
- agenda e matricula clientes;
- acessa calendário global e resolve a operação da casa;
- acessa pagamentos, valores da casa, líquidos, divisões e repasses;
- administra CMS, configurações e regras de comissão.

## 4. Eventos, cursos e agenda

O antigo vínculo único `events.professional_id` foi evoluído para instrutores múltiplos. O vínculo armazena papel, posição, permissão financeira, permissão de presença e percentual de divisão.

Cursos usam `event_sessions`, permitindo várias datas e horários. Cada encontro pode ter sala e instrutores. As sessões alimentam a agenda global e a agenda do profissional.

O motor de conflito verifica:

1. consultas existentes do profissional;
2. encontros de eventos de todos os instrutores;
3. bloqueios individuais;
4. ocupação da sala;
5. sobreposição entre compromissos.

## 5. Pagamentos e financeiro

Consultas, inscrições e cobranças alimentam uma estrutura comum de transações. Cada lançamento registra valor bruto, desconto, valor da casa, líquido, provedor, identificador externo, método, estado e datas operacionais.

Os eventos geram divisões entre os instrutores configurados. Os valores ficam congelados no momento financeiro para preservar o histórico mesmo quando preços ou percentuais mudarem depois.

O webhook do Mercado Pago:

- valida a assinatura HMAC e a tolerância de tempo;
- consulta o pagamento diretamente no provedor antes de atualizar o pedido;
- aceita reenvio idempotente;
- trata consulta e evento;
- emite passe somente após a condição de confirmação;
- registra rejeição, cancelamento e estorno.

O root dispõe de relatório consolidado e exportação CSV/PDF. O profissional vê somente seus lançamentos e divisões. Repasses não podem ultrapassar o saldo apurado no período.

## 6. Passaporte e presença

O passaporte é emitido para consulta ou inscrição confirmada, inclusive gratuidades e liberações manuais autorizadas. Ele contém código público não sequencial, hash persistido, URL temporária assinada, QR code, validade e estado.

Estados suportados: válido, utilizado, cancelado e expirado. O check-in registra operador e horário e respeita o profissional responsável ou as permissões do evento.

## 7. Privacidade, segurança e comunicação

- termos e política são aceitos nos cadastros e convites;
- dados sensíveis de atendimento exigem consentimento;
- conta inativa não autentica nem aparece publicamente;
- cliente pode exportar os próprios dados;
- exclusão anonimiza os dados preservando registros financeiros obrigatórios;
- profissional não encerra unilateralmente uma conta operacional sem o root;
- operações críticas geram auditoria;
- notificações de operação e lembretes são enviadas pelo canal configurado.

## 8. Validação técnica executada

Validação feita em 14/07/2026:

- **166 rotas** da aplicação registradas;
- migrations `000028` e `000029` aplicadas no banco de desenvolvimento;
- **18 testes e 69 asserções aprovados** no PostgreSQL de teste;
- sintaxe PHP verificada em aplicação, migrations e rotas;
- `npm run build` concluído com sucesso pelo Vite;
- `git diff --check` sem erros;
- scheduler reconhece `events:remind` e `appointments:remind`, ambos horários;
- imagem PHP reconstruída e processos FPM + scheduler confirmados ativos no container.
- resposta HTTP `200 OK` confirmada pelo Nginx após a reconstrução.

Os testes novos cobrem governança de cadastro, múltiplos instrutores, sessões e conflitos, persistência do evento root, transações/divisões, emissão e check-in de passe, páginas operacionais e assinatura oficial do webhook.

No container, a suíte deve ser executada com o hostname interno do banco:

```bash
docker exec \
  -e DB_HOST=pindorama-postgres \
  -e DB_PORT=5432 \
  pindorama-app php artisan test
```

## 9. Checklist obrigatório para publicar em produção

Estes itens não representam funcionalidade ausente no repositório; são configurações e validações externas ao código:

- preencher `APP_URL` com HTTPS e gerar chave segura da aplicação;
- configurar banco, fila/cache, e-mail e credenciais Google do ambiente;
- preencher `MP_ACCESS_TOKEN`, `MP_PUBLIC_KEY` e `MP_WEBHOOK_SECRET` de produção;
- cadastrar a URL pública do webhook no painel Mercado Pago;
- executar pagamento, PIX, recusa, reenvio de webhook, cancelamento e estorno no ambiente de homologação;
- definir operacionalmente repasse manual ou automatizado e responsáveis pela conciliação;
- revisar Termos, Política de Privacidade, cancelamento, reembolso, no-show e retenções com assessoria jurídica;
- configurar backup, retenção de logs, alertas, monitoramento e rotina de recuperação;
- homologar os dois cenários ponta a ponta com usuários reais de cada papel.

## 10. Cenários finais de aceite

### Terapia/consulta

1. root cria o profissional e envia convite;
2. profissional ativa o acesso e configura perfil, serviço, valor e agenda;
3. cliente agenda, aceita os consentimentos e paga;
4. root e profissional visualizam a ocupação;
5. sistema emite passaporte;
6. profissional realiza check-in e conclui o atendimento;
7. financeiro exibe bruto, casa, líquido e repasse.

### Evento/curso

1. root cria evento, encontros, sala e instrutores;
2. evento aparece publicamente e nas páginas dos instrutores;
3. cliente se inscreve e paga;
4. instrutores autorizados veem participante e situação financeira;
5. sistema emite passaporte/QR;
6. instrutor ou root registra presença;
7. financeiro divide bruto, casa e líquidos conforme a configuração.

## 11. Conclusão

O domínio e os fluxos solicitados estão alinhados no código. O projeto deixou de ser apenas a fundação técnica descrita na primeira auditoria e passou a suportar a operação integral proposta. A próxima etapa correta é **homologação de ambiente e negócio**, não uma nova remodelagem funcional.
