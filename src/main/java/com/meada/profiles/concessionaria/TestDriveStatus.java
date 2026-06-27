package com.meada.profiles.concessionaria;

import java.util.Arrays;
import java.util.Optional;
import java.util.Set;

/**
 * Status de um test-drive da concessionária (camada 8.17) — clone do funil de agenda do dental:
 * <pre>
 *   agendado   → confirmado, cancelado
 *   confirmado → realizado, cancelado, no_show
 *   realizado/cancelado/no_show → (terminal)
 * </pre>
 * Transição inválida → 409 invalid_status_transition no controller.
 *
 * <p>Notifica: {@code confirmado} (com vendedor + veículo + data/hora) e {@code cancelado} (texto
 * defensivo); {@code agendado}/{@code realizado}/{@code no_show} são silenciosos (quem furou não
 * recebe sermão). Texto SEM promessa de venda/preço.
 *
 * <p>Espelhado 1:1 por {@code frontend/profiles/concessionaria/concessionaria-test-drive-status.ts}
 * (TestDriveStatusParityTest garante a paridade Java↔TS).
 */
public enum TestDriveStatus {
    AGENDADO("agendado", "Agendado"),
    CONFIRMADO("confirmado", "Confirmado"),
    REALIZADO("realizado", "Realizado"),
    CANCELADO("cancelado", "Cancelado"),
    NO_SHOW("no_show", "Não compareceu");

    private final String id;
    private final String label;

    TestDriveStatus(String id, String label) {
        this.id = id;
        this.label = label;
    }

    public String id() {
        return id;
    }

    public String label() {
        return label;
    }

    public static Optional<TestDriveStatus> fromId(String id) {
        if (id == null) {
            return Optional.empty();
        }
        return Arrays.stream(values()).filter(s -> s.id.equals(id)).findFirst();
    }

    public Set<TestDriveStatus> allowedNext() {
        return switch (this) {
            case AGENDADO -> Set.of(CONFIRMADO, CANCELADO);
            case CONFIRMADO -> Set.of(REALIZADO, CANCELADO, NO_SHOW);
            case REALIZADO, CANCELADO, NO_SHOW -> Set.of();
        };
    }

    public boolean canTransitionTo(TestDriveStatus next) {
        return allowedNext().contains(next);
    }

    /**
     * Texto fixo da notificação outbound ao ENTRAR neste status. null = não notifica.
     * {@code confirmado} e {@code cancelado} avisam; demais silenciosos. SEM promessa de venda/preço.
     * Ver {@link #notificationText(String, String, String)} para o confirmado (com vendedor+veículo+data).
     */
    public String notificationText() {
        return switch (this) {
            case CANCELADO -> "Seu test-drive foi cancelado. Se quiser remarcar, é só me chamar.";
            case AGENDADO, CONFIRMADO, REALIZADO, NO_SHOW -> null;
        };
    }

    /**
     * Texto da notificação de CONFIRMAÇÃO (status confirmado), com veículo + vendedor + data/hora.
     * Para os demais status, devolve {@link #notificationText()}.
     */
    public String notificationText(String vehicleLabel, String salespersonName, String whenLabel) {
        if (this == CONFIRMADO) {
            return "Seu test-drive do " + vehicleLabel + " está confirmado com " + salespersonName
                + " em " + whenLabel + ". Te esperamos! Qualquer mudança, é só avisar.";
        }
        return notificationText();
    }
}
