package com.meada.profiles.papelaria.catalog;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Acesso a {@code papelaria_catalog_items} (camada 8.15 / perfil papelaria). Clone de
 * {@code com.meada.profiles.padaria.menu.PadariaMenuItemRepository} (camada 8.8) —
 * menu→catalog (a tabela é {@code papelaria_catalog_items}; a coluna FK das opções é
 * {@code catalog_item_id}) — com as colunas da ESCAPADA ({@code made_to_order}, {@code lead_time_days})
 * e {@code specs} (no lugar de {@code allergens}) + hidratação das opções por item via
 * {@link PapelariaCatalogOptionRepository} (N+1 aceitável — catálogo é pequeno). Opera via
 * service_role; o escopo por company_id no WHERE de cada query é a defesa.
 */
@Repository
public class PapelariaCatalogItemRepository {

    private static final String COLS =
        "id, name, description, price_cents, category, made_to_order, lead_time_days, specs, "
            + "available, created_at, updated_at";

    private final JdbcTemplate jdbcTemplate;
    private final PapelariaCatalogOptionRepository optionRepository;

    public PapelariaCatalogItemRepository(JdbcTemplate jdbcTemplate,
                                          PapelariaCatalogOptionRepository optionRepository) {
        this.jdbcTemplate = jdbcTemplate;
        this.optionRepository = optionRepository;
    }

    /** Mapeia a row do item SEM as opções (hidratadas à parte por {@link #withOptions}). */
    private final RowMapper<PapelariaCatalogItem> bareMapper = (rs, rn) -> {
        Object leadObj = rs.getObject("lead_time_days");
        Integer lead = leadObj == null ? null : ((Number) leadObj).intValue();
        return new PapelariaCatalogItem(
            (UUID) rs.getObject("id"),
            rs.getString("name"),
            rs.getString("description"),
            rs.getInt("price_cents"),
            rs.getString("category"),
            rs.getBoolean("made_to_order"),
            lead,
            rs.getString("specs"),
            rs.getBoolean("available"),
            rs.getTimestamp("created_at").toInstant(),
            rs.getTimestamp("updated_at").toInstant(),
            List.of());
    };

    /** Lista itens do tenant, opcionalmente filtrando por categoria e/ou só disponíveis. */
    public List<PapelariaCatalogItem> listByCompany(UUID companyId, String category, boolean onlyAvailable) {
        StringBuilder sql = new StringBuilder(
            "select " + COLS + " from papelaria_catalog_items where company_id = ?");
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
        List<PapelariaCatalogItem> bare = jdbcTemplate.query(sql.toString(), bareMapper, args.toArray());
        List<PapelariaCatalogItem> withOpts = new ArrayList<>(bare.size());
        for (PapelariaCatalogItem it : bare) {
            withOpts.add(withOptions(companyId, it));
        }
        return withOpts;
    }

    public Optional<PapelariaCatalogItem> findById(UUID companyId, UUID id) {
        return jdbcTemplate.query(
                "select " + COLS + " from papelaria_catalog_items where company_id = ? and id = ?",
                bareMapper, companyId, id)
            .stream().findFirst()
            .map(it -> withOptions(companyId, it));
    }

    private PapelariaCatalogItem withOptions(UUID companyId, PapelariaCatalogItem it) {
        List<PapelariaCatalogOption> options = optionRepository.listByItem(companyId, it.id());
        return new PapelariaCatalogItem(it.id(), it.name(), it.description(), it.priceCents(),
            it.category(), it.madeToOrder(), it.leadTimeDays(), it.specs(), it.available(),
            it.createdAt(), it.updatedAt(), options);
    }

    public PapelariaCatalogItem insert(UUID companyId, String name, String description, int priceCents,
                                       String category, boolean madeToOrder, Integer leadTimeDays,
                                       String specs) {
        UUID id = jdbcTemplate.queryForObject(
            "insert into papelaria_catalog_items (company_id, name, description, price_cents, category, "
                + "made_to_order, lead_time_days, specs) values (?, ?, ?, ?, ?, ?, ?, ?) returning id",
            UUID.class, companyId, name.trim(), description, priceCents, category,
            madeToOrder, leadTimeDays, specs);
        return findById(companyId, id).orElseThrow();
    }

    /**
     * Atualiza campos não-null (PATCH parcial). category já validada no service. Retorna o item
     * atualizado, ou empty se não existir/pertencer ao tenant. {@code clearLeadTime} permite ZERAR o
     * lead_time_days (voltar ao default da config) — distingue "não mexe" (false) de "limpa" (true).
     */
    public Optional<PapelariaCatalogItem> update(UUID companyId, UUID id, String name, String description,
                                                 Integer priceCents, String category, Boolean madeToOrder,
                                                 Integer leadTimeDays, boolean clearLeadTime,
                                                 String specs, Boolean available) {
        List<String> sets = new ArrayList<>();
        List<Object> args = new ArrayList<>();
        if (name != null && !name.isBlank()) { sets.add("name = ?"); args.add(name.trim()); }
        // description: null = não mexe. Para limpar, o frontend manda string vazia.
        if (description != null) { sets.add("description = ?"); args.add(description); }
        if (priceCents != null) { sets.add("price_cents = ?"); args.add(priceCents); }
        if (category != null && !category.isBlank()) { sets.add("category = ?"); args.add(category); }
        if (madeToOrder != null) { sets.add("made_to_order = ?"); args.add(madeToOrder); }
        if (clearLeadTime) { sets.add("lead_time_days = null"); }
        else if (leadTimeDays != null) { sets.add("lead_time_days = ?"); args.add(leadTimeDays); }
        if (specs != null) { sets.add("specs = ?"); args.add(specs); }
        if (available != null) { sets.add("available = ?"); args.add(available); }

        if (!sets.isEmpty()) {
            sets.add("updated_at = now()");
            args.add(companyId);
            args.add(id);
            int n = jdbcTemplate.update(
                "update papelaria_catalog_items set " + String.join(", ", sets)
                    + " where company_id = ? and id = ?",
                args.toArray());
            if (n == 0) {
                return Optional.empty();
            }
        }
        return findById(companyId, id);
    }

    /** Atalho dedicado para o toggle de disponibilidade. */
    public Optional<PapelariaCatalogItem> toggle(UUID companyId, UUID id, boolean available) {
        int n = jdbcTemplate.update(
            "update papelaria_catalog_items set available = ?, updated_at = now() "
                + "where company_id = ? and id = ?",
            available, companyId, id);
        return n == 0 ? Optional.empty() : findById(companyId, id);
    }

    /** Hard delete. Lança DataIntegrityViolation se houver order_item referenciando (FK restrict). */
    public boolean delete(UUID companyId, UUID id) {
        return jdbcTemplate.update(
            "delete from papelaria_catalog_items where company_id = ? and id = ?", companyId, id) > 0;
    }
}
