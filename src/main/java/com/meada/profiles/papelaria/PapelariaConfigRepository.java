package com.meada.profiles.papelaria;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Acesso a {@code papelaria_config} (camada 8.15 / perfil papelaria). 1:1 com company. Ausente →
 * {@link PapelariaConfig#DEFAULT}. Clone de
 * {@link com.meada.profiles.padaria.PadariaConfigRepository} (camada 8.8). Opera via
 * service_role; o escopo por company_id no WHERE é a defesa.
 */
@Repository
public class PapelariaConfigRepository {

    private final JdbcTemplate jdbcTemplate;

    public PapelariaConfigRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** Config do tenant, ou {@link PapelariaConfig#DEFAULT} se não houver linha. */
    public PapelariaConfig findByCompany(UUID companyId) {
        return jdbcTemplate.query(
                "select delivery_fee_cents, min_order_cents, lead_time_days_default "
                    + "from papelaria_config where company_id = ?",
                (rs, rn) -> new PapelariaConfig(
                    rs.getInt("delivery_fee_cents"), rs.getInt("min_order_cents"),
                    rs.getInt("lead_time_days_default")),
                companyId)
            .stream().findFirst().orElse(PapelariaConfig.DEFAULT);
    }

    /** Upsert da config (insert ou update por company_id). Mantém updated_at. */
    public PapelariaConfig upsert(UUID companyId, int deliveryFeeCents, int minOrderCents,
                                  int leadTimeDaysDefault) {
        jdbcTemplate.update(
            "insert into papelaria_config (company_id, delivery_fee_cents, min_order_cents, lead_time_days_default) "
                + "values (?, ?, ?, ?) "
                + "on conflict (company_id) do update set "
                + "delivery_fee_cents = excluded.delivery_fee_cents, "
                + "min_order_cents = excluded.min_order_cents, "
                + "lead_time_days_default = excluded.lead_time_days_default, updated_at = now()",
            companyId, deliveryFeeCents, minOrderCents, leadTimeDaysDefault);
        return findByCompany(companyId);
    }
}
