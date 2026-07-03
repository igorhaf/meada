package com.meada.profiles.casamento.coupons;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Acesso a {@code wedding_coupons} (onda 1 do casamento, backlog #10 — clone do motor atelie). service_role;
 * escopo por company_id. {@link #findByCode} é case-insensitive (UNIQUE company_id+lower(code)).
 * {@link #incrementUses}/{@link #decrementUses} rodam na MESMA transação de aplicar/remover o cupom
 * da proposta.
 */
@Repository
public class WeddingCouponRepository {

    private static final RowMapper<WeddingCoupon> MAPPER = (rs, rn) -> {
        Date vu = rs.getDate("valid_until");
        Object maxUses = rs.getObject("max_uses");
        return new WeddingCoupon(
            (UUID) rs.getObject("id"),
            (UUID) rs.getObject("company_id"),
            rs.getString("code"),
            rs.getString("kind"),
            rs.getInt("value"),
            rs.getInt("min_order_cents"),
            maxUses == null ? null : ((Number) maxUses).intValue(),
            rs.getInt("uses"),
            vu == null ? null : vu.toLocalDate(),
            rs.getBoolean("active"),
            rs.getTimestamp("created_at").toInstant(),
            rs.getTimestamp("updated_at").toInstant());
    };

    private static final String COLS =
        "id, company_id, code, kind, value, min_order_cents, max_uses, uses, valid_until, active, "
            + "created_at, updated_at";

    private final JdbcTemplate jdbcTemplate;

    public WeddingCouponRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<WeddingCoupon> listByCompany(UUID companyId) {
        return jdbcTemplate.query(
            "select " + COLS + " from wedding_coupons where company_id = ? order by code asc",
            MAPPER, companyId);
    }

    public Optional<WeddingCoupon> findById(UUID companyId, UUID id) {
        return jdbcTemplate.query(
                "select " + COLS + " from wedding_coupons where company_id = ? and id = ?",
                MAPPER, companyId, id)
            .stream().findFirst();
    }

    /** Busca por code case-insensitive (lower(code)). */
    public Optional<WeddingCoupon> findByCode(UUID companyId, String code) {
        if (code == null || code.isBlank()) {
            return Optional.empty();
        }
        return jdbcTemplate.query(
                "select " + COLS + " from wedding_coupons where company_id = ? and lower(code) = lower(?)",
                MAPPER, companyId, code.trim())
            .stream().findFirst();
    }

    public WeddingCoupon insert(UUID companyId, String code, String kind, int value, int minOrderCents,
                               Integer maxUses, LocalDate validUntil, boolean active) {
        UUID id = jdbcTemplate.queryForObject(
            "insert into wedding_coupons (company_id, code, kind, value, min_order_cents, max_uses, "
                + "valid_until, active) values (?, ?, ?, ?, ?, ?, ?, ?) returning id",
            UUID.class, companyId, code.trim(), kind, value, minOrderCents, maxUses,
            validUntil == null ? null : Date.valueOf(validUntil), active);
        return findById(companyId, id).orElseThrow();
    }

    /** PATCH parcial. validUntil/maxUses controlados por flags "provided" (podem ser limpos p/ null). */
    public Optional<WeddingCoupon> update(UUID companyId, UUID id, String code, String kind, Integer value,
                                         Integer minOrderCents, Integer maxUses, boolean maxUsesProvided,
                                         LocalDate validUntil, boolean validUntilProvided, Boolean active) {
        List<String> sets = new ArrayList<>();
        List<Object> args = new ArrayList<>();
        if (code != null && !code.isBlank()) { sets.add("code = ?"); args.add(code.trim()); }
        if (kind != null && !kind.isBlank()) { sets.add("kind = ?"); args.add(kind); }
        if (value != null) { sets.add("value = ?"); args.add(value); }
        if (minOrderCents != null) { sets.add("min_order_cents = ?"); args.add(minOrderCents); }
        if (maxUsesProvided) { sets.add("max_uses = ?"); args.add(maxUses); }
        if (validUntilProvided) {
            sets.add("valid_until = ?");
            args.add(validUntil == null ? null : Date.valueOf(validUntil));
        }
        if (active != null) { sets.add("active = ?"); args.add(active); }
        if (!sets.isEmpty()) {
            sets.add("updated_at = now()");
            args.add(companyId);
            args.add(id);
            int n = jdbcTemplate.update(
                "update wedding_coupons set " + String.join(", ", sets)
                    + " where company_id = ? and id = ?",
                args.toArray());
            if (n == 0) {
                return Optional.empty();
            }
        }
        return findById(companyId, id);
    }

    public Optional<WeddingCoupon> toggle(UUID companyId, UUID id, boolean active) {
        int n = jdbcTemplate.update(
            "update wedding_coupons set active = ?, updated_at = now() where company_id = ? and id = ?",
            active, companyId, id);
        return n == 0 ? Optional.empty() : findById(companyId, id);
    }

    public boolean delete(UUID companyId, UUID id) {
        return jdbcTemplate.update(
            "delete from wedding_coupons where company_id = ? and id = ?", companyId, id) > 0;
    }

    /** Incrementa uses (na transação de aplicar o cupom na proposta). */
    public void incrementUses(UUID companyId, UUID id) {
        jdbcTemplate.update(
            "update wedding_coupons set uses = uses + 1, updated_at = now() "
                + "where company_id = ? and id = ?",
            companyId, id);
    }

    /** Decrementa uses com piso 0 (na transação de remover o cupom da proposta). */
    public void decrementUses(UUID companyId, UUID id) {
        jdbcTemplate.update(
            "update wedding_coupons set uses = greatest(uses - 1, 0), updated_at = now() "
                + "where company_id = ? and id = ?",
            companyId, id);
    }
}
