package com.meada.whatsapp.profiles.lavanderia;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Período de COLETA do perfil lavanderia (camada 8.10) — MATERIALIZADO, espelho 1:1 de
 * {@code frontend/profiles/lavanderia/lavanderia-period.ts}. O pedido carrega o dia da coleta
 * (collect_date) + a FAIXA do dia (este enum). Duas faixas hardcoded — não slot por minuto. O
 * {@code LavanderiaPeriodParityTest} garante a paridade Java↔TS; a CHECK de
 * {@code lavanderia_orders.period} (migration 54) trava os mesmos ids no banco. Clone de
 * {@link com.meada.whatsapp.profiles.floricultura.FloriculturaPeriod}.
 */
public enum LavanderiaPeriod {
    MANHA("manha", "Manhã (8h–12h)"),
    TARDE("tarde", "Tarde (13h–18h)");

    private final String id;
    private final String label;

    LavanderiaPeriod(String id, String label) {
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
    public static Optional<LavanderiaPeriod> fromId(String id) {
        if (id == null) {
            return Optional.empty();
        }
        return Arrays.stream(values()).filter(p -> p.id.equals(id)).findFirst();
    }

    /** Todos os períodos, na ordem de declaração. */
    public static List<LavanderiaPeriod> allActive() {
        return List.of(values());
    }
}
