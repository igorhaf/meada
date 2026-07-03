package com.meada.profiles.concessionaria.wishlists;

import com.meada.profiles.concessionaria.vehicles.ConcessionariaVehicle;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;
import java.util.Optional;

/**
 * Acesso a {@code concessionaria_wishlists} (onda 1, backlog #1). service_role; escopo por
 * company_id. {@link #findMatches} é o coração do alerta: desejos ATIVOS que casam com um veículo
 * (brand/model por ILIKE de substring, teto de preço, ano mínimo).
 */
@Repository
public class ConcessionariaWishlistRepository {

    private static final RowMapper<ConcessionariaWishlist> MAPPER = (rs, rn) -> {
        Object maxPrice = rs.getObject("max_price_cents");
        Object minYear = rs.getObject("min_year");
        java.sql.Timestamp notifiedAt = rs.getTimestamp("notified_at");
        return new ConcessionariaWishlist(
            (UUID) rs.getObject("id"),
            (UUID) rs.getObject("company_id"),
            (UUID) rs.getObject("contact_id"),
            (UUID) rs.getObject("conversation_id"),
            rs.getString("contact_name"),
            rs.getString("brand"),
            rs.getString("model"),
            maxPrice == null ? null : ((Number) maxPrice).intValue(),
            minYear == null ? null : ((Number) minYear).intValue(),
            rs.getString("notes"),
            rs.getBoolean("active"),
            notifiedAt == null ? null : notifiedAt.toInstant(),
            (UUID) rs.getObject("notified_vehicle_id"),
            rs.getTimestamp("created_at").toInstant(),
            rs.getTimestamp("updated_at").toInstant());
    };

    private static final String SELECT =
        "select w.id, w.company_id, w.contact_id, w.conversation_id, ct.name as contact_name, "
            + "w.brand, w.model, w.max_price_cents, w.min_year, w.notes, w.active, w.notified_at, "
            + "w.notified_vehicle_id, w.created_at, w.updated_at "
            + "from concessionaria_wishlists w join contacts ct on ct.id = w.contact_id ";

    private final JdbcTemplate jdbcTemplate;

    public ConcessionariaWishlistRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<ConcessionariaWishlist> listByCompany(UUID companyId, boolean onlyActive) {
        String sql = SELECT + "where w.company_id = ?"
            + (onlyActive ? " and w.active = true" : "")
            + " order by w.created_at desc";
        return jdbcTemplate.query(sql, MAPPER, companyId);
    }

    public Optional<ConcessionariaWishlist> findById(UUID companyId, UUID id) {
        return jdbcTemplate.query(SELECT + "where w.company_id = ? and w.id = ?", MAPPER, companyId, id)
            .stream().findFirst();
    }

    public ConcessionariaWishlist insert(UUID companyId, UUID contactId, UUID conversationId,
                                         String brand, String model, Integer maxPriceCents,
                                         Integer minYear, String notes) {
        UUID id = jdbcTemplate.queryForObject(
            "insert into concessionaria_wishlists (company_id, contact_id, conversation_id, brand, "
                + "model, max_price_cents, min_year, notes) values (?, ?, ?, ?, ?, ?, ?, ?) returning id",
            UUID.class, companyId, contactId, conversationId, brand, model, maxPriceCents, minYear, notes);
        return findById(companyId, id).orElseThrow();
    }

    /** Desejos ATIVOS que casam com o veículo (critérios nulos não filtram). */
    public List<ConcessionariaWishlist> findMatches(UUID companyId, ConcessionariaVehicle vehicle) {
        return jdbcTemplate.query(
            SELECT + "where w.company_id = ? and w.active = true "
                + "and (w.brand is null or ? ilike '%' || w.brand || '%') "
                + "and (w.model is null or ? ilike '%' || w.model || '%') "
                + "and (w.max_price_cents is null or ? <= w.max_price_cents) "
                + "and (w.min_year is null or coalesce(?, 0) >= w.min_year) "
                + "order by w.created_at asc",
            MAPPER, companyId, vehicle.brand(), vehicle.model(), vehicle.priceCents(),
            vehicle.modelYear());
    }

    /** ONE-SHOT: marca o alerta enviado e DESATIVA o desejo (o cliente pode registrar de novo). */
    public void markNotified(UUID wishlistId, UUID vehicleId) {
        jdbcTemplate.update(
            "update concessionaria_wishlists set active = false, notified_at = now(), "
                + "notified_vehicle_id = ?, updated_at = now() where id = ?",
            vehicleId, wishlistId);
    }

    public Optional<ConcessionariaWishlist> setActive(UUID companyId, UUID id, boolean active) {
        int n = jdbcTemplate.update(
            "update concessionaria_wishlists set active = ?, updated_at = now() "
                + "where company_id = ? and id = ?",
            active, companyId, id);
        return n == 0 ? Optional.empty() : findById(companyId, id);
    }

    public boolean delete(UUID companyId, UUID id) {
        return jdbcTemplate.update(
            "delete from concessionaria_wishlists where company_id = ? and id = ?", companyId, id) > 0;
    }
}
