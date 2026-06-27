package com.meada.profiles.cursos.courses;

import com.meada.common.audit.AuditLogger;
import com.meada.profiles.cursos.CursosContextCache;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Regras dos cursos do tenant cursos (camada 8.20 / perfil cursos). Audita mutações e invalida o
 * {@link CursosContextCache}. Clone do AcademiaPlanService (camada 7.7).
 */
@Service
public class CursosCourseService {

    private final CursosCourseRepository repository;
    private final AuditLogger auditLogger;
    private final CursosContextCache contextCache;

    public CursosCourseService(CursosCourseRepository repository, AuditLogger auditLogger,
                               CursosContextCache contextCache) {
        this.repository = repository;
        this.auditLogger = auditLogger;
        this.contextCache = contextCache;
    }

    /** Curso não encontrado / de outro tenant (→ 404). */
    public static class CourseNotFoundException extends RuntimeException {}

    /** Curso referenciado por matrícula (FK restrict) — não pode hard-deletar (→ 409 course_in_use). */
    public static class CourseInUseException extends RuntimeException {}

    @Transactional
    public CursosCourse create(UUID companyId, UUID userId, String title, String category,
                               int monthlyCents, String description) {
        CursosCourse created = repository.insert(companyId, title, category, monthlyCents, description);
        auditLogger.log(companyId, userId, "cursos_course_created", "cursos_course",
            created.id(), Map.of("title", created.title()));
        contextCache.invalidate(companyId);
        return created;
    }

    @Transactional
    public CursosCourse update(UUID companyId, UUID userId, UUID id, String title, String category,
                               Integer monthlyCents, String description, Boolean active) {
        CursosCourse updated = repository.update(companyId, id, title, category, monthlyCents, description, active)
            .orElseThrow(CourseNotFoundException::new);
        auditLogger.log(companyId, userId, "cursos_course_updated", "cursos_course", id, Map.of());
        contextCache.invalidate(companyId);
        return updated;
    }

    @Transactional
    public CursosCourse toggle(UUID companyId, UUID userId, UUID id, boolean active) {
        CursosCourse c = repository.toggle(companyId, id, active).orElseThrow(CourseNotFoundException::new);
        auditLogger.log(companyId, userId, "cursos_course_updated", "cursos_course", id, Map.of("active", active));
        contextCache.invalidate(companyId);
        return c;
    }

    @Transactional
    public void delete(UUID companyId, UUID userId, UUID id) {
        try {
            if (!repository.delete(companyId, id)) {
                throw new CourseNotFoundException();
            }
        } catch (DataIntegrityViolationException e) {
            throw new CourseInUseException();
        }
        auditLogger.log(companyId, userId, "cursos_course_deleted", "cursos_course", id, Map.of());
        contextCache.invalidate(companyId);
    }

    public List<CursosCourse> list(UUID companyId, boolean onlyActive) {
        return repository.listByCompany(companyId, onlyActive);
    }

    public Optional<CursosCourse> get(UUID companyId, UUID id) {
        return repository.findById(companyId, id);
    }
}
