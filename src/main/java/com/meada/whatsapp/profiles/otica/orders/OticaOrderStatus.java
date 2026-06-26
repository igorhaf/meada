package com.meada.whatsapp.profiles.otica.orders;

import java.util.Arrays;
import java.util.Optional;
import java.util.Set;

/**
 * Status de uma encomenda de óculos (camada 8.12, perfil otica FLUXO B) — clone de
 * {@link com.meada.whatsapp.profiles.floricultura.orders.FloriculturaOrderStatus}. Inclui o GATE DE
 * ACEITE HUMANO (aguardando → em_montagem / recusado, ações HUMANAS no painel — a IA NÃO transiciona).
 * Óculos pronto = RETIRADA na loja (sem entrega nesta SM).
 * <pre>
 *   aguardando   → em_montagem, recusado
 *   em_montagem  → pronto, cancelado
 *   pronto       → retirado, cancelado
 *   retirado     → (terminal)
 *   recusado     → (terminal)
 *   cancelado    → (terminal)
 * </pre>
 * Transição inválida → 409 invalid_status_transition no controller. Espelhado 1:1 por
 * {@code frontend/profiles/otica/otica-order-status.ts} (OticaOrderStatusParityTest).
 */
public enum OticaOrderStatus {
    AGUARDANDO("aguardando"),
    EM_MONTAGEM("em_montagem"),
    PRONTO("pronto"),
    RETIRADO("retirado"),
    RECUSADO("recusado"),
    CANCELADO("cancelado");

    private final String id;

    OticaOrderStatus(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public static Optional<OticaOrderStatus> fromId(String id) {
        if (id == null) {
            return Optional.empty();
        }
        return Arrays.stream(values()).filter(s -> s.id.equals(id)).findFirst();
    }

    /** Transições permitidas a partir deste status. */
    public Set<OticaOrderStatus> allowedNext() {
        return switch (this) {
            case AGUARDANDO -> Set.of(EM_MONTAGEM, RECUSADO);
            case EM_MONTAGEM -> Set.of(PRONTO, CANCELADO);
            case PRONTO -> Set.of(RETIRADO, CANCELADO);
            case RETIRADO, RECUSADO, CANCELADO -> Set.of();
        };
    }

    public boolean canTransitionTo(OticaOrderStatus next) {
        return allowedNext().contains(next);
    }

    /**
     * Texto fixo da notificação outbound disparada ao ENTRAR neste status. null = não notifica
     * ({@code aguardando} é silencioso — a IA já confirmou o RECEBIMENTO na própria mensagem;
     * {@code retirado} e {@code cancelado} também não notificam). No caso de {@code recusado}, o
     * MOTIVO (rejection_reason) é concatenado pelo Service, não aqui (o enum é estático). Textos
     * DEFENSIVOS, sem promessa clínica.
     */
    public String notificationText() {
        return switch (this) {
            case EM_MONTAGEM -> "Seu pedido foi aceito! Seu óculos já entrou em montagem. 👓";
            case PRONTO -> "Boa notícia: seu óculos está pronto pra retirada na loja!";
            case RECUSADO -> "Infelizmente não conseguimos atender seu pedido. Pedimos desculpa pelo transtorno.";
            // aguardando/retirado/cancelado não notificam.
            case AGUARDANDO, RETIRADO, CANCELADO -> null;
        };
    }
}
