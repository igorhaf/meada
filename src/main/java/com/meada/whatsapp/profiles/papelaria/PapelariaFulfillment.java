package com.meada.whatsapp.profiles.papelaria;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Forma de entrega de um pedido papelaria (camada 8.15 / perfil papelaria) — MATERIALIZADO, espelho
 * 1:1 de {@code frontend/profiles/papelaria/papelaria-fulfillment.ts}. Clone de
 * {@link com.meada.whatsapp.profiles.padaria.PadariaFulfillment} (camada 8.8): o cliente escolhe
 * RETIRADA (balcão, sem taxa/endereço) ou ENTREGA (exige endereço + soma a taxa). O
 * {@code PapelariaFulfillmentParityTest} garante a paridade Java↔TS; a CHECK de
 * {@code papelaria_orders.fulfillment} (migration 59) trava os mesmos ids no banco.
 */
public enum PapelariaFulfillment {
    RETIRADA("retirada", "Retirada"),
    ENTREGA("entrega", "Entrega");

    private final String id;
    private final String label;

    PapelariaFulfillment(String id, String label) {
        this.id = id;
        this.label = label;
    }

    public String id() {
        return id;
    }

    public String label() {
        return label;
    }

    /** Resolve uma forma de entrega pelo id estável. Optional vazio se inválido/null. */
    public static Optional<PapelariaFulfillment> fromId(String id) {
        if (id == null) {
            return Optional.empty();
        }
        return Arrays.stream(values()).filter(f -> f.id.equals(id)).findFirst();
    }

    /** Todas as formas de entrega, na ordem de declaração. */
    public static List<PapelariaFulfillment> allActive() {
        return List.of(values());
    }
}
