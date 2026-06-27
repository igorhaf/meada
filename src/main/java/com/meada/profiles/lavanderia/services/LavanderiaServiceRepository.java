package com.meada.profiles.lavanderia.services;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Acesso a {@code lavanderia_services} (camada 8.10). Clone de
 * {@link com.meada.profiles.floricultura.catalog.FloriculturaCatalogItemRepository} + os campos
 * {@code turnaround_days} e {@code care_instructions} + hidratação das opções por serviço via
 * {@link LavanderiaServiceOptionRepository} (N+1 aceitável — catálogo pequeno). Opera via service_role;
 * o escopo por company_id no WHERE de cada query é a defesa.
 */
@Repository
public class LavanderiaServiceRepository {

    private static final String COLS =
        "id, name, description, price_cents, category, turnaround_days, care_instructions, "
            + "available, created_at, updated_at";

    private final JdbcTemplate jdbcTemplate;
    private final LavanderiaServiceOptionRepository optionRepository;

    public LavanderiaServiceRepository(JdbcTemplate jdbcTemplate,
                                       LavanderiaServiceOptionRepository optionRepository) {
        this.jdbcTemplate = jdbcTemplate;
        this.optionRepository = optionRepository;
    }

    /** Mapeia a row do serviço SEM as opções (hidratadas à parte por {@link #withOptions}). */
    private final RowMapper<LavanderiaService> bareMapper = (rs, rn) -> new LavanderiaService(
        (UUID) rs.getObject("id"),
        rs.getString("name"),
        rs.getString("description"),
        rs.getInt("price_cents"),
        rs.getString("category"),
        rs.getInt("turnaround_days"),
        rs.getString("care_instructions"),
        rs.getBoolean("available"),
        rs.getTimestamp("created_at").toInstant(),
        rs.getTimestamp("updated_at").toInstant(),
        List.of());

    /** Lista serviços do tenant, opcionalmente filtrando por categoria e/ou só disponíveis. */
    public List<LavanderiaService> listByCompany(UUID companyId, String category, boolean onlyAvailable) {
        StringBuilder sql = new StringBuilder(
            "select " + COLS + " from lavanderia_services where company_id = ?");
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
        List<LavanderiaService> bare = jdbcTemplate.query(sql.toString(), bareMapper, args.toArray());
        List<LavanderiaService> withOpts = new ArrayList<>(bare.size());
        for (LavanderiaService it : bare) {
            withOpts.add(withOptions(companyId, it));
        }
        return withOpts;
    }

    public Optional<LavanderiaService> findById(UUID companyId, UUID id) {
        return jdbcTemplate.query(
                "select " + COLS + " from lavanderia_services where company_id = ? and id = ?",
                bareMapper, companyId, id)
            .stream().findFirst()
            .map(it -> withOptions(companyId, it));
    }

    private LavanderiaService withOptions(UUID companyId, LavanderiaService it) {
        List<LavanderiaServiceOption> options = optionRepository.listByService(companyId, it.id());
        return new LavanderiaService(it.id(), it.name(), it.description(), it.priceCents(),
            it.category(), it.turnaroundDays(), it.careInstructions(), it.available(),
            it.createdAt(), it.updatedAt(), options);
    }

    public LavanderiaService insert(UUID companyId, String name, String description, int priceCents,
                                    String category, int turnaroundDays, String careInstructions) {
        UUID id = jdbcTemplate.queryForObject(
            "insert into lavanderia_services (company_id, name, description, price_cents, category, "
                + "turnaround_days, care_instructions) values (?, ?, ?, ?, ?, ?, ?) returning id",
            UUID.class, companyId, name.trim(), description, priceCents, category, turnaroundDays,
            careInstructions);
        return findById(companyId, id).orElseThrow();
    }

    /**
     * Atualiza campos não-null (PATCH parcial). category já validada no service. Retorna o serviço
     * atualizado, ou empty se não existir/pertencer ao tenant.
     */
    public Optional<LavanderiaService> update(UUID companyId, UUID id, String name, String description,
                                              Integer priceCents, String category, Integer turnaroundDays,
                                              String careInstructions, Boolean available) {
        List<String> sets = new ArrayList<>();
        List<Object> args = new ArrayList<>();
        if (name != null && !name.isBlank()) { sets.add("name = ?"); args.add(name.trim()); }
        // description/care_instructions: null = não mexe. Para limpar, o frontend manda string vazia.
        if (description != null) { sets.add("description = ?"); args.add(description); }
        if (priceCents != null) { sets.add("price_cents = ?"); args.add(priceCents); }
        if (category != null && !category.isBlank()) { sets.add("category = ?"); args.add(category); }
        if (turnaroundDays != null) { sets.add("turnaround_days = ?"); args.add(turnaroundDays); }
        if (careInstructions != null) { sets.add("care_instructions = ?"); args.add(careInstructions); }
        if (available != null) { sets.add("available = ?"); args.add(available); }

        if (!sets.isEmpty()) {
            sets.add("updated_at = now()");
            args.add(companyId);
            args.add(id);
            int n = jdbcTemplate.update(
                "update lavanderia_services set " + String.join(", ", sets)
                    + " where company_id = ? and id = ?",
                args.toArray());
            if (n == 0) {
                return Optional.empty();
            }
        }
        return findById(companyId, id);
    }

    /** Atalho dedicado para o toggle de disponibilidade. */
    public Optional<LavanderiaService> toggle(UUID companyId, UUID id, boolean available) {
        int n = jdbcTemplate.update(
            "update lavanderia_services set available = ?, updated_at = now() "
                + "where company_id = ? and id = ?",
            available, companyId, id);
        return n == 0 ? Optional.empty() : findById(companyId, id);
    }

    /** Hard delete. Lança DataIntegrityViolation se houver order_item referenciando (FK restrict). */
    public boolean delete(UUID companyId, UUID id) {
        return jdbcTemplate.update(
            "delete from lavanderia_services where company_id = ? and id = ?", companyId, id) > 0;
    }
}
