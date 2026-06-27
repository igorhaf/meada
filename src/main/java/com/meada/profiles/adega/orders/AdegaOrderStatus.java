package com.meada.profiles.adega.orders;

import java.util.Arrays;
import java.util.Optional;
import java.util.Set;

/**
 * Status de um pedido adega (camada 8.4) — clone de
 * {@link com.meada.profiles.sushi.orders.SushiOrderStatus} + a forma de
 * {@code AestheticAppointmentStatus} (canTransitionTo + notificationText). Inclui a ESCAPADA 1:
 * o gate de aceite do restaurante (aguardando → em_preparo / recusado, ações HUMANAS no painel).
 * <pre>
 *   aguardando    → em_preparo, recusado
 *   em_preparo    → saiu_entrega, cancelado
 *   saiu_entrega  → entregue, cancelado
 *   entregue      → (terminal)
 *   recusado      → (terminal)
 *   cancelado     → (terminal)
 * </pre>
 * Transição inválida → 409 invalid_status_transition no controller. Espelhado 1:1 por
 * {@code frontend/profiles/adega/adega-order-status.ts} (AdegaOrderStatusParityTest).
 */
public enum AdegaOrderStatus {
    AGUARDANDO("aguardando"),
    EM_PREPARO("em_preparo"),
    SAIU_ENTREGA("saiu_entrega"),
    ENTREGUE("entregue"),
    RECUSADO("recusado"),
    CANCELADO("cancelado");

    private final String id;

    AdegaOrderStatus(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public static Optional<AdegaOrderStatus> fromId(String id) {
        if (id == null) {
            return Optional.empty();
        }
        return Arrays.stream(values()).filter(s -> s.id.equals(id)).findFirst();
    }

    /** Transições permitidas a partir deste status. */
    public Set<AdegaOrderStatus> allowedNext() {
        return switch (this) {
            case AGUARDANDO -> Set.of(EM_PREPARO, RECUSADO);
            case EM_PREPARO -> Set.of(SAIU_ENTREGA, CANCELADO);
            case SAIU_ENTREGA -> Set.of(ENTREGUE, CANCELADO);
            case ENTREGUE, RECUSADO, CANCELADO -> Set.of();
        };
    }

    public boolean canTransitionTo(AdegaOrderStatus next) {
        return allowedNext().contains(next);
    }

    /**
     * Texto fixo da notificação outbound disparada ao ENTRAR neste status (ESCAPADA 1). null = não
     * notifica ({@code aguardando} é silencioso — a IA já confirmou o RECEBIMENTO na própria
     * mensagem). No caso de {@code recusado}, o MOTIVO (rejection_reason) é concatenado pelo
     * Service, não aqui — o enum é estático.
     */
    public String notificationText() {
        return switch (this) {
            case EM_PREPARO -> "Seu pedido foi aceito! Já estamos separando suas bebidas. 🍷";
            case SAIU_ENTREGA -> "Seu pedido saiu pra entrega. Já já chega aí! Beba com moderação.";
            case ENTREGUE -> "Pedido entregue. Aproveite e beba com moderação — obrigado pela preferência!";
            case RECUSADO -> "Infelizmente não conseguimos aceitar seu pedido agora. Pedimos desculpa pelo transtorno.";
            case CANCELADO -> "Seu pedido foi cancelado. Se quiser refazer, é só me chamar.";
            // aguardando não notifica (a IA já confirmou o recebimento na mensagem).
            case AGUARDANDO -> null;
        };
    }
}
