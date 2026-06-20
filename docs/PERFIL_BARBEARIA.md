# Perfil Barbearia — BarbeariaBot (camada 8.1)

Guia operacional do tenant **barbearia** (`profile_id='barbearia'`). O BarbeariaBot é o produto de
**barbearia / barber shop**: você gerencia barbeiros e serviços, marca horários na agenda, e gerencia
uma **fila de walk-in** (por ordem de chegada). A IA atende seus clientes pelo WhatsApp e oferece dois
caminhos: **marcar horário** com um barbeiro, ou **entrar na fila** quando o cliente quer ser atendido
"assim que der".

## Telas (menu "Barbearia")

- **Barbeiros** (`/dashboard/barber-barbers`): cadastre quem atende. Especialidade é texto livre e
  opcional ("corte/barba", "degradê"). Barbeiro inativo some da disponibilidade que a IA enxerga.
  Não dá pra excluir um barbeiro com agendamento ou ticket de fila — **desative** em vez de excluir.
- **Serviços** (`/dashboard/barber-services`): nome, duração própria (em minutos) e preço opcional.
  A duração entra como "foto" (snapshot) no agendamento/ticket — mudar o serviço depois não altera os
  passados. Preço em branco = a IA não expõe valor.
- **Agenda** (`/dashboard/barber-appointments`): os horários marcados, agrupados por dia, com filtro de
  status e de barbeiro. Você pode criar manualmente. **O conflito é por barbeiro**: dois clientes no
  mesmo horário com barbeiros diferentes é normal (paralelismo). Botões de transição de status seguem
  as regras (ver abaixo).
- **Fila** (`/dashboard/barber-queue`): a fila de walk-in. Cada pessoa aguardando mostra a **posição**
  (calculada na hora) e a **espera estimada**. Veja como funciona logo abaixo.
- **Configurações** (`/dashboard/barber-settings`): horário de funcionamento, granularidade dos slots
  (ex.: de 15 em 15 min) e o **interruptor da fila de walk-in**.

## Como a FILA funciona (a parte nova)

A fila é **por ordem de chegada**, sem hora marcada. O ponto importante: a **posição não é fixa** — ela
é recalculada toda hora.

- Cada ticket pode ser de um **barbeiro específico** ou da **fila geral** ("qualquer barbeiro").
- A **posição** que você vê é derivada na hora: é quantas pessoas estão à frente naquele escopo, + 1.
  Não existe um número gravado que precise ser "arrastado".
- Quando você **atende** (ou alguém **desiste**), todo mundo atrás sobe **automaticamente** — sem você
  reordenar nada.
- Escopo do "qualquer barbeiro": um ticket geral conta contra todos os que estão à frente; um ticket de
  um barbeiro específico conta contra a fila dele + os tickets gerais que chegaram antes (um geral à
  frente pode acabar pegando aquele barbeiro).
- A **espera estimada** é só uma estimativa (soma das durações à frente). A IA sempre fala
  "aproximadamente" — desistências e horários marcados mexem a fila.

### Chamar o próximo

Quando o barbeiro fica livre, **você** clica em **"Chamar"** no ticket que está em 1º na fila daquele
barbeiro. Isso:

1. muda o ticket para **chamado** e
2. **notifica o cliente no WhatsApp**: "Chegou a sua vez! Procure o barbeiro Fulano." — é a notificação
   crítica do walk-in.

Depois que ele for atendido, clique em **"Atendido"**. Se a pessoa sumiu, **"Desistiu"**.

> **A IA não chama ninguém.** Ela só coloca o cliente na fila e informa a posição/espera estimadas.
> Quem chama é sempre o painel (você). Isso é proposital — chamar é decisão de quem está no balcão.

## Status

**Agenda (horários marcados):** `agendado → confirmado → realizado`; `agendado/confirmado → cancelado`;
`confirmado → falta`. Só **confirmado** (com data/hora/barbeiro) e **cancelado** avisam o cliente.

**Fila (tickets):** `aguardando → chamado → atendido`; de aguardando dá pra ir a `desistiu`/`expirado`;
de chamado, a `atendido`/`desistiu`. Só **chamado** notifica ("chegou sua vez").

## O que a IA faz (e o que NÃO faz)

A IA **faz**: identifica o cliente pelo telefone, mostra serviços e barbeiros, oferece marcar horário
**ou** entrar na fila, informa a posição/espera estimadas, e cria o agendamento/ticket na confirmação.

A IA **nunca**: opina sobre a aparência/estilo do cliente nem promete resultado de corte; recomenda
serviço que o cliente não pediu (sem upsell agressivo); inventa posição na fila ou tempo de espera
exato ("você é o próximo garantido"); chama o cliente ou move um ticket por conta própria; garante um
horário que conflita (o sistema reforça com erro).

## Limitações conhecidas (fases futuras)

- Não há "chamar o próximo" automático que vire atendimento na agenda — chamar é manual.
- Sem expiração automática da fila por tempo, sem lembrete "está chegando sua vez".
- Sem painel de TV / display público / check-in por QR.
- Sem pagamento/comanda/gorjeta, sem assinatura de cortes recorrentes.
- Sem foto do corte / galeria de referência.
- Um barbeiro = um atendimento por vez (sem múltiplas cadeiras paralelas).
