package com.meada.profiles.lavanderia.orders;

import java.util.Arrays;
import java.util.Optional;
import java.util.Set;

/**
 * Status de um pedido lavanderia (camada 8.10) — clone de
 * {@link com.meada.profiles.floricultura.orders.FloriculturaOrderStatus} adaptado ao fluxo de
 * lavanderia (coleta → processamento → entrega). Inclui o GATE DE ACEITE HUMANO (aguardando →
 * coletado / recusado, ações HUMANAS no painel).
 * <pre>
 *   aguardando    → coletado, recusado, cancelado
 *   coletado      → em_processo, cancelado
 *   em_processo   → pronto, cancelado
 *   pronto        → saiu_entrega, cancelado
 *   saiu_entrega  → entregue, cancelado
 *   entregue      → (terminal)
 *   recusado      → (terminal)
 *   cancelado     → (terminal)
 * </pre>
 * Transição inválida → 409 invalid_status_transition no controller. Espelhado 1:1 por
 * {@code frontend/profiles/lavanderia/lavanderia-order-status.ts} (LavanderiaOrderStatusParityTest).
 */
public enum LavanderiaOrderStatus {
    AGUARDANDO("aguardando"),
    COLETADO("coletado"),
    EM_PROCESSO("em_processo"),
    PRONTO("pronto"),
    SAIU_ENTREGA("saiu_entrega"),
    ENTREGUE("entregue"),
    RECUSADO("recusado"),
    CANCELADO("cancelado");

    private final String id;

    LavanderiaOrderStatus(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public static Optional<LavanderiaOrderStatus> fromId(String id) {
        if (id == null) {
            return Optional.empty();
        }
        return Arrays.stream(values()).filter(s -> s.id.equals(id)).findFirst();
    }

    /** Transições permitidas a partir deste status. */
    public Set<LavanderiaOrderStatus> allowedNext() {
        return switch (this) {
            case AGUARDANDO -> Set.of(COLETADO, RECUSADO, CANCELADO);
            case COLETADO -> Set.of(EM_PROCESSO, CANCELADO);
            case EM_PROCESSO -> Set.of(PRONTO, CANCELADO);
            case PRONTO -> Set.of(SAIU_ENTREGA, CANCELADO);
            case SAIU_ENTREGA -> Set.of(ENTREGUE, CANCELADO);
            case ENTREGUE, RECUSADO, CANCELADO -> Set.of();
        };
    }

    public boolean canTransitionTo(LavanderiaOrderStatus next) {
        return allowedNext().contains(next);
    }

    /**
     * Texto fixo da notificação outbound disparada ao ENTRAR neste status. null = não notifica
     * ({@code aguardando} é silencioso — a IA já confirmou o recebimento na própria mensagem). No caso
     * de {@code recusado}, o MOTIVO (rejection_reason) é concatenado pelo Service, não aqui.
     */
    public String notificationText() {
        return switch (this) {
            case COLETADO -> "Recebemos suas peças, já vamos cuidar delas com todo o carinho. 🧺";
            case PRONTO -> "Boa notícia! Suas peças estão prontas. Em breve enviamos para entrega.";
            case SAIU_ENTREGA -> "Suas peças saíram para entrega. Logo chegam aí!";
            case ENTREGUE -> "Pedido entregue! Esperamos que esteja tudo impecável. ✨";
            case RECUSADO -> "Infelizmente não conseguimos atender seu pedido. Pedimos desculpa pelo transtorno.";
            case CANCELADO -> "Seu pedido foi cancelado. Se quiser refazer, é só me chamar.";
            // aguardando/em_processo não notificam (recebimento já confirmado; processamento é interno).
            case AGUARDANDO, EM_PROCESSO -> null;
        };
    }
}
