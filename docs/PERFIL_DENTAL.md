# DentalBot — guia operacional da clínica (camada 7.4)

O DentalBot é o produto do Meada para clínicas odontológicas. Seus pacientes falam pelo WhatsApp em
linguagem natural; a IA atende com tom acolhedor, reconhece o paciente pelo telefone, informa as
próximas consultas e agenda novas. Você acompanha tudo pela agenda e muda o status conforme o
atendimento.

## 1. Cadastrar pacientes (`/dashboard/patients`)

- **Novo paciente:** nome (obrigatório), telefone, email, CPF, data de nascimento e observações
  (todos opcionais).
- **Vínculo com o WhatsApp:** o badge **"vinculado"** indica que o paciente está ligado a um contato
  do WhatsApp — é assim que a IA o reconhece pelo telefone e responde sobre as consultas dele.
- **Busca:** por nome, telefone, email ou CPF.
- **Detalhe:** clique no nome para ver os dados + a lista de consultas do paciente.
- **Excluir:** bloqueado se o paciente tiver consultas (proteção de histórico).

## 2. Configurar o consultório (`/dashboard/dental-settings`)

- **Duração da consulta:** quanto tempo cada consulta ocupa a agenda (padrão 30 min).
- **Intervalo entre consultas:** folga extra (0 por padrão).
- **Horário de funcionamento:** abre/fecha (padrão 08:00–18:00). A consulta inteira tem de caber
  nessa janela.
- **Importante:** mudanças afetam apenas consultas **futuras** — as já criadas mantêm a duração do
  momento em que foram agendadas.

## 3. Agenda de consultas (`/dashboard/appointments`)

- **Lista por dia:** as consultas vêm agrupadas por data, em ordem de horário. Filtre por status
  (agendada/confirmada/realizada/cancelada/falta).
- **Nova consulta (manual):** escolha o paciente, data, hora, o **tipo** (texto livre com sugestões:
  Limpeza, Avaliação, Restauração, Canal, Ortodontia, Manutenção, Retorno) e observações. Se o
  horário estiver ocupado, o sistema recusa e mostra **quem** ocupa e **de que horas a que horas**.
- **Detalhe + status:** clique numa consulta para ver os dados e mudar o status. Ao **confirmar** ou
  **cancelar**, o paciente é notificado automaticamente (se vinculado ao WhatsApp). Marcar como
  realizada ou falta é silencioso.

## 4. Como a IA atende

- A IA conhece o paciente identificado pelo telefone (nome + próximas consultas) e os horários livres
  dos próximos 14 dias. Atende com tom acolhedor, com empatia por quem tem medo de dentista.
- **A IA NUNCA dá diagnóstico, NUNCA recomenda procedimento e NUNCA discute sintoma.** Para qualquer
  dúvida clínica (dor, sintoma, recomendação), ela responde: *"Para isso, vou pedir que o dentista
  avalie. Posso agendar uma consulta?"*.
- Quando o paciente confirma um agendamento (dia/hora/tipo definidos), a IA cria a consulta como
  **agendada** — você a vê na agenda e a confirma quando quiser (o paciente recebe o aviso).
- **Cancelamento pela IA é bloqueado:** se o paciente pedir para desmarcar, a IA encaminha pro
  consultório ("vou avisar o dentista, ele entra em contato pra confirmar o cancelamento"). O
  cancelamento é feito manualmente por você, na agenda.

## LGPD — dados administrativos, não clínicos (importante)

- As **observações** (do paciente e da consulta) são **administrativas** — preferências de horário,
  forma de contato, etc. **NÃO** registre informação clínica ali (diagnóstico, alergia, histórico,
  medicação).
- O **tipo** da consulta é uma etiqueta administrativa ("Limpeza", "Avaliação"), não um plano clínico.
- **Dados clínicos** (prontuário, odontograma, diagnóstico, plano de tratamento, alergias) **não são
  modelados nesta versão** — virão em fase futura, com criptografia em repouso e registro de acesso
  por usuário, como exige o tratamento de dados sensíveis de saúde.

## Limitações conhecidas (honestas)

- **Sem prontuário/odontograma/plano de tratamento/TUSS/anamnese** (fases futuras, LGPD pesada).
- **Sem `dentist_id`:** o conflito de horário é por consultório (modelo de 1 dentista por clínica
  nesta versão). Multi-dentista é fase futura.
- **Sem lembrete automático** ("sua consulta é amanhã") e **sem auto-transição** (consulta passada não
  vira "realizada" sozinha).
- **Fuso fixo** America/Sao_Paulo.
- **Sem anexo** (raio-X, foto) — bloqueador técnico de Storage.
- Os textos de notificação são fixos nesta versão.
- **Risco aceito no MVP:** se a IA prometer um horário e o backend detectar conflito ao gravar, a
  consulta não é criada (não aparece na agenda) — contorne manualmente. É raro.
