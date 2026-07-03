package com.meada.profiles.lingerie;

import com.meada.common.audit.AuditLogger;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

/**
 * Regras da config do tenant lingerie (camada 8.21): taxa de entrega + pedido mínimo. Lê com
 * fallback (ZERO); PATCH upsert + audita + invalida o {@link LingerieMenuCache} (taxa/mínimo
 * aparecem no prompt).
 */
@Service
public class LingerieConfigService {

    private final LingerieConfigRepository repository;
    private final AuditLogger auditLogger;
    private final LingerieMenuCache menuCache;

    public LingerieConfigService(LingerieConfigRepository repository, AuditLogger auditLogger,
                                 LingerieMenuCache menuCache) {
        this.repository = repository;
        this.auditLogger = auditLogger;
        this.menuCache = menuCache;
    }

    public LingerieConfig get(UUID companyId) {
        return repository.findByCompany(companyId);
    }

    @Transactional
    public LingerieConfig update(UUID companyId, UUID userId, int deliveryFeeCents, int minOrderCents) {
        LingerieConfig saved = repository.upsert(companyId, Math.max(0, deliveryFeeCents),
            Math.max(0, minOrderCents));
        auditLogger.log(companyId, userId, "lingerie_config_updated", "lingerie_config", companyId, Map.of());
        menuCache.invalidate(companyId);
        return saved;
    }
}
