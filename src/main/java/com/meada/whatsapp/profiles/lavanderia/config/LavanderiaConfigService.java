package com.meada.whatsapp.profiles.lavanderia.config;

import com.meada.whatsapp.common.audit.AuditLogger;
import com.meada.whatsapp.profiles.lavanderia.LavanderiaCatalogCache;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

/**
 * Regras da config do tenant lavanderia (camada 8.10). Lê com fallback; PUT upsert + audita + invalida
 * o {@link LavanderiaCatalogCache} (taxa/mínimo/turnaround default aparecem no prompt). Clone do padrão
 * do AtelieConfigService.
 */
@Service
public class LavanderiaConfigService {

    private final LavanderiaConfigRepository repository;
    private final AuditLogger auditLogger;
    private final LavanderiaCatalogCache catalogCache;

    public LavanderiaConfigService(LavanderiaConfigRepository repository, AuditLogger auditLogger,
                                   LavanderiaCatalogCache catalogCache) {
        this.repository = repository;
        this.auditLogger = auditLogger;
        this.catalogCache = catalogCache;
    }

    public LavanderiaConfig get(UUID companyId) {
        return repository.findByCompany(companyId);
    }

    @Transactional
    public LavanderiaConfig update(UUID companyId, UUID userId, int deliveryFeeCents, int minOrderCents,
                                   int turnaroundDaysDefault) {
        LavanderiaConfig saved = repository.upsert(companyId, Math.max(0, deliveryFeeCents),
            Math.max(0, minOrderCents), Math.max(0, turnaroundDaysDefault));
        auditLogger.log(companyId, userId, "lavanderia_config_updated", "lavanderia_config", companyId, Map.of());
        catalogCache.invalidate(companyId);
        return saved;
    }
}
