package com.meada.whatsapp.profiles.lavanderia.config;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Acesso a {@code lavanderia_config} (camada 8.10). 1:1 com company. Ausente → {@link
 * LavanderiaConfig#DEFAULT} (0/0/1). Clone de
 * {@link com.meada.whatsapp.profiles.floricultura.FloriculturaConfigRepository} + turnaround default.
 * Opera via service_role; o escopo por company_id no WHERE é a defesa.
 */
@Repository
public class LavanderiaConfigRepository {

    private final JdbcTemplate jdbcTemplate;

    public LavanderiaConfigRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** Config do tenant, ou {@link LavanderiaConfig#DEFAULT} se não houver linha. */
    public LavanderiaConfig findByCompany(UUID companyId) {
        return jdbcTemplate.query(
                "select delivery_fee_cents, min_order_cents, turnaround_days_default "
                    + "from lavanderia_config where company_id = ?",
                (rs, rn) -> new LavanderiaConfig(
                    rs.getInt("delivery_fee_cents"), rs.getInt("min_order_cents"),
                    rs.getInt("turnaround_days_default")),
                companyId)
            .stream().findFirst().orElse(LavanderiaConfig.DEFAULT);
    }

    /** Upsert da config (insert ou update por company_id). Mantém updated_at. */
    public LavanderiaConfig upsert(UUID companyId, int deliveryFeeCents, int minOrderCents,
                                   int turnaroundDaysDefault) {
        jdbcTemplate.update(
            "insert into lavanderia_config (company_id, delivery_fee_cents, min_order_cents, "
                + "turnaround_days_default) values (?, ?, ?, ?) "
                + "on conflict (company_id) do update set "
                + "delivery_fee_cents = excluded.delivery_fee_cents, "
                + "min_order_cents = excluded.min_order_cents, "
                + "turnaround_days_default = excluded.turnaround_days_default, updated_at = now()",
            companyId, deliveryFeeCents, minOrderCents, turnaroundDaysDefault);
        return findByCompany(companyId);
    }
}
