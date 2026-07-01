package com.meada.profiles.academia.billing;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Linha de matrícula candidata à régua de inadimplência (Onda 2 / piloto Academia). Traz só o que
 * o {@link AcademiaInadimplenciaJob} precisa para decidir lembrete/suspensão e resolver o canal de
 * envio — sem hidratar as aulas (que não importam aqui).
 *
 * @param membershipId       id da matrícula
 * @param companyId          empresa (tenant)
 * @param contactId          contato dono da matrícula (pode ser null se a matrícula foi criada sem WhatsApp)
 * @param conversationId     conversa mais recente (para resolver credenciais Evolution); pode ser null
 * @param studentName        nome do aluno (snapshot) — usado no texto do lembrete
 * @param planMonthlyCents   valor mensal do plano (snapshot) — usado no texto do lembrete
 * @param startDate          início da assinatura — base do cálculo de meses em aberto
 * @param overdueNotifiedMonth mês (dia 01) do último lembrete enviado; null se nunca notificado
 */
public record DueMembership(
    UUID membershipId,
    UUID companyId,
    UUID contactId,
    UUID conversationId,
    String studentName,
    int planMonthlyCents,
    LocalDate startDate,
    LocalDate overdueNotifiedMonth) {}
