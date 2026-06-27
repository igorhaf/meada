package com.meada.profiles.floricultura.catalog;

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
 * Acesso a {@code floricultura_catalog_item_options} (camada 8.4, ESCAPADA 2). Opera via service_role; o
 * escopo por company_id (e por catalog_item_id) no WHERE de cada query é a defesa. Sem paralelo no
 * sushi — é a sub-entidade nova (modifiers).
 */
@Repository
public class FloriculturaCatalogOptionRepository {

    private static final RowMapper<FloriculturaCatalogOption> MAPPER = (rs, rn) -> new FloriculturaCatalogOption(
        (UUID) rs.getObject("id"),
        (UUID) rs.getObject("catalog_item_id"),
        rs.getString("group_label"),
        rs.getString("option_label"),
        rs.getInt("price_delta_cents"),
        rs.getBoolean("available"),
        rs.getInt("sort_order"),
        rs.getTimestamp("created_at").toInstant(),
        rs.getTimestamp("updated_at").toInstant());

    private static final String COLS =
        "id, catalog_item_id, group_label, option_label, price_delta_cents, available, sort_order, "
            + "created_at, updated_at";

    private final JdbcTemplate jdbcTemplate;

    public FloriculturaCatalogOptionRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** Todas as opções de um item (qualquer disponibilidade), ordenadas por sort_order. */
    public List<FloriculturaCatalogOption> listByItem(UUID companyId, UUID catalogItemId) {
        return jdbcTemplate.query(
            "select " + COLS + " from floricultura_catalog_item_options "
                + "where company_id = ? and catalog_item_id = ? order by sort_order asc, created_at asc",
            MAPPER, companyId, catalogItemId);
    }

    public Optional<FloriculturaCatalogOption> findById(UUID companyId, UUID catalogItemId, UUID id) {
        return jdbcTemplate.query(
                "select " + COLS + " from floricultura_catalog_item_options "
                    + "where company_id = ? and catalog_item_id = ? and id = ?",
                MAPPER, companyId, catalogItemId, id)
            .stream().findFirst();
    }

    /**
     * Resolve as opções (a) do {@code catalogItemId}, (b) do {@code companyId}, (c) available=true,
     * cujo id está em {@code optionIds}. Usado no recálculo do pedido — se o tamanho do resultado
     * difere do tamanho de optionIds, alguma opção é inválida/indisponível/de outro item.
     * Optional vazio de entrada (sem opções pedidas) → lista vazia.
     */
    public List<FloriculturaCatalogOption> findByIdsForItem(UUID companyId, UUID catalogItemId,
                                                   Collection<UUID> optionIds) {
        if (optionIds == null || optionIds.isEmpty()) {
            return List.of();
        }
        String placeholders = optionIds.stream().map(o -> "?").collect(Collectors.joining(", "));
        List<Object> args = new ArrayList<>();
        args.add(companyId);
        args.add(catalogItemId);
        args.addAll(optionIds);
        return jdbcTemplate.query(
            "select " + COLS + " from floricultura_catalog_item_options "
                + "where company_id = ? and catalog_item_id = ? and available = true "
                + "and id in (" + placeholders + ")",
            MAPPER, args.toArray());
    }

    public FloriculturaCatalogOption insert(UUID companyId, UUID catalogItemId, String groupLabel,
                                   String optionLabel, int priceDeltaCents, int sortOrder) {
        UUID id = jdbcTemplate.queryForObject(
            "insert into floricultura_catalog_item_options (company_id, catalog_item_id, group_label, "
                + "option_label, price_delta_cents, sort_order) values (?, ?, ?, ?, ?, ?) returning id",
            UUID.class, companyId, catalogItemId, groupLabel.trim(), optionLabel.trim(),
            priceDeltaCents, sortOrder);
        return findById(companyId, catalogItemId, id).orElseThrow();
    }

    /**
     * Atualiza campos não-null (PATCH parcial). Retorna a opção atualizada, ou empty se não
     * existir/pertencer ao item+tenant.
     */
    public Optional<FloriculturaCatalogOption> update(UUID companyId, UUID catalogItemId, UUID id,
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
            args.add(catalogItemId);
            args.add(id);
            int n = jdbcTemplate.update(
                "update floricultura_catalog_item_options set " + String.join(", ", sets)
                    + " where company_id = ? and catalog_item_id = ? and id = ?",
                args.toArray());
            if (n == 0) {
                return Optional.empty();
            }
        }
        return findById(companyId, catalogItemId, id);
    }

    /** Atalho dedicado para o toggle de disponibilidade. */
    public Optional<FloriculturaCatalogOption> toggle(UUID companyId, UUID catalogItemId, UUID id, boolean available) {
        int n = jdbcTemplate.update(
            "update floricultura_catalog_item_options set available = ?, updated_at = now() "
                + "where company_id = ? and catalog_item_id = ? and id = ?",
            available, companyId, catalogItemId, id);
        return n == 0 ? Optional.empty() : findById(companyId, catalogItemId, id);
    }

    /** Hard delete da opção. Snapshots em floricultura_order_item_options são preservados (set null). */
    public boolean delete(UUID companyId, UUID catalogItemId, UUID id) {
        return jdbcTemplate.update(
            "delete from floricultura_catalog_item_options where company_id = ? and catalog_item_id = ? and id = ?",
            companyId, catalogItemId, id) > 0;
    }
}
