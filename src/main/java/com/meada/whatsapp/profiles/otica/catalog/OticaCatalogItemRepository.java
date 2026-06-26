package com.meada.whatsapp.profiles.otica.catalog;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Acesso a {@code otica_catalog_items} (camada 8.12, FLUXO B). Clone de
 * {@link com.meada.whatsapp.profiles.floricultura.catalog.FloriculturaCatalogItemRepository} +
 * {@code made_to_order} / {@code lead_time_days} (a ESCAPADA da ótica) + hidratação das opções por
 * item via {@link OticaCatalogOptionRepository} (N+1 aceitável — catálogo é pequeno). Opera via
 * service_role; o escopo por company_id no WHERE é a defesa.
 */
@Repository
public class OticaCatalogItemRepository {

    private static final String COLS =
        "id, name, description, price_cents, category, made_to_order, lead_time_days, available, "
            + "created_at, updated_at";

    private final JdbcTemplate jdbcTemplate;
    private final OticaCatalogOptionRepository optionRepository;

    public OticaCatalogItemRepository(JdbcTemplate jdbcTemplate,
                                      OticaCatalogOptionRepository optionRepository) {
        this.jdbcTemplate = jdbcTemplate;
        this.optionRepository = optionRepository;
    }

    /** Mapeia a row do item SEM as opções (hidratadas à parte por {@link #withOptions}). */
    private final RowMapper<OticaCatalogItem> bareMapper = (rs, rn) -> new OticaCatalogItem(
        (UUID) rs.getObject("id"),
        rs.getString("name"),
        rs.getString("description"),
        rs.getInt("price_cents"),
        rs.getString("category"),
        rs.getBoolean("made_to_order"),
        rs.getObject("lead_time_days") == null ? null : rs.getInt("lead_time_days"),
        rs.getBoolean("available"),
        rs.getTimestamp("created_at").toInstant(),
        rs.getTimestamp("updated_at").toInstant(),
        List.of());

    /** Lista itens do tenant, opcionalmente filtrando por categoria e/ou só disponíveis. */
    public List<OticaCatalogItem> listByCompany(UUID companyId, String category, boolean onlyAvailable) {
        StringBuilder sql = new StringBuilder(
            "select " + COLS + " from otica_catalog_items where company_id = ?");
        List<Object> args = new ArrayList<>();
        args.add(companyId);
        if (category != null && !category.isBlank()) {
            sql.append(" and category = ?");
            args.add(category);
        }
        if (onlyAvailable) {
            sql.append(" and available = true");
        }
        sql.append(" order by category asc, name asc");
        List<OticaCatalogItem> bare = jdbcTemplate.query(sql.toString(), bareMapper, args.toArray());
        List<OticaCatalogItem> withOpts = new ArrayList<>(bare.size());
        for (OticaCatalogItem it : bare) {
            withOpts.add(withOptions(companyId, it));
        }
        return withOpts;
    }

    public Optional<OticaCatalogItem> findById(UUID companyId, UUID id) {
        return jdbcTemplate.query(
                "select " + COLS + " from otica_catalog_items where company_id = ? and id = ?",
                bareMapper, companyId, id)
            .stream().findFirst()
            .map(it -> withOptions(companyId, it));
    }

    private OticaCatalogItem withOptions(UUID companyId, OticaCatalogItem it) {
        List<OticaCatalogOption> options = optionRepository.listByItem(companyId, it.id());
        return new OticaCatalogItem(it.id(), it.name(), it.description(), it.priceCents(),
            it.category(), it.madeToOrder(), it.leadTimeDays(), it.available(),
            it.createdAt(), it.updatedAt(), options);
    }

    public OticaCatalogItem insert(UUID companyId, String name, String description, int priceCents,
                                   String category, boolean madeToOrder, Integer leadTimeDays) {
        UUID id = jdbcTemplate.queryForObject(
            "insert into otica_catalog_items (company_id, name, description, price_cents, category, "
                + "made_to_order, lead_time_days) values (?, ?, ?, ?, ?, ?, ?) returning id",
            UUID.class, companyId, name.trim(), description, priceCents, category, madeToOrder, leadTimeDays);
        return findById(companyId, id).orElseThrow();
    }

    /**
     * Atualiza campos não-null (PATCH parcial). category já validada no service. {@code leadTimeDays}
     * tem semântica de três estados: null = não mexe; o controller usa {@code leadTimeDaysProvided}
     * pra distinguir "limpar" (set null) de "não enviado". Retorna o item atualizado, ou empty.
     */
    public Optional<OticaCatalogItem> update(UUID companyId, UUID id, String name, String description,
                                             Integer priceCents, String category, Boolean madeToOrder,
                                             Integer leadTimeDays, boolean leadTimeDaysProvided,
                                             Boolean available) {
        List<String> sets = new ArrayList<>();
        List<Object> args = new ArrayList<>();
        if (name != null && !name.isBlank()) { sets.add("name = ?"); args.add(name.trim()); }
        if (description != null) { sets.add("description = ?"); args.add(description); }
        if (priceCents != null) { sets.add("price_cents = ?"); args.add(priceCents); }
        if (category != null && !category.isBlank()) { sets.add("category = ?"); args.add(category); }
        if (madeToOrder != null) { sets.add("made_to_order = ?"); args.add(madeToOrder); }
        if (leadTimeDaysProvided) { sets.add("lead_time_days = ?"); args.add(leadTimeDays); }
        if (available != null) { sets.add("available = ?"); args.add(available); }

        if (!sets.isEmpty()) {
            sets.add("updated_at = now()");
            args.add(companyId);
            args.add(id);
            int n = jdbcTemplate.update(
                "update otica_catalog_items set " + String.join(", ", sets)
                    + " where company_id = ? and id = ?",
                args.toArray());
            if (n == 0) {
                return Optional.empty();
            }
        }
        return findById(companyId, id);
    }

    /** Atalho dedicado para o toggle de disponibilidade. */
    public Optional<OticaCatalogItem> toggle(UUID companyId, UUID id, boolean available) {
        int n = jdbcTemplate.update(
            "update otica_catalog_items set available = ?, updated_at = now() "
                + "where company_id = ? and id = ?",
            available, companyId, id);
        return n == 0 ? Optional.empty() : findById(companyId, id);
    }

    /** Hard delete. Lança DataIntegrityViolation se houver order_item referenciando (FK restrict). */
    public boolean delete(UUID companyId, UUID id) {
        return jdbcTemplate.update(
            "delete from otica_catalog_items where company_id = ? and id = ?", companyId, id) > 0;
    }
}
