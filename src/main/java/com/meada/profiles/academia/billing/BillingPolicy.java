package com.meada.profiles.academia.billing;

import java.util.UUID;

/**
 * Política de cobrança de UM tenant academia (colunas novas em {@code academia_config}, migration 72).
 * Opt-in por tenant: lembrete de vencimento ligável, tolerância em dias, e suspensão automática
 * opcional após N dias de atraso.
 *
 * @param companyId          empresa
 * @param reminderEnabled    envia lembrete de vencimento (default true)
 * @param graceDays          dias de tolerância após o vencimento antes de considerar atraso
 * @param autoSuspendDays    dias de atraso para suspensão automática; null = nunca suspende
 */
public record BillingPolicy(
    UUID companyId,
    boolean reminderEnabled,
    int graceDays,
    Integer autoSuspendDays) {}
