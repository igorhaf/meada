package com.meada.profiles.pizzaria;

import com.meada.common.audit.AuditLogger;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

/**
 * Regras da config do tenant pizzaria (camada 8.6): taxa de entrega + pedido mínimo. Lê com
 * fallback (ZERO); PATCH upsert + audita + invalida o {@link PizzariaMenuCache} (taxa/mínimo
 * aparecem no prompt).
 */
@Service
public class PizzariaConfigService {

    private final PizzariaConfigRepository repository;
    private final AuditLogger auditLogger;
    private final PizzariaMenuCache menuCache;

    public PizzariaConfigService(PizzariaConfigRepository repository, AuditLogger auditLogger,
                                 PizzariaMenuCache menuCache) {
        this.repository = repository;
        this.auditLogger = auditLogger;
        this.menuCache = menuCache;
    }

    public PizzariaConfig get(UUID companyId) {
        return repository.findByCompany(companyId);
    }

    @Transactional
    public PizzariaConfig update(UUID companyId, UUID userId, int deliveryFeeCents, int minOrderCents) {
        PizzariaConfig saved = repository.upsert(companyId, Math.max(0, deliveryFeeCents),
            Math.max(0, minOrderCents));
        auditLogger.log(companyId, userId, "pizzaria_config_updated", "pizzaria_config", companyId, Map.of());
        menuCache.invalidate(companyId);
        return saved;
    }
}
