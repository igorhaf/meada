package com.meada.whatsapp.profiles.papelaria.orders;

import java.util.Arrays;
import java.util.Optional;
import java.util.Set;

/**
 * Status de um pedido papelaria (camada 8.15 / perfil papelaria) — clone de
 * {@link com.meada.whatsapp.profiles.padaria.orders.PadariaOrderStatus} (camada 8.8) + o ESTADO EXTRA
 * da ESCAPADA: {@code arte_aprovacao} (entre 'aceito' e 'em_producao'). Inclui o GATE DE ACEITE humano
 * (aguardando → aceito / recusado) e o FUNIL QUE DIVERGE no fim pela forma de entrega (retirada →
 * retirado; entrega → saiu_entrega → entregue).
 * <pre>
 *   aguardando      → aceito, recusado, cancelado
 *   aceito          → arte_aprovacao, em_producao, cancelado   (em_producao = caminho pronta-entrega/sem-arte)
 *   arte_aprovacao  → em_producao (SÓ se art_approved=true → senão 409 art_not_approved), cancelado
 *   em_producao     → pronto, cancelado
 *   pronto          → retirado, saiu_entrega, cancelado
 *   saiu_entrega    → entregue, cancelado
 *   retirado        → (terminal)
 *   entregue        → (terminal)
 *   recusado        → (terminal)
 *   cancelado       → (terminal)
 * </pre>
 * Transição inválida → 409 invalid_status_transition no controller. O gate {@code art_not_approved}
 * (arte_aprovacao→em_producao sem art_approved) vive no Service, não aqui (o enum é estático).
 * Espelhado 1:1 por {@code frontend/profiles/papelaria/papelaria-order-status.ts}
 * (PapelariaOrderStatusParityTest).
 */
public enum PapelariaOrderStatus {
    AGUARDANDO("aguardando"),
    ACEITO("aceito"),
    ARTE_APROVACAO("arte_aprovacao"),
    EM_PRODUCAO("em_producao"),
    PRONTO("pronto"),
    RETIRADO("retirado"),
    SAIU_ENTREGA("saiu_entrega"),
    ENTREGUE("entregue"),
    RECUSADO("recusado"),
    CANCELADO("cancelado");

    private final String id;

    PapelariaOrderStatus(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public static Optional<PapelariaOrderStatus> fromId(String id) {
        if (id == null) {
            return Optional.empty();
        }
        return Arrays.stream(values()).filter(s -> s.id.equals(id)).findFirst();
    }

    /** Transições permitidas a partir deste status. */
    public Set<PapelariaOrderStatus> allowedNext() {
        return switch (this) {
            case AGUARDANDO -> Set.of(ACEITO, RECUSADO, CANCELADO);
            case ACEITO -> Set.of(ARTE_APROVACAO, EM_PRODUCAO, CANCELADO);
            case ARTE_APROVACAO -> Set.of(EM_PRODUCAO, CANCELADO);
            case EM_PRODUCAO -> Set.of(PRONTO, CANCELADO);
            case PRONTO -> Set.of(RETIRADO, SAIU_ENTREGA, CANCELADO);
            case SAIU_ENTREGA -> Set.of(ENTREGUE, CANCELADO);
            case RETIRADO, ENTREGUE, RECUSADO, CANCELADO -> Set.of();
        };
    }

    public boolean canTransitionTo(PapelariaOrderStatus next) {
        return allowedNext().contains(next);
    }

    /**
     * Texto fixo da notificação outbound disparada ao ENTRAR neste status. null = não notifica
     * ({@code aguardando}, {@code cancelado} e {@code retirado} são silenciosos — a IA já confirmou o
     * RECEBIMENTO na própria mensagem; quem cancela não recebe sermão). No caso de {@code recusado}, o
     * MOTIVO (rejection_reason) é concatenado pelo Service, não aqui — o enum é estático.
     */
    public String notificationText() {
        return switch (this) {
            case ACEITO -> "Recebemos seu pedido! 🎉 Vamos preparar a arte do seu material e já te avisamos.";
            case ARTE_APROVACAO -> "Sua arte está pronta! Dê uma olhada e, se estiver tudo certo, é só aprovar pra gente imprimir. 🎨";
            case EM_PRODUCAO -> "Arte aprovada! ✅ Já mandamos pra impressão. Em breve avisamos quando estiver pronto.";
            case PRONTO -> "Seu pedido está pronto pra retirada! Pode vir buscar quando quiser.";
            case SAIU_ENTREGA -> "Seu pedido saiu pra entrega. Já já chega aí!";
            case ENTREGUE -> "Pedido entregue! Esperamos que ame o resultado. 💌";
            case RECUSADO -> "Infelizmente não conseguimos atender seu pedido. Pedimos desculpa pelo transtorno.";
            // aguardando/cancelado/retirado não notificam.
            case AGUARDANDO, RETIRADO, CANCELADO -> null;
        };
    }
}
