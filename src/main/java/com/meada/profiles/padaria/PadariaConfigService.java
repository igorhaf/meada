package com.meada.profiles.padaria;

import com.meada.common.audit.AuditLogger;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

/**
 * Regras da config do tenant padaria (camada 8.8): taxa de entrega + pedido mínimo + lead time
 * padrão. Lê com fallback (DEFAULT); PATCH upsert + audita + invalida o {@link PadariaMenuCache}
 * (taxa/mínimo/lead time aparecem no prompt). Clone do SuplementosConfigService + o 3º campo
 * {@code leadTimeDaysDefault}.
 */
@Service
public class PadariaConfigService {

    private final PadariaConfigRepository repository;
    private final AuditLogger auditLogger;
    private final PadariaMenuCache menuCache;

    public PadariaConfigService(PadariaConfigRepository repository, AuditLogger auditLogger,
                                PadariaMenuCache menuCache) {
        this.repository = repository;
        this.auditLogger = auditLogger;
        this.menuCache = menuCache;
    }

    public PadariaConfig get(UUID companyId) {
        return repository.findByCompany(companyId);
    }

    @Transactional
    public PadariaConfig update(UUID companyId, UUID userId, int deliveryFeeCents, int minOrderCents,
                                int leadTimeDaysDefault) {
        PadariaConfig saved = repository.upsert(companyId, Math.max(0, deliveryFeeCents),
            Math.max(0, minOrderCents), Math.max(0, leadTimeDaysDefault));
        auditLogger.log(companyId, userId, "padaria_config_updated", "padaria_config", companyId, Map.of());
        menuCache.invalidate(companyId);
        return saved;
    }
}
