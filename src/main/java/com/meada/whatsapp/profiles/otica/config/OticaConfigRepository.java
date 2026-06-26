package com.meada.whatsapp.profiles.otica.config;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Time;
import java.time.LocalTime;
import java.util.UUID;

/**
 * Acesso a {@code otica_config} (camada 8.12). 1:1 com company. Ausente → defaults
 * ({@link OticaConfig#defaultFor}). Opera via service_role; o escopo por company_id no WHERE é a
 * defesa. Clone do {@code DentalClinicConfigRepository} + os campos do FLUXO B (mínimo/lead).
 */
@Repository
public class OticaConfigRepository {

    private final JdbcTemplate jdbcTemplate;

    public OticaConfigRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public OticaConfig findByCompany(UUID companyId) {
        return jdbcTemplate.query(
                "select opens_at, closes_at, exam_duration_minutes, min_order_cents, lead_time_days_default "
                    + "from otica_config where company_id = ?",
                (rs, rn) -> new OticaConfig(
                    companyId,
                    rs.getObject("opens_at", LocalTime.class),
                    rs.getObject("closes_at", LocalTime.class),
                    rs.getInt("exam_duration_minutes"),
                    rs.getInt("min_order_cents"),
                    rs.getInt("lead_time_days_default")),
                companyId)
            .stream().findFirst().orElse(OticaConfig.defaultFor(companyId));
    }

    /** Upsert (INSERT … ON CONFLICT) — cria ou atualiza a config 1:1 do tenant. */
    public OticaConfig upsert(UUID companyId, LocalTime opensAt, LocalTime closesAt,
                              int examDurationMinutes, int minOrderCents, int leadTimeDaysDefault) {
        jdbcTemplate.update(
            "insert into otica_config "
                + "(company_id, opens_at, closes_at, exam_duration_minutes, min_order_cents, lead_time_days_default) "
                + "values (?, ?, ?, ?, ?, ?) "
                + "on conflict (company_id) do update set "
                + "opens_at = excluded.opens_at, closes_at = excluded.closes_at, "
                + "exam_duration_minutes = excluded.exam_duration_minutes, "
                + "min_order_cents = excluded.min_order_cents, "
                + "lead_time_days_default = excluded.lead_time_days_default, updated_at = now()",
            companyId, Time.valueOf(opensAt), Time.valueOf(closesAt),
            examDurationMinutes, minOrderCents, leadTimeDaysDefault);
        return findByCompany(companyId);
    }
}
