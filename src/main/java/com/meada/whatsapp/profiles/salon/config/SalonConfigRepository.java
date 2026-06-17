package com.meada.whatsapp.profiles.salon.config;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Time;
import java.time.LocalTime;
import java.util.UUID;

/**
 * Acesso a {@code salon_config} (camada 7.5). 1:1 com company. Ausente → defaults. service_role.
 */
@Repository
public class SalonConfigRepository {

    private final JdbcTemplate jdbcTemplate;

    public SalonConfigRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public SalonConfig findByCompany(UUID companyId) {
        return jdbcTemplate.query(
                "select opens_at, closes_at, buffer_minutes from salon_config where company_id = ?",
                (rs, rn) -> new SalonConfig(
                    companyId,
                    rs.getObject("opens_at", LocalTime.class),
                    rs.getObject("closes_at", LocalTime.class),
                    rs.getInt("buffer_minutes")),
                companyId)
            .stream().findFirst().orElse(SalonConfig.defaultFor(companyId));
    }

    public SalonConfig upsert(UUID companyId, LocalTime opensAt, LocalTime closesAt, int bufferMinutes) {
        jdbcTemplate.update(
            "insert into salon_config (company_id, opens_at, closes_at, buffer_minutes) "
                + "values (?, ?, ?, ?) "
                + "on conflict (company_id) do update set "
                + "opens_at = excluded.opens_at, closes_at = excluded.closes_at, "
                + "buffer_minutes = excluded.buffer_minutes, updated_at = now()",
            companyId, Time.valueOf(opensAt), Time.valueOf(closesAt), bufferMinutes);
        return findByCompany(companyId);
    }
}
