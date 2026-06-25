package com.meada.whatsapp.profiles.floricultura;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Acesso a {@code floricultura_config} (camada 8.4). 1:1 com company. Ausente → {@link FloriculturaConfig#ZERO}.
 * Clone de {@link com.meada.whatsapp.profiles.sushi.SushiRestaurantConfigRepository} + upsert no
 * padrão do estetica (AestheticConfigRepository). Opera via service_role; o escopo por company_id
 * no WHERE é a defesa.
 */
@Repository
public class FloriculturaConfigRepository {

    private final JdbcTemplate jdbcTemplate;

    public FloriculturaConfigRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** Config do tenant, ou {@link FloriculturaConfig#ZERO} se não houver linha. */
    public FloriculturaConfig findByCompany(UUID companyId) {
        return jdbcTemplate.query(
                "select delivery_fee_cents, min_order_cents from floricultura_config where company_id = ?",
                (rs, rn) -> new FloriculturaConfig(
                    rs.getInt("delivery_fee_cents"), rs.getInt("min_order_cents")),
                companyId)
            .stream().findFirst().orElse(FloriculturaConfig.ZERO);
    }

    /** Upsert da config (insert ou update por company_id). Mantém updated_at. */
    public FloriculturaConfig upsert(UUID companyId, int deliveryFeeCents, int minOrderCents) {
        jdbcTemplate.update(
            "insert into floricultura_config (company_id, delivery_fee_cents, min_order_cents) "
                + "values (?, ?, ?) "
                + "on conflict (company_id) do update set "
                + "delivery_fee_cents = excluded.delivery_fee_cents, "
                + "min_order_cents = excluded.min_order_cents, updated_at = now()",
            companyId, deliveryFeeCents, minOrderCents);
        return findByCompany(companyId);
    }
}
