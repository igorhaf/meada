package com.meada.profiles.cursos.modules;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Acesso a {@code cursos_modules} (camada 8.20 / perfil cursos, ESCAPADA 1). Opera via service_role;
 * escopo por company_id. Análogo ao AcademiaClassRepository (camada 7.7), mas a ordem é {@code
 * position} (0..N) em vez de dia/hora. UNIQUE(course_id, position) → a colisão bubbles como
 * DataIntegrityViolation (o service mapeia para 409 duplicate_position).
 */
@Repository
public class CursosModuleRepository {

    private static final RowMapper<CursosModule> MAPPER = (rs, rn) -> new CursosModule(
        (UUID) rs.getObject("id"),
        (UUID) rs.getObject("course_id"),
        rs.getInt("position"),
        rs.getString("title"),
        rs.getString("content"),
        rs.getTimestamp("created_at").toInstant(),
        rs.getTimestamp("updated_at").toInstant());

    private static final String COLS = "id, course_id, position, title, content, created_at, updated_at";

    private final JdbcTemplate jdbcTemplate;

    public CursosModuleRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** Lista os módulos de um curso, ordenados por position ASC (a trilha). */
    public List<CursosModule> listByCourse(UUID companyId, UUID courseId) {
        return jdbcTemplate.query("select " + COLS + " from cursos_modules "
                + "where company_id = ? and course_id = ? order by position asc",
            MAPPER, companyId, courseId);
    }

    public Optional<CursosModule> findById(UUID companyId, UUID id) {
        return jdbcTemplate.query("select " + COLS + " from cursos_modules where company_id = ? and id = ?",
                MAPPER, companyId, id)
            .stream().findFirst();
    }

    public CursosModule insert(UUID companyId, UUID courseId, int position, String title, String content) {
        UUID id = jdbcTemplate.queryForObject(
            "insert into cursos_modules (company_id, course_id, position, title, content) "
                + "values (?, ?, ?, ?, ?) returning id",
            UUID.class, companyId, courseId, position, title.trim(), content);
        return findById(companyId, id).orElseThrow();
    }

    public Optional<CursosModule> update(UUID companyId, UUID id, Integer position, String title, String content) {
        List<String> sets = new ArrayList<>();
        List<Object> args = new ArrayList<>();
        if (position != null) { sets.add("position = ?"); args.add(position); }
        if (title != null && !title.isBlank()) { sets.add("title = ?"); args.add(title.trim()); }
        if (content != null) { sets.add("content = ?"); args.add(content); }
        if (!sets.isEmpty()) {
            sets.add("updated_at = now()");
            args.add(companyId);
            args.add(id);
            int n = jdbcTemplate.update("update cursos_modules set " + String.join(", ", sets)
                + " where company_id = ? and id = ?", args.toArray());
            if (n == 0) {
                return Optional.empty();
            }
        }
        return findById(companyId, id);
    }

    public boolean delete(UUID companyId, UUID id) {
        return jdbcTemplate.update("delete from cursos_modules where company_id = ? and id = ?", companyId, id) > 0;
    }

    /**
     * Reordena os módulos do curso conforme a ordem da lista (índice 0..N vira position 0..N).
     * DUAS FASES para não bater no UNIQUE (course_id, position) durante o swap: primeiro joga as
     * positions para um espaço negativo (1000 + idx) * -1, depois grava as positions finais. Só
     * mexe nos módulos do próprio company/curso. Retorna quantos módulos foram afetados.
     */
    public int reorder(UUID companyId, UUID courseId, List<UUID> orderedIds) {
        int affected = 0;
        // Fase 1: posições temporárias negativas (sem colisão entre si nem com as finais 0..N).
        for (int i = 0; i < orderedIds.size(); i++) {
            affected += jdbcTemplate.update(
                "update cursos_modules set position = ? where company_id = ? and course_id = ? and id = ?",
                -(1000 + i), companyId, courseId, orderedIds.get(i));
        }
        // Fase 2: posições finais 0..N.
        for (int i = 0; i < orderedIds.size(); i++) {
            jdbcTemplate.update(
                "update cursos_modules set position = ?, updated_at = now() "
                    + "where company_id = ? and course_id = ? and id = ?",
                i, companyId, courseId, orderedIds.get(i));
        }
        return affected;
    }
}
