package com.meada.profiles.floricultura;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Período de entrega do perfil floricultura (camada 8.5, ESCAPADA) — MATERIALIZADO, espelho 1:1 de
 * {@code frontend/profiles/floricultura/floricultura-period.ts}. Flor é presente AGENDADO: o pedido
 * carrega o dia (delivery_date) + a FAIXA do dia (este enum). Duas faixas hardcoded — não slot por
 * minuto (over-engineering). O {@code FloriculturaPeriodParityTest} garante a paridade Java↔TS; a
 * CHECK de {@code floricultura_orders.delivery_period} (migration 49) trava os mesmos ids no banco.
 */
public enum FloriculturaPeriod {
    MANHA("manha", "Manhã (8h–12h)"),
    TARDE("tarde", "Tarde (13h–18h)");

    private final String id;
    private final String label;

    FloriculturaPeriod(String id, String label) {
        this.id = id;
        this.label = label;
    }

    public String id() {
        return id;
    }

    public String label() {
        return label;
    }

    /** Resolve um período pelo id estável. Optional vazio se inválido/null. */
    public static Optional<FloriculturaPeriod> fromId(String id) {
        if (id == null) {
            return Optional.empty();
        }
        return Arrays.stream(values()).filter(p -> p.id.equals(id)).findFirst();
    }

    /** Todos os períodos, na ordem de declaração. */
    public static List<FloriculturaPeriod> allActive() {
        return List.of(values());
    }
}
