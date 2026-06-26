package com.meada.whatsapp.profiles.cursos.payments;

import com.meada.whatsapp.common.audit.AuditLogger;
import com.meada.whatsapp.profiles.cursos.enrollments.CursosEnrollment;
import com.meada.whatsapp.profiles.cursos.enrollments.CursosEnrollmentRepository;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Regras dos pagamentos manuais do tenant cursos (camada 8.20 / perfil cursos). Clone do
 * AcademiaPaymentService (camada 7.7) com {@code enrollment} no lugar de {@code membership}: registra
 * pagamento mensal só em matrícula ATIVA, impede duplicidade no mês (UNIQUE → 409), e calcula um
 * resumo (último mês pago + meses em aberto). NÃO invalida o CursosContextCache (pagamento não entra
 * no contexto da IA por ora).
 */
@Service
public class CursosPaymentService {

    static final ZoneId TENANT_ZONE = ZoneId.of("America/Sao_Paulo");

    private final CursosPaymentRepository repository;
    private final CursosEnrollmentRepository enrollmentRepository;
    private final AuditLogger auditLogger;

    public CursosPaymentService(CursosPaymentRepository repository,
                                CursosEnrollmentRepository enrollmentRepository,
                                AuditLogger auditLogger) {
        this.repository = repository;
        this.enrollmentRepository = enrollmentRepository;
        this.auditLogger = auditLogger;
    }

    public static class EnrollmentNotFoundException extends RuntimeException {}
    public static class EnrollmentNotActiveException extends RuntimeException {}
    public static class DuplicatePaymentException extends RuntimeException {}
    public static class PaymentNotFoundException extends RuntimeException {}

    /** Resumo de pagamentos: último mês pago + meses em aberto (decorridos desde start_date - pagos). */
    public record PaymentSummary(LocalDate lastPaidMonth, int monthsOpen, int totalPayments) {}

    @Transactional
    public CursosPayment record(UUID companyId, UUID userId, UUID enrollmentId, LocalDate referenceMonth,
                                int amountCents, String method, String notes) {
        CursosEnrollment e = enrollmentRepository.findById(companyId, enrollmentId)
            .orElseThrow(EnrollmentNotFoundException::new);
        if (!"ativa".equals(e.status())) {
            throw new EnrollmentNotActiveException();
        }
        CursosPayment created;
        try {
            // normaliza pro dia 01 do mês de referência.
            LocalDate firstOfMonth = referenceMonth.withDayOfMonth(1);
            created = repository.insert(companyId, enrollmentId, firstOfMonth, amountCents, method, notes);
        } catch (DuplicateKeyException ex) {
            throw new DuplicatePaymentException();
        }
        auditLogger.log(companyId, userId, "cursos_payment_recorded", "cursos_payment",
            created.id(), Map.of("enrollment_id", enrollmentId.toString(), "amount_cents", amountCents));
        return created;
    }

    public List<CursosPayment> listByEnrollment(UUID companyId, UUID enrollmentId) {
        return repository.listByEnrollment(companyId, enrollmentId);
    }

    public Optional<CursosEnrollment> getEnrollment(UUID companyId, UUID enrollmentId) {
        return enrollmentRepository.findById(companyId, enrollmentId);
    }

    /**
     * Resumo: último mês pago + meses em aberto. monthsOpen = meses decorridos desde o start_date
     * (inclusivo, +1) menos os pagamentos lançados, nunca negativo. Cálculo simples (sem juros/multa).
     */
    public PaymentSummary summary(UUID companyId, UUID enrollmentId, LocalDate startDate) {
        Optional<LocalDate> last = repository.lastPaidMonth(companyId, enrollmentId);
        int paid = repository.countByEnrollment(companyId, enrollmentId);
        LocalDate today = LocalDate.now(TENANT_ZONE);
        long monthsElapsed = ChronoUnit.MONTHS.between(startDate.withDayOfMonth(1), today.withDayOfMonth(1)) + 1;
        int monthsOpen = (int) Math.max(0, monthsElapsed - paid);
        return new PaymentSummary(last.orElse(null), monthsOpen, paid);
    }

    @Transactional
    public void delete(UUID companyId, UUID userId, UUID paymentId) {
        if (!repository.delete(companyId, paymentId)) {
            throw new PaymentNotFoundException();
        }
        auditLogger.log(companyId, userId, "cursos_payment_deleted", "cursos_payment", paymentId, Map.of());
    }
}
