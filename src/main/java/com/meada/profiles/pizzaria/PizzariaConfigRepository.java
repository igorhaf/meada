package com.meada.profiles.pizzaria;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Acesso a {@code pizzaria_config} (camada 8.4). 1:1 com company. Ausente → {@link PizzariaConfig#ZERO}.
 * Clone de {@link com.meada.profiles.sushi.SushiRestaurantConfigRepository} + upsert no
 * padrão do estetica (AestheticConfigRepository). Opera via service_role; o escopo por company_id
 * no WHERE é a defesa.
 */
@Repository
public class PizzariaConfigRepository {

    private final JdbcTemplate jdbcTemplate;

    public PizzariaConfigRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** Config do tenant, ou {@link PizzariaConfig#ZERO} se não houver linha. */
    public PizzariaConfig findByCompany(UUID companyId) {
        return jdbcTemplate.query(
                "select delivery_fee_cents, min_order_cents from pizzaria_config where company_id = ?",
                (rs, rn) -> new PizzariaConfig(
                    rs.getInt("delivery_fee_cents"), rs.getInt("min_order_cents")),
                companyId)
            .stream().findFirst().orElse(PizzariaConfig.ZERO);
    }

    /** Upsert da config (insert ou update por company_id). Mantém updated_at. */
    public PizzariaConfig upsert(UUID companyId, int deliveryFeeCents, int minOrderCents) {
        jdbcTemplate.update(
            "insert into pizzaria_config (company_id, delivery_fee_cents, min_order_cents) "
                + "values (?, ?, ?) "
                + "on conflict (company_id) do update set "
                + "delivery_fee_cents = excluded.delivery_fee_cents, "
                + "min_order_cents = excluded.min_order_cents, updated_at = now()",
            companyId, deliveryFeeCents, minOrderCents);
        return findByCompany(companyId);
    }
}
