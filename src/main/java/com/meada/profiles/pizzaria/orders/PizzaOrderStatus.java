package com.meada.profiles.pizzaria.orders;

import java.util.Arrays;
import java.util.Optional;
import java.util.Set;

/**
 * Status de um pedido pizzaria (camada 8.4) — clone de
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
 * {@code frontend/profiles/pizzaria/pizzaria-order-status.ts} (PizzaOrderStatusParityTest).
 */
public enum PizzaOrderStatus {
    AGUARDANDO("aguardando"),
    EM_PREPARO("em_preparo"),
    SAIU_ENTREGA("saiu_entrega"),
    ENTREGUE("entregue"),
    RECUSADO("recusado"),
    CANCELADO("cancelado");

    private final String id;

    PizzaOrderStatus(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public static Optional<PizzaOrderStatus> fromId(String id) {
        if (id == null) {
            return Optional.empty();
        }
        return Arrays.stream(values()).filter(s -> s.id.equals(id)).findFirst();
    }

    /** Transições permitidas a partir deste status. */
    public Set<PizzaOrderStatus> allowedNext() {
        return switch (this) {
            case AGUARDANDO -> Set.of(EM_PREPARO, RECUSADO);
            case EM_PREPARO -> Set.of(SAIU_ENTREGA, CANCELADO);
            case SAIU_ENTREGA -> Set.of(ENTREGUE, CANCELADO);
            case ENTREGUE, RECUSADO, CANCELADO -> Set.of();
        };
    }

    public boolean canTransitionTo(PizzaOrderStatus next) {
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
            case EM_PREPARO -> "Seu pedido foi aceito! Já estamos preparando sua pizza. 🍕";
            case SAIU_ENTREGA -> "Sua pizza saiu pra entrega. Já já chega quentinha!";
            case ENTREGUE -> "Pizza entregue! Bom apetite e obrigado pela preferência! 🍕";
            case RECUSADO -> "Infelizmente não conseguimos aceitar seu pedido agora. Pedimos desculpa pelo transtorno.";
            case CANCELADO -> "Seu pedido foi cancelado. Se quiser refazer, é só me chamar.";
            // aguardando não notifica (a IA já confirmou o recebimento na mensagem).
            case AGUARDANDO -> null;
        };
    }
}
