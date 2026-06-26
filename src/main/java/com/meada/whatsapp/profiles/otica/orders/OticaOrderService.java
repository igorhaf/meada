package com.meada.whatsapp.profiles.otica.orders;

import com.meada.whatsapp.profiles.otica.config.OticaConfig;
import com.meada.whatsapp.profiles.otica.config.OticaConfigRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Regras das encomendas de óculos otica (camada 8.12, FLUXO B). Clone de
 * {@link com.meada.whatsapp.profiles.floricultura.orders.FloriculturaOrderService} + a ESCAPADA do
 * PRAZO (lead-time) e da RECEITA.
 *
 * <p>{@link #create} é chamado pelo {@code EncomendaOticaConfirmHandler} (vindo da IA). Lê o lead
 * padrão do {@link OticaConfig} do tenant e delega ao repositório — que recalcula os totais a partir
 * do catálogo + opções (IGNORA o total que a IA mandou), valida o prazo, e persiste a receita AS-IS.
 *
 * <p>{@link #updateStatus} valida a transição (→ 409 se inválida) e, ao persistir, dispara a
 * notificação outbound do novo status via {@link OticaOrderNotifier}. O aceite/recusa é AÇÃO HUMANA
 * (a IA não transiciona).
 */
@Service
public class OticaOrderService {

    private final OticaOrderRepository orderRepository;
    private final OticaConfigRepository configRepository;
    private final OticaOrderNotifier notifier;

    public OticaOrderService(OticaOrderRepository orderRepository,
                             OticaConfigRepository configRepository,
                             OticaOrderNotifier notifier) {
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

    /** ready_date ausente/cedo demais p/ item sob encomenda (→ 422 lead_time_violation). */
    public static class LeadTimeViolationException extends RuntimeException {
        private final transient LocalDate earliest;

        public LeadTimeViolationException(LocalDate earliest) {
            this.earliest = earliest;
        }

        public LocalDate earliest() {
            return earliest;
        }
    }

    /**
     * Cria um pedido a partir das linhas confirmadas pela IA. O lead padrão vem do config do tenant.
     * O repositório faz o snapshot de preço+nome+opções, recalcula os totais (descarta o da IA),
     * valida o prazo e persiste a receita AS-IS. Mapeia a violação de prazo do repo em
     * {@link LeadTimeViolationException}.
     */
    @Transactional
    public OticaOrder create(UUID companyId, UUID conversationId, UUID contactId,
                             List<OticaOrderLineInput> lines, String notes, LocalDate readyDate,
                             OticaPrescription rx) {
        OticaConfig config = configRepository.findByCompany(companyId);
        try {
            return orderRepository.createOrder(companyId, conversationId, contactId, lines, notes,
                readyDate, rx, config.leadTimeDaysDefault());
        } catch (OticaOrderRepository.LeadTimeViolationException e) {
            throw new LeadTimeViolationException(e.earliest());
        }
    }

    public List<OticaOrder> list(UUID companyId, String status, int limit, int offset) {
        return orderRepository.listByCompany(companyId, status, limit, offset);
    }

    public long count(UUID companyId, String status) {
        return orderRepository.countByCompany(companyId, status);
    }

    public Optional<OticaOrder> get(UUID companyId, UUID id) {
        return orderRepository.findById(companyId, id);
    }

    /**
     * Transiciona o status do pedido (gate humano). Valida o alvo (enum) e a transição. Persiste —
     * gravando rejection_reason SÓ quando o alvo é {@code recusado} — e notifica o cliente com o
     * texto fixo do novo status (concatenando o motivo na recusa, defensivamente). A notificação é
     * best-effort (não reverte).
     */
    @Transactional
    public OticaOrder updateStatus(UUID companyId, UUID id, String newStatusId, String rejectionReason) {
        OticaOrderStatus newStatus = OticaOrderStatus.fromId(newStatusId)
            .orElseThrow(InvalidStatusException::new);

        OticaOrder current = orderRepository.findById(companyId, id)
            .orElseThrow(OrderNotFoundException::new);
        OticaOrderStatus from = OticaOrderStatus.fromId(current.status())
            .orElseThrow(InvalidStatusException::new);

        if (!from.canTransitionTo(newStatus)) {
            throw new InvalidStatusTransitionException();
        }

        String reasonToPersist = newStatus == OticaOrderStatus.RECUSADO ? rejectionReason : null;
        orderRepository.updateStatus(companyId, id, newStatus.id(), reasonToPersist);

        String text = newStatus.notificationText();
        if (newStatus == OticaOrderStatus.RECUSADO && rejectionReason != null && !rejectionReason.isBlank()) {
            text = text + " Motivo: " + rejectionReason.strip();
        }
        if (text != null) {
            notifier.notifyStatus(companyId, current.conversationId(), text);
        }

        return orderRepository.findById(companyId, id).orElseThrow(OrderNotFoundException::new);
    }
}
