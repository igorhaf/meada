package com.meada.profiles.casamento.payments;

import com.meada.common.audit.AuditLogger;
import com.meada.profiles.casamento.CasamentoContextCache;
import com.meada.profiles.casamento.WeddingProposalStatus;
import com.meada.profiles.casamento.proposals.WeddingProposal;
import com.meada.profiles.casamento.proposals.WeddingProposalRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Regras do plano de pagamento do contrato (onda 1, backlog #1). O plano é montado e marcado pago
 * À MÃO pela equipe (Pix conferido) até o gateway #50. DIFERENTE dos sub-itens da proposta
 * (itemsLocked a partir de 'fechada'), o plano segue MUTÁVEL depois de fechada — as parcelas vencem
 * até o dia do casamento; só recusada/cancelada travam (409 proposal_locked). kind sinal|parcela,
 * amount &gt; 0 → 400 invalid_payment. Invalida o contexto da IA (que INFORMA o plano ao casal).
 */
@Service
public class WeddingPaymentService {

    private final WeddingPaymentRepository repository;
    private final WeddingProposalRepository proposalRepository;
    private final AuditLogger auditLogger;
    private final CasamentoContextCache contextCache;

    public WeddingPaymentService(WeddingPaymentRepository repository,
                                 WeddingProposalRepository proposalRepository,
                                 AuditLogger auditLogger,
                                 CasamentoContextCache contextCache) {
        this.repository = repository;
        this.proposalRepository = proposalRepository;
        this.auditLogger = auditLogger;
        this.contextCache = contextCache;
    }

    public static class ProposalNotFoundException extends RuntimeException {}
    public static class PaymentNotFoundException extends RuntimeException {}
    public static class InvalidPaymentException extends RuntimeException {}
    public static class ProposalLockedException extends RuntimeException {}

    /** Plano mutável em qualquer status EXCETO recusada/cancelada (parcelas vencem até o casamento). */
    private void requirePayableProposal(UUID companyId, UUID proposalId) {
        WeddingProposal proposal = proposalRepository.findById(companyId, proposalId)
            .orElseThrow(ProposalNotFoundException::new);
        String status = proposal.status();
        if (WeddingProposalStatus.RECUSADA.id().equals(status)
            || WeddingProposalStatus.CANCELADA.id().equals(status)) {
            throw new ProposalLockedException();
        }
    }

    private static void requireValid(String kind, LocalDate dueDate, Integer amountCents) {
        if (kind != null && !kind.isBlank() && !"sinal".equals(kind) && !"parcela".equals(kind)) {
            throw new InvalidPaymentException();
        }
        if (amountCents != null && amountCents <= 0) {
            throw new InvalidPaymentException();
        }
    }

    public List<WeddingPayment> list(UUID companyId, UUID proposalId) {
        proposalRepository.findById(companyId, proposalId).orElseThrow(ProposalNotFoundException::new);
        return repository.listByProposal(companyId, proposalId);
    }

    @Transactional
    public WeddingPayment create(UUID companyId, UUID userId, UUID proposalId, String kind, String label,
                                 LocalDate dueDate, Integer amountCents) {
        requirePayableProposal(companyId, proposalId);
        String effKind = kind == null || kind.isBlank() ? "parcela" : kind;
        requireValid(effKind, dueDate, amountCents);
        if (dueDate == null || amountCents == null) {
            throw new InvalidPaymentException();
        }
        WeddingPayment created = repository.insert(companyId, proposalId, effKind, label, dueDate, amountCents);
        auditLogger.log(companyId, userId, "wedding_payment_created", "wedding_payment",
            created.id(), Map.of("kind", created.kind()));
        contextCache.invalidate(companyId);
        return created;
    }

    @Transactional
    public WeddingPayment update(UUID companyId, UUID userId, UUID proposalId, UUID paymentId, String kind,
                                 String label, boolean labelProvided, LocalDate dueDate, Integer amountCents) {
        requirePayableProposal(companyId, proposalId);
        requireValid(kind, dueDate, amountCents);
        // garante que a parcela pertence à proposta informada.
        WeddingPayment current = repository.findById(companyId, paymentId)
            .orElseThrow(PaymentNotFoundException::new);
        if (!current.proposalId().equals(proposalId)) {
            throw new PaymentNotFoundException();
        }
        WeddingPayment updated = repository.update(companyId, paymentId, kind, label, labelProvided,
            dueDate, amountCents).orElseThrow(PaymentNotFoundException::new);
        auditLogger.log(companyId, userId, "wedding_payment_updated", "wedding_payment", paymentId, Map.of());
        contextCache.invalidate(companyId);
        return updated;
    }

    /** Marca pago/não-pago (confirmação MANUAL do Pix pela equipe — até o gateway #50). */
    @Transactional
    public WeddingPayment setPaid(UUID companyId, UUID userId, UUID proposalId, UUID paymentId, boolean paid) {
        requirePayableProposal(companyId, proposalId);
        WeddingPayment current = repository.findById(companyId, paymentId)
            .orElseThrow(PaymentNotFoundException::new);
        if (!current.proposalId().equals(proposalId)) {
            throw new PaymentNotFoundException();
        }
        WeddingPayment updated = repository.setPaid(companyId, paymentId, paid)
            .orElseThrow(PaymentNotFoundException::new);
        auditLogger.log(companyId, userId, "wedding_payment_paid_toggled", "wedding_payment", paymentId,
            Map.of("paid", paid));
        contextCache.invalidate(companyId);
        return updated;
    }

    @Transactional
    public void delete(UUID companyId, UUID userId, UUID proposalId, UUID paymentId) {
        requirePayableProposal(companyId, proposalId);
        WeddingPayment current = repository.findById(companyId, paymentId)
            .orElseThrow(PaymentNotFoundException::new);
        if (!current.proposalId().equals(proposalId)) {
            throw new PaymentNotFoundException();
        }
        repository.delete(companyId, paymentId);
        auditLogger.log(companyId, userId, "wedding_payment_deleted", "wedding_payment", paymentId, Map.of());
        contextCache.invalidate(companyId);
    }
}
