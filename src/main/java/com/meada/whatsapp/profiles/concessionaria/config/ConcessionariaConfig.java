package com.meada.whatsapp.profiles.concessionaria.config;

import java.time.LocalTime;
import java.util.UUID;

/**
 * Config do tenant concessionaria (camada 8.17) — espelha concessionaria_config. Clone do
 * dental_clinic_config + {@code businessName} (estilo event_config). {@code durationMinutes} é quanto
 * dura um test-drive (45min padrão); {@code bufferMinutes} é o intervalo extra (0 nesta SM);
 * {@code opensAt}/{@code closesAt} é a janela de funcionamento. Ausente → defaults.
 */
public record ConcessionariaConfig(
    UUID companyId,
    String businessName,
    int durationMinutes,
    int bufferMinutes,
    LocalTime opensAt,
    LocalTime closesAt,
    String notes) {

    /** Defaults cravados: 45min de test-drive, sem buffer, 09:00–18:00. */
    public static ConcessionariaConfig defaultFor(UUID companyId) {
        return new ConcessionariaConfig(
            companyId, null, 45, 0, LocalTime.of(9, 0), LocalTime.of(18, 0), null);
    }
}
