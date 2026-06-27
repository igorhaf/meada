package com.meada.profiles.oficina.vehicles;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Acesso a {@code os_vehicles} (camada 7.9). Sub-entidade do cliente (contact). Opera via
 * service_role; escopo por company_id. {@link #contactExists} valida que o cliente é do company
 * (sem estender o ContactRepository do core, compartilhado). plate UNIQUE por company — violação
 * sobe como DataIntegrityViolationException (o service mapeia → plate_taken).
 */
@Repository
public class OsVehicleRepository {

    private static final RowMapper<OsVehicle> MAPPER = (rs, rn) -> new OsVehicle(
        (UUID) rs.getObject("id"),
        (UUID) rs.getObject("contact_id"),
        rs.getString("plate"),
        rs.getString("brand"),
        rs.getString("model"),
        (Integer) rs.getObject("year"),
        rs.getString("color"),
        (Integer) rs.getObject("mileage_km"),
        rs.getString("notes"),
        rs.getBoolean("active"),
        rs.getTimestamp("created_at").toInstant(),
        rs.getTimestamp("updated_at").toInstant());

    private static final String COLS =
        "id, contact_id, plate, brand, model, year, color, mileage_km, notes, active, created_at, updated_at";

    private final JdbcTemplate jdbcTemplate;

    public OsVehicleRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** True se o contato existe e é do company (valida o cliente antes de criar o veículo). */
    public boolean contactExists(UUID companyId, UUID contactId) {
        Integer n = jdbcTemplate.queryForObject(
            "select count(*) from contacts where id = ? and company_id = ?", Integer.class, contactId, companyId);
        return n != null && n > 0;
    }

    /** Nome do contato (cliente) — para snapshot na OS. */
    public Optional<String> contactName(UUID companyId, UUID contactId) {
        return jdbcTemplate.query("select name from contacts where id = ? and company_id = ?",
                (rs, rn) -> rs.getString("name"), contactId, companyId)
            .stream().findFirst();
    }

    /** Telefone do contato (cliente) — para snapshot na OS. */
    public Optional<String> contactPhone(UUID companyId, UUID contactId) {
        return jdbcTemplate.query("select phone_number from contacts where id = ? and company_id = ?",
                (rs, rn) -> rs.getString("phone_number"), contactId, companyId)
            .stream().findFirst();
    }

    public List<OsVehicle> listByCompany(UUID companyId, UUID contactId, Boolean active, String search) {
        StringBuilder sql = new StringBuilder("select " + COLS + " from os_vehicles where company_id = ?");
        List<Object> args = new ArrayList<>();
        args.add(companyId);
        if (contactId != null) { sql.append(" and contact_id = ?"); args.add(contactId); }
        if (active != null) { sql.append(" and active = ?"); args.add(active); }
        if (search != null && !search.isBlank()) {
            sql.append(" and (plate ilike ? or model ilike ? or brand ilike ?)");
            String like = "%" + search.trim() + "%";
            args.add(like); args.add(like); args.add(like);
        }
        sql.append(" order by plate asc");
        return jdbcTemplate.query(sql.toString(), MAPPER, args.toArray());
    }

    public List<OsVehicle> listByContact(UUID companyId, UUID contactId, boolean onlyActive) {
        return listByCompany(companyId, contactId, onlyActive ? Boolean.TRUE : null, null);
    }

    public Optional<OsVehicle> findById(UUID companyId, UUID id) {
        return jdbcTemplate.query("select " + COLS + " from os_vehicles where company_id = ? and id = ?",
                MAPPER, companyId, id)
            .stream().findFirst();
    }

    public OsVehicle insert(UUID companyId, UUID contactId, String plate, String brand, String model,
                            Integer year, String color, Integer mileageKm, String notes) {
        UUID id = jdbcTemplate.queryForObject(
            "insert into os_vehicles (company_id, contact_id, plate, brand, model, year, color, mileage_km, notes) "
                + "values (?, ?, ?, ?, ?, ?, ?, ?, ?) returning id",
            UUID.class, companyId, contactId, plate.trim(), brand, model, year, color, mileageKm, notes);
        return findById(companyId, id).orElseThrow();
    }

    public Optional<OsVehicle> update(UUID companyId, UUID id, String plate, String brand, String model,
                                      Integer year, String color, Integer mileageKm, String notes, Boolean active) {
        List<String> sets = new ArrayList<>();
        List<Object> args = new ArrayList<>();
        if (plate != null && !plate.isBlank()) { sets.add("plate = ?"); args.add(plate.trim()); }
        if (brand != null) { sets.add("brand = ?"); args.add(brand); }
        if (model != null) { sets.add("model = ?"); args.add(model); }
        if (year != null) { sets.add("year = ?"); args.add(year); }
        if (color != null) { sets.add("color = ?"); args.add(color); }
        if (mileageKm != null) { sets.add("mileage_km = ?"); args.add(mileageKm); }
        if (notes != null) { sets.add("notes = ?"); args.add(notes); }
        if (active != null) { sets.add("active = ?"); args.add(active); }
        if (!sets.isEmpty()) {
            sets.add("updated_at = now()");
            args.add(companyId);
            args.add(id);
            int n = jdbcTemplate.update("update os_vehicles set " + String.join(", ", sets)
                + " where company_id = ? and id = ?", args.toArray());
            if (n == 0) {
                return Optional.empty();
            }
        }
        return findById(companyId, id);
    }

    public Optional<OsVehicle> archive(UUID companyId, UUID id) {
        int n = jdbcTemplate.update("update os_vehicles set active = false, updated_at = now() "
            + "where company_id = ? and id = ?", companyId, id);
        return n == 0 ? Optional.empty() : findById(companyId, id);
    }

    public boolean delete(UUID companyId, UUID id) {
        return jdbcTemplate.update("delete from os_vehicles where company_id = ? and id = ?", companyId, id) > 0;
    }
}
