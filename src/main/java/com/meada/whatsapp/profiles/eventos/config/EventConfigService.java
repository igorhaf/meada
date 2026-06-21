package com.meada.whatsapp.profiles.eventos.config;

import com.meada.whatsapp.common.audit.AuditLogger;
import com.meada.whatsapp.profiles.eventos.EventosContextCache;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

/**
 * Regras da config do tenant eventos (camada 8.2). Lê com fallback; PUT upsert + audita + invalida
 * cache. SEM validação de horário — não há agenda (só nome do espaço + notas).
 */
@Service
public class EventConfigService {

    private final EventConfigRepository repository;
    private final AuditLogger auditLogger;
    private final EventosContextCache contextCache;

    public EventConfigService(EventConfigRepository repository, AuditLogger auditLogger,
                              EventosContextCache contextCache) {
        this.repository = repository;
        this.auditLogger = auditLogger;
        this.contextCache = contextCache;
    }

    public EventConfig get(UUID companyId) {
        return repository.findByCompany(companyId);
    }

    @Transactional
    public EventConfig update(UUID companyId, UUID userId, String businessName, String notes) {
        EventConfig saved = repository.upsert(companyId, businessName, notes);
        auditLogger.log(companyId, userId, "event_config_updated", "event_config", companyId, Map.of());
        contextCache.invalidate(companyId);
        return saved;
    }
}
