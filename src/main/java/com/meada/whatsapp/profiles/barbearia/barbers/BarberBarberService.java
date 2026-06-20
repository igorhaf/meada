package com.meada.whatsapp.profiles.barbearia.barbers;

import com.meada.whatsapp.common.audit.AuditLogger;
import com.meada.whatsapp.profiles.barbearia.BarberContextCache;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Regras dos barbeiros (camada 8.1). Audita mutações e invalida o {@link BarberContextCache} — a IA
 * vê a mudança (quem está ativo) na hora. Espelho de SalonProfessionalService.
 *
 * <p>Diferença vs salon: o ticket da fila referencia barber_id com {@code on delete set null} (não
 * restrict), então a FK só barra o delete quando há AGENDAMENTO (restrict). Para honrar "barbeiro em
 * uso por ticket" também, o delete checa explicitamente tickets que ainda apontam pro barbeiro.
 */
@Service
public class BarberBarberService {

    private final BarberBarberRepository repository;
    private final JdbcTemplate jdbcTemplate;
    private final AuditLogger auditLogger;
    private final BarberContextCache contextCache;

    public BarberBarberService(BarberBarberRepository repository, JdbcTemplate jdbcTemplate,
                               AuditLogger auditLogger, BarberContextCache contextCache) {
        this.repository = repository;
        this.jdbcTemplate = jdbcTemplate;
        this.auditLogger = auditLogger;
        this.contextCache = contextCache;
    }

    /** Barbeiro não encontrado / de outro tenant (→ 404). */
    public static class BarberNotFoundException extends RuntimeException {}

    /** Barbeiro referenciado por agendamento ou ticket — não pode hard-deletar (→ 409). */
    public static class BarberInUseException extends RuntimeException {}

    @Transactional
    public BarberBarber create(UUID companyId, UUID userId, String name, String specialty, String notes) {
        BarberBarber created = repository.insert(companyId, name, specialty, notes);
        auditLogger.log(companyId, userId, "barber_barber_created", "barber_barber",
            created.id(), Map.of("name", created.name()));
        contextCache.invalidate(companyId);
        return created;
    }

    @Transactional
    public BarberBarber update(UUID companyId, UUID userId, UUID id, String name, String specialty,
                               String notes, Boolean active) {
        BarberBarber updated = repository.update(companyId, id, name, specialty, notes, active)
            .orElseThrow(BarberNotFoundException::new);
        auditLogger.log(companyId, userId, "barber_barber_updated", "barber_barber", id, Map.of());
        contextCache.invalidate(companyId);
        return updated;
    }

    @Transactional
    public BarberBarber toggle(UUID companyId, UUID userId, UUID id, boolean active) {
        BarberBarber b = repository.toggle(companyId, id, active)
            .orElseThrow(BarberNotFoundException::new);
        auditLogger.log(companyId, userId, "barber_barber_updated", "barber_barber", id,
            Map.of("active", active));
        contextCache.invalidate(companyId);
        return b;
    }

    @Transactional
    public void delete(UUID companyId, UUID userId, UUID id) {
        // O ticket aponta barber_id com on delete set null — checamos antes pra honrar "em uso".
        Long tickets = jdbcTemplate.queryForObject(
            "select count(*) from barber_queue_tickets where company_id = ? and barber_id = ?",
            Long.class, companyId, id);
        if (tickets != null && tickets > 0) {
            throw new BarberInUseException();
        }
        try {
            boolean deleted = repository.delete(companyId, id);
            if (!deleted) {
                throw new BarberNotFoundException();
            }
        } catch (DataIntegrityViolationException e) {
            // FK restrict de barber_appointments.
            throw new BarberInUseException();
        }
        auditLogger.log(companyId, userId, "barber_barber_deleted", "barber_barber", id, Map.of());
        contextCache.invalidate(companyId);
    }

    public List<BarberBarber> list(UUID companyId, boolean onlyActive) {
        return repository.listByCompany(companyId, onlyActive);
    }

    public Optional<BarberBarber> get(UUID companyId, UUID id) {
        return repository.findById(companyId, id);
    }
}
