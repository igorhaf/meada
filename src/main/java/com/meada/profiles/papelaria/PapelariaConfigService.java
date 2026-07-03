package com.meada.profiles.papelaria;

import com.meada.common.audit.AuditLogger;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

/**
 * Regras da config do tenant papelaria (camada 8.15): taxa de entrega + pedido mínimo + lead time
 * padrão. Lê com fallback (DEFAULT); PATCH upsert + audita + invalida o {@link PapelariaCatalogCache}
 * (taxa/mínimo/lead time aparecem no prompt). Clone do SuplementosConfigService + o 3º campo
 * {@code leadTimeDaysDefault}.
 */
@Service
public class PapelariaConfigService {

    private final PapelariaConfigRepository repository;
    private final AuditLogger auditLogger;
    private final PapelariaCatalogCache catalogCache;

    public PapelariaConfigService(PapelariaConfigRepository repository, AuditLogger auditLogger,
                                  PapelariaCatalogCache catalogCache) {
        this.repository = repository;
        this.auditLogger = auditLogger;
        this.catalogCache = catalogCache;
    }

    public PapelariaConfig get(UUID companyId) {
        return repository.findByCompany(companyId);
    }

    @Transactional
    public PapelariaConfig update(UUID companyId, UUID userId, int deliveryFeeCents, int minOrderCents,
                                  int leadTimeDaysDefault) {
        PapelariaConfig saved = repository.upsert(companyId, Math.max(0, deliveryFeeCents),
            Math.max(0, minOrderCents), Math.max(0, leadTimeDaysDefault));
        auditLogger.log(companyId, userId, "papelaria_config_updated", "papelaria_config", companyId, Map.of());
        catalogCache.invalidate(companyId);
        return saved;
    }
}
