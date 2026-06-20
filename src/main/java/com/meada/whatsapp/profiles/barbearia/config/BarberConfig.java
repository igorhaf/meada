package com.meada.whatsapp.profiles.barbearia.config;

import java.time.LocalTime;
import java.util.UUID;

/**
 * Config da barbearia (camada 8.1) — espelha barber_config. {@code opensAt}/{@code closesAt} é a
 * janela de funcionamento; {@code slotMinutes} é a granularidade dos slots livres que a IA enxerga;
 * {@code queueEnabled} liga/desliga a fila de walk-in. Ausente → defaults (09:00–20:00, 15, true).
 */
public record BarberConfig(
    UUID companyId,
    LocalTime opensAt,
    LocalTime closesAt,
    int slotMinutes,
    boolean queueEnabled) {

    /** Defaults cravados: 09:00–20:00, slot 15min, fila ligada. */
    public static BarberConfig defaultFor(UUID companyId) {
        return new BarberConfig(companyId, LocalTime.of(9, 0), LocalTime.of(20, 0), 15, true);
    }
}
