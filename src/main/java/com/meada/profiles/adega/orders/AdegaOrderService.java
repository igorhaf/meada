package com.meada.profiles.adega.orders;

import com.meada.profiles.adega.AdegaConfig;
import com.meada.profiles.adega.AdegaConfigRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Regras dos pedidos adega (camada 8.9). Clone do chassi comida (gate de aceite com rejection_reason
 * na recusa) + a ESCAPADA +18 (trava de faixa etária).
 *
 * <p>{@link #create} é chamado pelo {@code PedidoAdegaConfirmHandler} (vindo da IA). <b>PRÉ-CONDIÇÃO
 * +18 (a escapada, ANTES de qualquer cálculo):</b> sem {@code ageConfirmed=true} lança
 * {@link AgeNotConfirmedException} (→ 422 age_not_confirmed); NENHUM pedido é criado. Com o flag, lê
 * a taxa de entrega do {@link AdegaConfig} e delega ao repositório — que recalcula os totais a partir
 * do cardápio + opções (IGNORA o total que a IA mandou) e PERSISTE {@code age_confirmed} pra
 * compliance.
 *
 * <p>{@link #updateStatus} valida a transição (→ 409 se inválida) e, ao persistir, dispara a
 * notificação outbound do novo status via {@link AdegaOrderNotifier}. O aceite/recusa é AÇÃO
 * HUMANA (a IA não transiciona).
 */
@Service
public class AdegaOrderService {

    private static final Logger log = LoggerFactory.getLogger(AdegaOrderService.class);

    private final AdegaOrderRepository orderRepository;
    private final AdegaConfigRepository configRepository;
    private final AdegaOrderNotifier notifier;

    public AdegaOrderService(AdegaOrderRepository orderRepository,
                              AdegaConfigRepository configRepository,
                              AdegaOrderNotifier notifier) {
        this.orderRepository = orderRepository;
        this.configRepository = configRepository;
        this.notifier = notifier;
    }

    /** Pedido não encontrado / de outro tenant (→ 404). */
    public static class OrderNotFoundException extends RuntimeException {}

    /** Transição de status inválida (→ 409 invalid_status_transition). */
    public static class InvalidStatusTransitionException extends RuntimeException {}

    /** Status alvo desconhecido (→ 400 invalid_status). */
    public static class InvalidStatusException extends RuntimeException {}

    /** ESCAPADA +18: pedido sem confirmação de maioridade (→ 422 age_not_confirmed). NENHUM pedido é criado. */
    public static class AgeNotConfirmedException extends RuntimeException {}

    /**
     * Cria um pedido a partir das linhas confirmadas pela IA. <b>PRÉ-CONDIÇÃO +18:</b> sem
     * {@code ageConfirmed=true} lança {@link AgeNotConfirmedException} ANTES de qualquer cálculo —
     * não há pedido de menor no banco. A taxa de entrega vem do config do tenant (0 se ausente). O
     * repositório faz o snapshot de preço+nome+opções, recalcula os totais (descarta o total da IA),
     * aplica cupom + fidelidade (backlog #1/#2 — cupom inválido NÃO aborta) e persiste
     * {@code age_confirmed} pra compliance.
     */
    @Transactional
    public AdegaOrder create(UUID companyId, UUID conversationId, UUID contactId,
                              String deliveryAddress, List<OrderLineInput> lines,
                              boolean ageConfirmed, String couponCode, String notes) {
        if (!ageConfirmed) {
            throw new AgeNotConfirmedException();   // trava +18 — não cria pedido sem maioridade confirmada.
        }
        AdegaConfig config = configRepository.findByCompany(companyId);
        return orderRepository.createOrder(
            companyId, conversationId, contactId, deliveryAddress, lines, couponCode,
            config.deliveryFeeCents(), ageConfirmed, notes);
    }

    public List<AdegaOrder> list(UUID companyId, String status, int limit, int offset) {
        return orderRepository.listByCompany(companyId, status, limit, offset);
    }

    public long count(UUID companyId, String status) {
        return orderRepository.countByCompany(companyId, status);
    }

    public Optional<AdegaOrder> get(UUID companyId, UUID id) {
        return orderRepository.findById(companyId, id);
    }

    /**
     * Transiciona o status do pedido (gate humano). Valida o alvo (enum) e a transição. Persiste —
     * gravando rejection_reason SÓ quando o alvo é {@code recusado} (ESCAPADA 1) — e notifica o
     * cliente com o texto fixo do novo status (concatenando o motivo na recusa, defensivamente). A
     * notificação é best-effort (não reverte).
     */
    @Transactional
    public AdegaOrder updateStatus(UUID companyId, UUID id, String newStatusId, String rejectionReason) {
        AdegaOrderStatus newStatus = AdegaOrderStatus.fromId(newStatusId)
            .orElseThrow(InvalidStatusException::new);

        AdegaOrder current = orderRepository.findById(companyId, id)
            .orElseThrow(OrderNotFoundException::new);
        AdegaOrderStatus from = AdegaOrderStatus.fromId(current.status())
            .orElseThrow(InvalidStatusException::new);

        if (!from.canTransitionTo(newStatus)) {
            throw new InvalidStatusTransitionException();
        }

        // rejection_reason só faz sentido na recusa; nas demais transições passa null.
        String reasonToPersist = newStatus == AdegaOrderStatus.RECUSADO ? rejectionReason : null;
        orderRepository.updateStatus(companyId, id, newStatus.id(), reasonToPersist);

        // Notificação outbound do novo status (best-effort; aguardando não chega aqui como alvo válido).
        String text = newStatus.notificationText();
        if (newStatus == AdegaOrderStatus.RECUSADO && rejectionReason != null && !rejectionReason.isBlank()) {
            text = text + " Motivo: " + rejectionReason.strip();
        }
        if (text != null) {
            notifier.notifyStatus(companyId, current.conversationId(), text);
        }

        return orderRepository.findById(companyId, id).orElseThrow(OrderNotFoundException::new);
    }
}
