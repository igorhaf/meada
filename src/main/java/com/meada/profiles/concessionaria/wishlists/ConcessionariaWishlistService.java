package com.meada.profiles.concessionaria.wishlists;

import com.meada.common.audit.AuditLogger;
import com.meada.profiles.concessionaria.ConcessionariaContextCache;
import com.meada.profiles.concessionaria.testdrives.ConcessionariaTestDriveNotifier;
import com.meada.profiles.concessionaria.vehicles.ConcessionariaVehicle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Regras da lista de desejos (onda 1 da concessionária, backlog #1). Criação vem da TAG
 * {@code <desejo_carro>} (conversa) ou do painel; exige pelo menos brand OU model → 400
 * invalid_wishlist. {@link #matchAndNotify} é chamado pelo ConcessionariaVehicleService sempre que
 * um veículo fica DISPONÍVEL (criado disponível, editado, ou reserva desfeita): desejos ativos que
 * casam disparam o alerta (texto fixo, defensivo — a IA não participa) e DESATIVAM (one-shot).
 * Sem canal (desejo criado no painel sem conversa) → marca sem enviar.
 */
@Service
public class ConcessionariaWishlistService {

    private static final Logger log = LoggerFactory.getLogger(ConcessionariaWishlistService.class);

    private final ConcessionariaWishlistRepository repository;
    private final ConcessionariaTestDriveNotifier notifier;
    private final AuditLogger auditLogger;
    private final ConcessionariaContextCache contextCache;

    public ConcessionariaWishlistService(ConcessionariaWishlistRepository repository,
                                         ConcessionariaTestDriveNotifier notifier,
                                         AuditLogger auditLogger,
                                         ConcessionariaContextCache contextCache) {
        this.repository = repository;
        this.notifier = notifier;
        this.auditLogger = auditLogger;
        this.contextCache = contextCache;
    }

    public static class WishlistNotFoundException extends RuntimeException {}
    public static class InvalidWishlistException extends RuntimeException {}

    public List<ConcessionariaWishlist> list(UUID companyId, boolean onlyActive) {
        return repository.listByCompany(companyId, onlyActive);
    }

    /** Criação (tag da IA ou painel). userId null quando vem da conversa (sem auditoria de usuário). */
    @Transactional
    public ConcessionariaWishlist create(UUID companyId, UUID userId, UUID contactId, UUID conversationId,
                                         String brand, String model, Integer maxPriceCents,
                                         Integer minYear, String notes) {
        String b = brand == null || brand.isBlank() ? null : brand.trim();
        String m = model == null || model.isBlank() ? null : model.trim();
        if ((b == null && m == null) || contactId == null
            || (maxPriceCents != null && maxPriceCents <= 0)
            || (minYear != null && minYear < 1950)) {
            throw new InvalidWishlistException();
        }
        ConcessionariaWishlist created = repository.insert(companyId, contactId, conversationId,
            b, m, maxPriceCents, minYear, notes);
        if (userId != null) {
            auditLogger.log(companyId, userId, "concessionaria_wishlist_created", "concessionaria_wishlist",
                created.id(), Map.of());
        }
        contextCache.invalidate(companyId);
        return created;
    }

    @Transactional
    public ConcessionariaWishlist setActive(UUID companyId, UUID userId, UUID id, boolean active) {
        ConcessionariaWishlist updated = repository.setActive(companyId, id, active)
            .orElseThrow(WishlistNotFoundException::new);
        auditLogger.log(companyId, userId, "concessionaria_wishlist_toggled", "concessionaria_wishlist",
            id, Map.of("active", active));
        contextCache.invalidate(companyId);
        return updated;
    }

    @Transactional
    public void delete(UUID companyId, UUID userId, UUID id) {
        if (!repository.delete(companyId, id)) {
            throw new WishlistNotFoundException();
        }
        auditLogger.log(companyId, userId, "concessionaria_wishlist_deleted", "concessionaria_wishlist",
            id, Map.of());
        contextCache.invalidate(companyId);
    }

    /**
     * ALERTA (#1): dispara para todo desejo ATIVO que casa com o veículo DISPONÍVEL. Texto fixo e
     * defensivo (sem promessa de reserva/preço). One-shot: marca + desativa mesmo sem canal (não
     * revarre). Best-effort — falha de envio não interrompe os demais.
     *
     * @return quantos desejos foram notificados/marcados
     */
    public int matchAndNotify(UUID companyId, ConcessionariaVehicle vehicle) {
        if (!"disponivel".equals(vehicle.status()) || !vehicle.active()) {
            return 0;
        }
        int touched = 0;
        for (ConcessionariaWishlist w : repository.findMatches(companyId, vehicle)) {
            try {
                if (w.conversationId() != null) {
                    String preco = "R$ " + String.format("%d,%02d",
                        vehicle.priceCents() / 100, vehicle.priceCents() % 100);
                    notifier.notifyStatus(companyId, w.conversationId(),
                        "Boa notícia, " + w.contactName() + "! Chegou no nosso estoque um "
                            + vehicle.brand() + " " + vehicle.model()
                            + (vehicle.modelYear() != null ? " " + vehicle.modelYear() : "")
                            + " por " + preco + " — bem o que você estava procurando. "
                            + "Quer agendar um test-drive ou saber mais? 🚗");
                } else {
                    log.info("concessionaria-wishlist: desejo {} sem canal — marcado sem envio", w.id());
                }
                repository.markNotified(w.id(), vehicle.id());
                touched++;
            } catch (Exception e) {
                log.warn("concessionaria-wishlist: falha ao notificar desejo {} ({})", w.id(), e.getMessage());
            }
        }
        if (touched > 0) {
            contextCache.invalidate(companyId);
        }
        return touched;
    }
}
