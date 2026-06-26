package com.meada.whatsapp.profiles.concessionaria;

import java.util.Arrays;
import java.util.Optional;
import java.util.Set;

/**
 * Status de ESTOQUE de um veículo da concessionária (camada 8.17). CICLO DE VIDA próprio do item de
 * estoque — o veículo VENDIDO sai da disponibilidade:
 * <pre>
 *   disponivel → reservado, vendido
 *   reservado  → disponivel, vendido
 *   vendido    → (terminal)
 * </pre>
 * Mudança de status é AÇÃO HUMANA do painel (a IA NÃO toca o estoque). Transição inválida → 409
 * invalid_status_transition. Apenas {@code disponivel} entra na vitrine e aceita test-drive/lead.
 *
 * <p>Espelhado 1:1 por {@code frontend/profiles/concessionaria/concessionaria-vehicle-status.ts}
 * (VehicleStatusParityTest garante a paridade Java↔TS).
 */
public enum VehicleStatus {
    DISPONIVEL("disponivel", "Disponível"),
    RESERVADO("reservado", "Reservado"),
    VENDIDO("vendido", "Vendido");

    private final String id;
    private final String label;

    VehicleStatus(String id, String label) {
        this.id = id;
        this.label = label;
    }

    public String id() {
        return id;
    }

    public String label() {
        return label;
    }

    public static Optional<VehicleStatus> fromId(String id) {
        if (id == null) {
            return Optional.empty();
        }
        return Arrays.stream(values()).filter(s -> s.id.equals(id)).findFirst();
    }

    /** Transições permitidas a partir deste status. */
    public Set<VehicleStatus> allowedNext() {
        return switch (this) {
            case DISPONIVEL -> Set.of(RESERVADO, VENDIDO);
            case RESERVADO -> Set.of(DISPONIVEL, VENDIDO);
            case VENDIDO -> Set.of();
        };
    }

    public boolean canTransitionTo(VehicleStatus next) {
        return allowedNext().contains(next);
    }

    /** True só para {@code disponivel} — o único status que entra na vitrine e aceita test-drive/lead. */
    public boolean isAvailable() {
        return this == DISPONIVEL;
    }
}
