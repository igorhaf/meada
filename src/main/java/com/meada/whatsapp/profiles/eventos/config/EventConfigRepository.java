package com.meada.whatsapp.profiles.eventos.config;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Acesso a {@code event_config} (camada 8.2). 1:1 com company. Apenas nome do espaço + notas (SEM
 * horário/slot — não há agenda). Ausente → defaults (vazios). service_role.
 */
@Repository
public class EventConfigRepository {

    private final JdbcTemplate jdbcTemplate;

    public EventConfigRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public EventConfig findByCompany(UUID companyId) {
        return jdbcTemplate.query(
                "select business_name, notes from event_config where company_id = ?",
                (rs, rn) -> new EventConfig(companyId, rs.getString("business_name"), rs.getString("notes")),
                companyId)
            .stream().findFirst().orElse(EventConfig.defaultFor(companyId));
    }

    public EventConfig upsert(UUID companyId, String businessName, String notes) {
        jdbcTemplate.update(
            "insert into event_config (company_id, business_name, notes) values (?, ?, ?) "
                + "on conflict (company_id) do update set business_name = excluded.business_name, "
                + "notes = excluded.notes, updated_at = now()",
            companyId, businessName, notes);
        return findByCompany(companyId);
    }
}
