package com.meada.profiles.restaurant.tables;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Acesso a {@code restaurant_tables} (camada 7.3). Opera via service_role; o escopo por
 * company_id no WHERE de cada query é a defesa (o backend não passa pelo RLS do tenant).
 */
@Repository
public class RestaurantTableRepository {

    private static final RowMapper<RestaurantTable> MAPPER = (rs, rn) -> new RestaurantTable(
        (UUID) rs.getObject("id"),
        rs.getString("label"),
        rs.getInt("capacity"),
        rs.getBoolean("available"),
        rs.getString("notes"),
        rs.getTimestamp("created_at").toInstant(),
        rs.getTimestamp("updated_at").toInstant());

    private static final String COLS =
        "id, label, capacity, available, notes, created_at, updated_at";

    private final JdbcTemplate jdbcTemplate;

    public RestaurantTableRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** Lista mesas do tenant, opcionalmente só as disponíveis. Ordena por label. */
    public List<RestaurantTable> listByCompany(UUID companyId, boolean onlyAvailable) {
        StringBuilder sql = new StringBuilder(
            "select " + COLS + " from restaurant_tables where company_id = ?");
        List<Object> args = new ArrayList<>();
        args.add(companyId);
        if (onlyAvailable) {
            sql.append(" and available = true");
        }
        sql.append(" order by label asc");
        return jdbcTemplate.query(sql.toString(), MAPPER, args.toArray());
    }

    public Optional<RestaurantTable> findById(UUID companyId, UUID id) {
        return jdbcTemplate.query(
                "select " + COLS + " from restaurant_tables where company_id = ? and id = ?",
                MAPPER, companyId, id)
            .stream().findFirst();
    }

    /** Insere. Lança DataIntegrityViolation se o label colidir (UNIQUE company_id,label). */
    public RestaurantTable insert(UUID companyId, String label, int capacity, String notes) {
        UUID id = jdbcTemplate.queryForObject(
            "insert into restaurant_tables (company_id, label, capacity, notes) "
                + "values (?, ?, ?, ?) returning id",
            UUID.class, companyId, label.trim(), capacity, notes);
        return findById(companyId, id).orElseThrow();
    }

    /**
     * Atualiza campos não-null (PATCH parcial). Retorna a mesa atualizada, ou empty se não
     * existir/pertencer ao tenant. Lança DataIntegrityViolation se o novo label colidir.
     */
    public Optional<RestaurantTable> update(UUID companyId, UUID id, String label, Integer capacity,
                                            String notes, Boolean available) {
        List<String> sets = new ArrayList<>();
        List<Object> args = new ArrayList<>();
        if (label != null && !label.isBlank()) { sets.add("label = ?"); args.add(label.trim()); }
        if (capacity != null) { sets.add("capacity = ?"); args.add(capacity); }
        // notes: null = não mexe; string vazia = limpa (frontend manda "").
        if (notes != null) { sets.add("notes = ?"); args.add(notes); }
        if (available != null) { sets.add("available = ?"); args.add(available); }

        if (!sets.isEmpty()) {
            sets.add("updated_at = now()");
            args.add(companyId);
            args.add(id);
            int n = jdbcTemplate.update(
                "update restaurant_tables set " + String.join(", ", sets)
                    + " where company_id = ? and id = ?",
                args.toArray());
            if (n == 0) {
                return Optional.empty();
            }
        }
        return findById(companyId, id);
    }

    /** Atalho dedicado para o toggle de disponibilidade. */
    public Optional<RestaurantTable> toggle(UUID companyId, UUID id, boolean available) {
        int n = jdbcTemplate.update(
            "update restaurant_tables set available = ?, updated_at = now() "
                + "where company_id = ? and id = ?",
            available, companyId, id);
        return n == 0 ? Optional.empty() : findById(companyId, id);
    }

    /** Hard delete. Lança DataIntegrityViolation se houver reserva referenciando (FK restrict). */
    public boolean delete(UUID companyId, UUID id) {
        return jdbcTemplate.update(
            "delete from restaurant_tables where company_id = ? and id = ?", companyId, id) > 0;
    }
}
