package com.meada.whatsapp.profiles.cursos.config;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Time;
import java.time.LocalTime;
import java.util.UUID;

/**
 * Acesso a {@code cursos_config} (camada 8.20 / perfil cursos). 1:1 com company. Ausente → defaults.
 * service_role. Análogo ao AcademiaConfigRepository (camada 7.7) com o campo extra {@code notes}.
 */
@Repository
public class CursosConfigRepository {

    private final JdbcTemplate jdbcTemplate;

    public CursosConfigRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public CursosConfig findByCompany(UUID companyId) {
        return jdbcTemplate.query(
                "select opens_at, closes_at, notes from cursos_config where company_id = ?",
                (rs, rn) -> new CursosConfig(
                    companyId,
                    rs.getObject("opens_at", LocalTime.class),
                    rs.getObject("closes_at", LocalTime.class),
                    rs.getString("notes")),
                companyId)
            .stream().findFirst().orElse(CursosConfig.defaultFor(companyId));
    }

    public CursosConfig upsert(UUID companyId, LocalTime opensAt, LocalTime closesAt, String notes) {
        jdbcTemplate.update(
            "insert into cursos_config (company_id, opens_at, closes_at, notes) values (?, ?, ?, ?) "
                + "on conflict (company_id) do update set "
                + "opens_at = excluded.opens_at, closes_at = excluded.closes_at, notes = excluded.notes, "
                + "updated_at = now()",
            companyId, Time.valueOf(opensAt), Time.valueOf(closesAt), notes);
        return findByCompany(companyId);
    }
}
