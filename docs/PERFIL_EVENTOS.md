# Perfil Eventos — EventosBot (camada 8.2)

Guia operacional do tenant **eventos** (`profile_id='eventos'`). Casa de festas / buffet /
cerimonial / espaço de eventos: a equipe gerencia cerimonialistas, abre propostas (orçamento +
cronograma do dia) e a IA atende clientes pelo WhatsApp — abre a proposta a partir do briefing e
captura a aprovação.

É o **12º perfil vertical** (13º contando o generic). CLONA o chassi do OficinaBot (proposta = OS,
itens = os_items, aprovação em 2 fases) e inaugura o **cronograma ordenado do dia do evento**.

## Telas (sidebar "Eventos")

| Tela | Rota | O que faz |
|------|------|-----------|
| Cerimonialistas | `/dashboard/eventos-planners` | CRUD do catálogo de responsáveis. Atribuir um cerimonialista à proposta é opcional. Excluir um que tem proposta → bloqueado (desative). |
| Propostas | `/dashboard/eventos-proposals` | Lista por status; abrir proposta; detalhe com os DOIS editores inline (orçamento + cronograma) e os botões de transição de status. |
| Configurações | `/dashboard/eventos-settings` | Nome do espaço/buffet + observações. Sem horário (não há agenda). |

## Como funciona a proposta

A proposta é o artefato central (espelho da ordem de serviço da oficina). Tem **dois tipos de
sub-item no mesmo artefato**, que não se misturam:

1. **Itens de ORÇAMENTO** — descrição + quantidade + preço unitário. O `total` da proposta é
   recalculado automaticamente a cada item adicionado/removido (espaço, buffet, decoração, etc.).
   **Entram no total.**
2. **Marcos de CRONOGRAMA** — horário + título (+ descrição opcional) do dia do evento (ex.:
   "19:00 recepção", "20:00 cerimônia", "23:00 festa"). Aparecem **ordenados por horário**,
   independente da ordem em que foram adicionados. **NÃO entram no total** — são o roteiro do dia.

### Estados da proposta

```
rascunho  → orcada, cancelada
orcada    → aprovada, recusada, cancelada
aprovada  → fechada, cancelada
fechada   → realizada, cancelada
realizada → (final)
recusada  → (final)
cancelada → (final)
```

- **rascunho** — proposta aberta, sem orçamento ainda (a IA abre aqui).
- **orcada** — a equipe montou o orçamento (≥1 item) e enviou ao cliente; aguardando a resposta.
  Só é possível ir para "orcada" com **total > 0** (não dá pra orçar sem item).
- **aprovada** — o cliente aceitou (pela conversa ou no painel).
- **fechada** — contrato fechado / sinal combinado fora do app.
- **realizada** — a festa aconteceu.
- **recusada / cancelada** — encerramento alternativo.

**Notificações automáticas ao cliente** (se houver vínculo com o WhatsApp): **orçada** (com o
total), **aprovada**, **fechada**, **recusada**. Os estados rascunho/realizada/cancelada são
silenciosos.

**Trava de edição:** os itens (orçamento E cronograma) só podem ser editados enquanto a proposta
está em **rascunho/orçada/aprovada**. Depois de **fechada** o escopo congela — o editor some no
painel e a API recusa alterações.

## O que a IA faz pelo WhatsApp

- Identifica o cliente pelo telefone, conversa em linguagem natural sobre o evento e **ABRE a
  proposta** a partir do briefing (tipo de evento, data prevista, número de convidados, o que o
  cliente imagina). A proposta nasce em **rascunho** — sem itens; a equipe monta o orçamento no
  painel.
- Quando a equipe coloca a proposta em **orçada**, o cliente recebe o total pelo WhatsApp. Se ele
  responder aprovando ou recusando, a IA **captura a decisão** e move a proposta para
  **aprovada/recusada** (só funciona enquanto a proposta está "orçada").

## O que a IA NÃO faz (cravado)

- **Não fecha contrato, preço ou desconto** — quem orça e fecha é a equipe no painel.
- **Não confirma disponibilidade de data** que não esteja confirmada — diz "vou verificar a
  disponibilidade com a equipe".
- **Não inventa** item de pacote, valor ou serviço, nem promete estrutura/comodidade do espaço que
  não tenha sido informada.
- **Não move** a proposta para "fechada"/"realizada" (transições administrativas do painel). A IA
  só ABRE a proposta e CAPTURA a aprovação/recusa.

## O que NÃO existe nesta fase

Conflito de agenda/data (a data é um campo livre — a casa faz ~1 evento por data), catálogo de
pacotes pré-cadastrados (o orçamento é ad-hoc, a equipe digita os itens), contrato com assinatura
digital/PDF, pagamento/sinal/parcelas, fornecedores externos com agenda própria, fotos/mood board,
lista de convidados/RSVP. Tudo fase futura.
