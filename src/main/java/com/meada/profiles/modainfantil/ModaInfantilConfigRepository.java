package com.meada.profiles.modainfantil;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Acesso a {@code moda_infantil_config} (camada 8.22). 1:1 com company. Ausente →
 * {@link ModaInfantilConfig#ZERO}. Clone de
 * {@link com.meada.profiles.lingerie.LingerieConfigRepository}. Opera via service_role; o
 * escopo por company_id no WHERE é a defesa.
 */
@Repository
public class ModaInfantilConfigRepository {

    private final JdbcTemplate jdbcTemplate;

    public ModaInfantilConfigRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** Config do tenant, ou {@link ModaInfantilConfig#ZERO} se não houver linha. */
    public ModaInfantilConfig findByCompany(UUID companyId) {
        return jdbcTemplate.query(
                "select delivery_fee_cents, min_order_cents from moda_infantil_config where company_id = ?",
                (rs, rn) -> new ModaInfantilConfig(
                    rs.getInt("delivery_fee_cents"), rs.getInt("min_order_cents")),
                companyId)
            .stream().findFirst().orElse(ModaInfantilConfig.ZERO);
    }

    /** Upsert da config (insert ou update por company_id). Mantém updated_at. */
    public ModaInfantilConfig upsert(UUID companyId, int deliveryFeeCents, int minOrderCents) {
        jdbcTemplate.update(
            "insert into moda_infantil_config (company_id, delivery_fee_cents, min_order_cents) "
                + "values (?, ?, ?) "
                + "on conflict (company_id) do update set "
                + "delivery_fee_cents = excluded.delivery_fee_cents, "
                + "min_order_cents = excluded.min_order_cents, updated_at = now()",
            companyId, deliveryFeeCents, minOrderCents);
        return findByCompany(companyId);
    }
}
