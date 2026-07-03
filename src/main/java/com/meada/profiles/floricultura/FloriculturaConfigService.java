package com.meada.profiles.floricultura;

import com.meada.common.audit.AuditLogger;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

/**
 * Regras da config do tenant floricultura (camada 8.5): taxa de entrega + pedido mínimo. Lê com
 * fallback (ZERO); PATCH upsert + audita + invalida o {@link FloriculturaCatalogCache} (taxa/mínimo
 * aparecem no prompt). Clone do padrão do SuplementosConfigService.
 */
@Service
public class FloriculturaConfigService {

    private final FloriculturaConfigRepository repository;
    private final AuditLogger auditLogger;
    private final FloriculturaCatalogCache catalogCache;

    public FloriculturaConfigService(FloriculturaConfigRepository repository, AuditLogger auditLogger,
                                     FloriculturaCatalogCache catalogCache) {
        this.repository = repository;
        this.auditLogger = auditLogger;
        this.catalogCache = catalogCache;
    }

    public FloriculturaConfig get(UUID companyId) {
        return repository.findByCompany(companyId);
    }

    @Transactional
    public FloriculturaConfig update(UUID companyId, UUID userId, int deliveryFeeCents, int minOrderCents) {
        FloriculturaConfig saved = repository.upsert(companyId, Math.max(0, deliveryFeeCents),
            Math.max(0, minOrderCents));
        auditLogger.log(companyId, userId, "floricultura_config_updated", "floricultura_config", companyId, Map.of());
        catalogCache.invalidate(companyId);
        return saved;
    }
}
