package com.meada.whatsapp.profiles.nutri.config;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Time;
import java.time.LocalTime;
import java.util.UUID;

/** Acesso a {@code nutri_config} (camada 8.0). 1:1 com company. Ausente → defaults. service_role. */
@Repository
public class NutriConfigRepository {

    private final JdbcTemplate jdbcTemplate;

    public NutriConfigRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public NutriConfig findByCompany(UUID companyId) {
        return jdbcTemplate.query(
                "select opens_at, closes_at, buffer_minutes from nutri_config where company_id = ?",
                (rs, rn) -> new NutriConfig(companyId, rs.getObject("opens_at", LocalTime.class),
                    rs.getObject("closes_at", LocalTime.class), rs.getInt("buffer_minutes")),
                companyId)
            .stream().findFirst().orElse(NutriConfig.defaultFor(companyId));
    }

    public NutriConfig upsert(UUID companyId, LocalTime opensAt, LocalTime closesAt, int bufferMinutes) {
        jdbcTemplate.update(
            "insert into nutri_config (company_id, opens_at, closes_at, buffer_minutes) values (?, ?, ?, ?) "
                + "on conflict (company_id) do update set opens_at = excluded.opens_at, "
                + "closes_at = excluded.closes_at, buffer_minutes = excluded.buffer_minutes, updated_at = now()",
            companyId, Time.valueOf(opensAt), Time.valueOf(closesAt), bufferMinutes);
        return findByCompany(companyId);
    }
}
