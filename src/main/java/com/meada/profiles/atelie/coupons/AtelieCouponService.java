package com.meada.profiles.atelie.coupons;

import com.meada.common.audit.AuditLogger;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Regras dos cupons atelie (onda 2, backlog #13 — clone do motor adega). Valida code/kind/value
 * (percent 1..100, fixed >= 0) e min_order/max_uses, audita. code duplicado (UNIQUE case-insensitive)
 * → 409 duplicate_coupon. A APLICAÇÃO do cupom (validade/min/max + cálculo/re-derivação do desconto)
 * acontece no {@code AtelieProposalService.applyCoupon}, não aqui — aqui é só o CRUD do catálogo.
 */
@Service
public class AtelieCouponService {

    private final AtelieCouponRepository repository;
    private final AuditLogger auditLogger;

    public AtelieCouponService(AtelieCouponRepository repository, AuditLogger auditLogger) {
        this.repository = repository;
        this.auditLogger = auditLogger;
    }

    public static class CouponNotFoundException extends RuntimeException {}
    public static class DuplicateCouponException extends RuntimeException {}
    public static class InvalidCouponException extends RuntimeException {}

    @Transactional
    public AtelieCoupon create(UUID companyId, UUID userId, String code, String kind, int value,
                               Integer minOrderCents, Integer maxUses, LocalDate validUntil, Boolean active) {
        requireValidCode(code);
        requireValidKindValue(kind, value);
        int minOrder = minOrderCents == null ? 0 : minOrderCents;
        if (minOrder < 0 || (maxUses != null && maxUses < 0)) {
            throw new InvalidCouponException();
        }
        AtelieCoupon created;
        try {
            created = repository.insert(companyId, code, kind, value, minOrder, maxUses, validUntil,
                active == null || active);
        } catch (DataIntegrityViolationException e) {
            throw new DuplicateCouponException();
        }
        auditLogger.log(companyId, userId, "atelie_coupon_created", "atelie_coupon",
            created.id(), Map.of("code", created.code()));
        return created;
    }

    @Transactional
    public AtelieCoupon update(UUID companyId, UUID userId, UUID id, String code, String kind,
                               Integer value, Integer minOrderCents, Integer maxUses, boolean maxUsesProvided,
                               LocalDate validUntil, boolean validUntilProvided, Boolean active) {
        if (code != null && !code.isBlank()) {
            requireValidCode(code);
        } else if (code != null) {
            throw new InvalidCouponException();
        }
        // Para validar kind/value coerentes, resolve o estado efetivo (mistura o que veio + o atual).
        if (kind != null || value != null) {
            AtelieCoupon current = repository.findById(companyId, id)
                .orElseThrow(CouponNotFoundException::new);
            String effKind = kind != null && !kind.isBlank() ? kind : current.kind();
            int effValue = value != null ? value : current.value();
            requireValidKindValue(effKind, effValue);
        }
        if (minOrderCents != null && minOrderCents < 0) {
            throw new InvalidCouponException();
        }
        if (maxUsesProvided && maxUses != null && maxUses < 0) {
            throw new InvalidCouponException();
        }
        AtelieCoupon updated;
        try {
            updated = repository.update(companyId, id, code, kind, value, minOrderCents, maxUses,
                    maxUsesProvided, validUntil, validUntilProvided, active)
                .orElseThrow(CouponNotFoundException::new);
        } catch (DataIntegrityViolationException e) {
            throw new DuplicateCouponException();
        }
        auditLogger.log(companyId, userId, "atelie_coupon_updated", "atelie_coupon", id, Map.of());
        return updated;
    }

    @Transactional
    public AtelieCoupon toggle(UUID companyId, UUID userId, UUID id, boolean active) {
        AtelieCoupon c = repository.toggle(companyId, id, active)
            .orElseThrow(CouponNotFoundException::new);
        auditLogger.log(companyId, userId, "atelie_coupon_updated", "atelie_coupon", id,
            Map.of("active", active));
        return c;
    }

    @Transactional
    public void delete(UUID companyId, UUID userId, UUID id) {
        if (!repository.delete(companyId, id)) {
            throw new CouponNotFoundException();
        }
        auditLogger.log(companyId, userId, "atelie_coupon_deleted", "atelie_coupon", id, Map.of());
    }

    public List<AtelieCoupon> list(UUID companyId) {
        return repository.listByCompany(companyId);
    }

    public Optional<AtelieCoupon> get(UUID companyId, UUID id) {
        return repository.findById(companyId, id);
    }

    private static void requireValidCode(String code) {
        if (code == null || code.isBlank() || code.trim().length() > 40) {
            throw new InvalidCouponException();
        }
    }

    private static void requireValidKindValue(String kind, int value) {
        if (!"percent".equals(kind) && !"fixed".equals(kind)) {
            throw new InvalidCouponException();
        }
        if ("percent".equals(kind)) {
            if (value < 1 || value > 100) {
                throw new InvalidCouponException();
            }
        } else { // fixed
            if (value < 0) {
                throw new InvalidCouponException();
            }
        }
    }
}
