package com.meada.whatsapp.profiles.dental.config;

import java.time.LocalTime;
import java.util.UUID;

/**
 * Config do consultório (camada 7.4) — espelha dental_clinic_config. {@code durationMinutes} é
 * quanto dura uma consulta (30min padrão); {@code bufferMinutes} é o intervalo extra entre consultas
 * (0 nesta SM); {@code opensAt}/{@code closesAt} é a janela de funcionamento. Ausente → defaults.
 */
public record DentalClinicConfig(
    UUID companyId,
    int durationMinutes,
    int bufferMinutes,
    LocalTime opensAt,
    LocalTime closesAt) {

    /** Defaults cravados (decisão 3): 30min de consulta, sem buffer, 08:00–18:00. */
    public static DentalClinicConfig defaultFor(UUID companyId) {
        return new DentalClinicConfig(
            companyId, 30, 0, LocalTime.of(8, 0), LocalTime.of(18, 0));
    }
}
