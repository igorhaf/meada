package com.meada.whatsapp.profiles.concessionaria.vehicles;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Acesso a {@code concessionaria_vehicles} (camada 8.17). Opera via service_role; escopo por
 * company_id no WHERE é a defesa. {@code listAvailable} é a VITRINE (status='disponivel' AND active).
 */
@Repository
public class ConcessionariaVehicleRepository {

    private static final RowMapper<ConcessionariaVehicle> MAPPER = (rs, rn) -> new ConcessionariaVehicle(
        (UUID) rs.getObject("id"),
        (UUID) rs.getObject("company_id"),
        rs.getString("brand"),
        rs.getString("model"),
        (Integer) rs.getObject("model_year"),
        (Integer) rs.getObject("mileage_km"),
        rs.getInt("price_cents"),
        rs.getString("color"),
        rs.getString("fuel"),
        rs.getString("transmission"),
        rs.getString("plate"),
        rs.getString("photo_url"),
        rs.getString("description"),
        rs.getString("status"),
        rs.getBoolean("active"),
        rs.getTimestamp("created_at").toInstant(),
        rs.getTimestamp("updated_at").toInstant(),
        rs.getTimestamp("status_updated_at").toInstant());

    private static final String COLS =
        "id, company_id, brand, model, model_year, mileage_km, price_cents, color, fuel, transmission, "
            + "plate, photo_url, description, status, active, created_at, updated_at, status_updated_at";

    private final JdbcTemplate jdbcTemplate;

    public ConcessionariaVehicleRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** Lista veículos do tenant (filtros opcionais: status, active). */
    public List<ConcessionariaVehicle> listByCompany(UUID companyId, String status, Boolean active, String search) {
        StringBuilder sql = new StringBuilder(
            "select " + COLS + " from concessionaria_vehicles where company_id = ?");
        List<Object> args = new ArrayList<>();
        args.add(companyId);
        if (status != null && !status.isBlank()) { sql.append(" and status = ?"); args.add(status); }
        if (active != null) { sql.append(" and active = ?"); args.add(active); }
        if (search != null && !search.isBlank()) {
            sql.append(" and (brand ilike ? or model ilike ?)");
            String like = "%" + search.trim() + "%";
            args.add(like);
            args.add(like);
        }
        sql.append(" order by created_at desc");
        return jdbcTemplate.query(sql.toString(), MAPPER, args.toArray());
    }

    /** VITRINE: veículos disponíveis e ativos (status='disponivel' AND active=true). */
    public List<ConcessionariaVehicle> listAvailable(UUID companyId) {
        return jdbcTemplate.query(
            "select " + COLS + " from concessionaria_vehicles where company_id = ? "
                + "and status = 'disponivel' and active = true order by created_at desc",
            MAPPER, companyId);
    }

    public Optional<ConcessionariaVehicle> findById(UUID companyId, UUID id) {
        return jdbcTemplate.query(
                "select " + COLS + " from concessionaria_vehicles where company_id = ? and id = ?",
                MAPPER, companyId, id)
            .stream().findFirst();
    }

    public ConcessionariaVehicle insert(UUID companyId, String brand, String model, Integer modelYear,
                                        Integer mileageKm, int priceCents, String color, String fuel,
                                        String transmission, String plate, String photoUrl, String description) {
        UUID id = jdbcTemplate.queryForObject(
            "insert into concessionaria_vehicles (company_id, brand, model, model_year, mileage_km, "
                + "price_cents, color, fuel, transmission, plate, photo_url, description) "
                + "values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) returning id",
            UUID.class, companyId, brand.trim(), model.trim(), modelYear, mileageKm, priceCents, color,
            fuel, transmission, plate, photoUrl, description);
        return findById(companyId, id).orElseThrow();
    }

    public Optional<ConcessionariaVehicle> update(UUID companyId, UUID id, String brand, String model,
                                                  Integer modelYear, boolean modelYearProvided,
                                                  Integer mileageKm, boolean mileageProvided,
                                                  Integer priceCents, String color, String fuel,
                                                  String transmission, String plate, String photoUrl,
                                                  String description, Boolean active) {
        List<String> sets = new ArrayList<>();
        List<Object> args = new ArrayList<>();
        if (brand != null && !brand.isBlank()) { sets.add("brand = ?"); args.add(brand.trim()); }
        if (model != null && !model.isBlank()) { sets.add("model = ?"); args.add(model.trim()); }
        if (modelYearProvided) { sets.add("model_year = ?"); args.add(modelYear); }
        if (mileageProvided) { sets.add("mileage_km = ?"); args.add(mileageKm); }
        if (priceCents != null) { sets.add("price_cents = ?"); args.add(priceCents); }
        if (color != null) { sets.add("color = ?"); args.add(color); }
        if (fuel != null) { sets.add("fuel = ?"); args.add(fuel); }
        if (transmission != null) { sets.add("transmission = ?"); args.add(transmission); }
        if (plate != null) { sets.add("plate = ?"); args.add(plate); }
        if (photoUrl != null) { sets.add("photo_url = ?"); args.add(photoUrl); }
        if (description != null) { sets.add("description = ?"); args.add(description); }
        if (active != null) { sets.add("active = ?"); args.add(active); }
        if (!sets.isEmpty()) {
            sets.add("updated_at = now()");
            args.add(companyId);
            args.add(id);
            int n = jdbcTemplate.update(
                "update concessionaria_vehicles set " + String.join(", ", sets)
                    + " where company_id = ? and id = ?",
                args.toArray());
            if (n == 0) {
                return Optional.empty();
            }
        }
        return findById(companyId, id);
    }

    /** Persiste a transição de status de estoque + status_updated_at. Service já validou a transição. */
    public Optional<ConcessionariaVehicle> updateStatus(UUID companyId, UUID id, String newStatus) {
        int n = jdbcTemplate.update(
            "update concessionaria_vehicles set status = ?, status_updated_at = now(), updated_at = now() "
                + "where company_id = ? and id = ?",
            newStatus, companyId, id);
        return n == 0 ? Optional.empty() : findById(companyId, id);
    }

    public boolean delete(UUID companyId, UUID id) {
        return jdbcTemplate.update(
            "delete from concessionaria_vehicles where company_id = ? and id = ?", companyId, id) > 0;
    }

    /**
     * True se o veículo está referenciado por algum test-drive ou lead (ambas FK restrict). Checagem
     * explícita p/ devolver 409 vehicle_in_use em vez do erro genérico de FK.
     */
    public boolean hasReferences(UUID companyId, UUID id) {
        Integer td = jdbcTemplate.queryForObject(
            "select count(*) from concessionaria_test_drives where company_id = ? and vehicle_id = ?",
            Integer.class, companyId, id);
        if (td != null && td > 0) {
            return true;
        }
        Integer leads = jdbcTemplate.queryForObject(
            "select count(*) from concessionaria_leads where company_id = ? and vehicle_id = ?",
            Integer.class, companyId, id);
        return leads != null && leads > 0;
    }
}
