package com.meada.profiles.adega;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Acesso a {@code adega_config} (camada 8.4). 1:1 com company. Ausente → {@link AdegaConfig#ZERO}.
 * Clone de {@link com.meada.profiles.sushi.SushiRestaurantConfigRepository} + upsert no
 * padrão do estetica (AestheticConfigRepository). Opera via service_role; o escopo por company_id
 * no WHERE é a defesa.
 */
@Repository
public class AdegaConfigRepository {

    private final JdbcTemplate jdbcTemplate;

    public AdegaConfigRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** Config do tenant, ou {@link AdegaConfig#ZERO} se não houver linha. */
    public AdegaConfig findByCompany(UUID companyId) {
        return jdbcTemplate.query(
                "select delivery_fee_cents, min_order_cents from adega_config where company_id = ?",
                (rs, rn) -> new AdegaConfig(
                    rs.getInt("delivery_fee_cents"), rs.getInt("min_order_cents")),
                companyId)
            .stream().findFirst().orElse(AdegaConfig.ZERO);
    }

    /** Upsert da config (insert ou update por company_id). Mantém updated_at. */
    public AdegaConfig upsert(UUID companyId, int deliveryFeeCents, int minOrderCents) {
        jdbcTemplate.update(
            "insert into adega_config (company_id, delivery_fee_cents, min_order_cents) "
                + "values (?, ?, ?) "
                + "on conflict (company_id) do update set "
                + "delivery_fee_cents = excluded.delivery_fee_cents, "
                + "min_order_cents = excluded.min_order_cents, updated_at = now()",
            companyId, deliveryFeeCents, minOrderCents);
        return findByCompany(companyId);
    }
}
