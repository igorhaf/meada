package com.meada.profiles.atelie.measurements;

import com.meada.common.audit.AuditLogger;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Regras da tabela de medidas do atelie (onda 2, backlog #9). Upsert por (contato, lower(label)) —
 * regravar a mesma medida atualiza o valor (reuso na recompra). Contato de outro tenant/inexistente
 * → contact_not_found. label/value vazios ou &gt; 100 chars → invalid_measurement. NÃO invalida o
 * contexto da IA — as medidas NÃO são injetadas no prompt (trava do nicho).
 */
@Service
public class AtelieMeasurementService {

    private final AtelieMeasurementRepository repository;
    private final AuditLogger auditLogger;

    public AtelieMeasurementService(AtelieMeasurementRepository repository, AuditLogger auditLogger) {
        this.repository = repository;
        this.auditLogger = auditLogger;
    }

    public static class ContactNotFoundException extends RuntimeException {}
    public static class MeasurementNotFoundException extends RuntimeException {}
    public static class InvalidMeasurementException extends RuntimeException {}

    public List<AtelieMeasurement> list(UUID companyId, UUID contactId) {
        if (!repository.contactBelongsToCompany(companyId, contactId)) {
            throw new ContactNotFoundException();
        }
        return repository.listByContact(companyId, contactId);
    }

    @Transactional
    public AtelieMeasurement upsert(UUID companyId, UUID userId, UUID contactId, String label, String value) {
        if (label == null || label.isBlank() || label.trim().length() > 100
            || value == null || value.isBlank() || value.trim().length() > 100) {
            throw new InvalidMeasurementException();
        }
        if (!repository.contactBelongsToCompany(companyId, contactId)) {
            throw new ContactNotFoundException();
        }
        AtelieMeasurement saved = repository.upsert(companyId, contactId, label, value);
        auditLogger.log(companyId, userId, "atelie_measurement_upserted", "atelie_measurement",
            saved.id(), Map.of("label", saved.label()));
        return saved;
    }

    @Transactional
    public void delete(UUID companyId, UUID userId, UUID id) {
        if (!repository.delete(companyId, id)) {
            throw new MeasurementNotFoundException();
        }
        auditLogger.log(companyId, userId, "atelie_measurement_deleted", "atelie_measurement", id, Map.of());
    }
}
