package com.meada.profiles.adega;

import com.meada.common.audit.AuditLogger;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

/**
 * Regras da config do tenant adega (camada 8.9): taxa de entrega + pedido mínimo. Lê com
 * fallback (ZERO); PATCH upsert + audita + invalida o {@link AdegaMenuCache} (taxa/mínimo
 * aparecem no prompt). Clone do padrão do SuplementosConfigService.
 */
@Service
public class AdegaConfigService {

    private final AdegaConfigRepository repository;
    private final AuditLogger auditLogger;
    private final AdegaMenuCache menuCache;

    public AdegaConfigService(AdegaConfigRepository repository, AuditLogger auditLogger,
                              AdegaMenuCache menuCache) {
        this.repository = repository;
        this.auditLogger = auditLogger;
        this.menuCache = menuCache;
    }

    public AdegaConfig get(UUID companyId) {
        return repository.findByCompany(companyId);
    }

    @Transactional
    public AdegaConfig update(UUID companyId, UUID userId, int deliveryFeeCents, int minOrderCents) {
        AdegaConfig saved = repository.upsert(companyId, Math.max(0, deliveryFeeCents),
            Math.max(0, minOrderCents));
        auditLogger.log(companyId, userId, "adega_config_updated", "adega_config", companyId, Map.of());
        menuCache.invalidate(companyId);
        return saved;
    }
}
