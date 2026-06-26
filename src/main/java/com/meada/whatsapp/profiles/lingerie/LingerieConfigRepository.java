package com.meada.whatsapp.profiles.lingerie;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Acesso a {@code lingerie_config} (camada 8.21). 1:1 com company. Ausente → {@link LingerieConfig#ZERO}.
 * Clone de {@link com.meada.whatsapp.profiles.adega.AdegaConfigRepository}. Opera via service_role; o
 * escopo por company_id no WHERE é a defesa.
 */
@Repository
public class LingerieConfigRepository {

    private final JdbcTemplate jdbcTemplate;

    public LingerieConfigRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** Config do tenant, ou {@link LingerieConfig#ZERO} se não houver linha. */
    public LingerieConfig findByCompany(UUID companyId) {
        return jdbcTemplate.query(
                "select delivery_fee_cents, min_order_cents from lingerie_config where company_id = ?",
                (rs, rn) -> new LingerieConfig(
                    rs.getInt("delivery_fee_cents"), rs.getInt("min_order_cents")),
                companyId)
            .stream().findFirst().orElse(LingerieConfig.ZERO);
    }

    /** Upsert da config (insert ou update por company_id). Mantém updated_at. */
    public LingerieConfig upsert(UUID companyId, int deliveryFeeCents, int minOrderCents) {
        jdbcTemplate.update(
            "insert into lingerie_config (company_id, delivery_fee_cents, min_order_cents) "
                + "values (?, ?, ?) "
                + "on conflict (company_id) do update set "
                + "delivery_fee_cents = excluded.delivery_fee_cents, "
                + "min_order_cents = excluded.min_order_cents, updated_at = now()",
            companyId, deliveryFeeCents, minOrderCents);
        return findByCompany(companyId);
    }
}
