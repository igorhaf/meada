package com.meada.profiles.atelie.reminders;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Prova/ajuste DUE para o lembrete de véspera (onda Ateliê, backlog #1): pendente, com due_date =
 * amanhã e ainda não lembrada para essa data. Carrega o mínimo pro job montar o texto e resolver o
 * canal (a conversa da proposta). Espelho do DueMembership da academia.
 */
public record DueFitting(
    UUID fittingId,
    UUID companyId,
    UUID proposalId,
    String title,
    LocalDate dueDate,
    UUID conversationId,
    String customerName) {
}
