package com.meada.whatsapp.profiles.lavanderia.services;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Acesso a {@code lavanderia_service_options} (camada 8.10). Opera via service_role; o escopo por
 * company_id (e por service_id) no WHERE de cada query é a defesa. Clone de
 * {@link com.meada.whatsapp.profiles.floricultura.catalog.FloriculturaCatalogOptionRepository} (coluna
 * {@code service_id} no lugar de {@code catalog_item_id}).
 */
@Repository
public class LavanderiaServiceOptionRepository {

    private static final RowMapper<LavanderiaServiceOption> MAPPER = (rs, rn) -> new LavanderiaServiceOption(
        (UUID) rs.getObject("id"),
        (UUID) rs.getObject("service_id"),
        rs.getString("group_label"),
        rs.getString("option_label"),
        rs.getInt("price_delta_cents"),
        rs.getBoolean("available"),
        rs.getInt("sort_order"),
        rs.getTimestamp("created_at").toInstant(),
        rs.getTimestamp("updated_at").toInstant());

    private static final String COLS =
        "id, service_id, group_label, option_label, price_delta_cents, available, sort_order, "
            + "created_at, updated_at";

    private final JdbcTemplate jdbcTemplate;

    public LavanderiaServiceOptionRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** Todas as opções de um serviço (qualquer disponibilidade), ordenadas por sort_order. */
    public List<LavanderiaServiceOption> listByService(UUID companyId, UUID serviceId) {
        return jdbcTemplate.query(
            "select " + COLS + " from lavanderia_service_options "
                + "where company_id = ? and service_id = ? order by sort_order asc, created_at asc",
            MAPPER, companyId, serviceId);
    }

    public Optional<LavanderiaServiceOption> findById(UUID companyId, UUID serviceId, UUID id) {
        return jdbcTemplate.query(
                "select " + COLS + " from lavanderia_service_options "
                    + "where company_id = ? and service_id = ? and id = ?",
                MAPPER, companyId, serviceId, id)
            .stream().findFirst();
    }

    /**
     * Resolve as opções (a) do {@code serviceId}, (b) do {@code companyId}, (c) available=true,
     * cujo id está em {@code optionIds}. Usado no recálculo do pedido — se o tamanho do resultado
     * difere do tamanho de optionIds, alguma opção é inválida/indisponível/de outro serviço.
     */
    public List<LavanderiaServiceOption> findByIdsForService(UUID companyId, UUID serviceId,
                                                             Collection<UUID> optionIds) {
        if (optionIds == null || optionIds.isEmpty()) {
            return List.of();
        }
        String placeholders = optionIds.stream().map(o -> "?").collect(Collectors.joining(", "));
        List<Object> args = new ArrayList<>();
        args.add(companyId);
        args.add(serviceId);
        args.addAll(optionIds);
        return jdbcTemplate.query(
            "select " + COLS + " from lavanderia_service_options "
                + "where company_id = ? and service_id = ? and available = true "
                + "and id in (" + placeholders + ")",
            MAPPER, args.toArray());
    }

    public LavanderiaServiceOption insert(UUID companyId, UUID serviceId, String groupLabel,
                                          String optionLabel, int priceDeltaCents, int sortOrder) {
        UUID id = jdbcTemplate.queryForObject(
            "insert into lavanderia_service_options (company_id, service_id, group_label, "
                + "option_label, price_delta_cents, sort_order) values (?, ?, ?, ?, ?, ?) returning id",
            UUID.class, companyId, serviceId, groupLabel.trim(), optionLabel.trim(),
            priceDeltaCents, sortOrder);
        return findById(companyId, serviceId, id).orElseThrow();
    }

    /** Atualiza campos não-null (PATCH parcial). Empty se não existir/pertencer ao serviço+tenant. */
    public Optional<LavanderiaServiceOption> update(UUID companyId, UUID serviceId, UUID id,
                                                    String groupLabel, String optionLabel,
                                                    Integer priceDeltaCents, Integer sortOrder,
                                                    Boolean available) {
        List<String> sets = new ArrayList<>();
        List<Object> args = new ArrayList<>();
        if (groupLabel != null && !groupLabel.isBlank()) { sets.add("group_label = ?"); args.add(groupLabel.trim()); }
        if (optionLabel != null && !optionLabel.isBlank()) { sets.add("option_label = ?"); args.add(optionLabel.trim()); }
        if (priceDeltaCents != null) { sets.add("price_delta_cents = ?"); args.add(priceDeltaCents); }
        if (sortOrder != null) { sets.add("sort_order = ?"); args.add(sortOrder); }
        if (available != null) { sets.add("available = ?"); args.add(available); }

        if (!sets.isEmpty()) {
            sets.add("updated_at = now()");
            args.add(companyId);
            args.add(serviceId);
            args.add(id);
            int n = jdbcTemplate.update(
                "update lavanderia_service_options set " + String.join(", ", sets)
                    + " where company_id = ? and service_id = ? and id = ?",
                args.toArray());
            if (n == 0) {
                return Optional.empty();
            }
        }
        return findById(companyId, serviceId, id);
    }

    /** Atalho dedicado para o toggle de disponibilidade. */
    public Optional<LavanderiaServiceOption> toggle(UUID companyId, UUID serviceId, UUID id, boolean available) {
        int n = jdbcTemplate.update(
            "update lavanderia_service_options set available = ?, updated_at = now() "
                + "where company_id = ? and service_id = ? and id = ?",
            available, companyId, serviceId, id);
        return n == 0 ? Optional.empty() : findById(companyId, serviceId, id);
    }

    /** Hard delete da opção. Snapshots em lavanderia_order_item_options são preservados (set null). */
    public boolean delete(UUID companyId, UUID serviceId, UUID id) {
        return jdbcTemplate.update(
            "delete from lavanderia_service_options where company_id = ? and service_id = ? and id = ?",
            companyId, serviceId, id) > 0;
    }
}
