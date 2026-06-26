package com.meada.whatsapp.profiles.cursos.modules;

import com.meada.whatsapp.common.audit.AuditLogger;
import com.meada.whatsapp.profiles.cursos.CursosContextCache;
import com.meada.whatsapp.profiles.cursos.courses.CursosCourseRepository;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Regras dos módulos do tenant cursos (camada 8.20 / perfil cursos, ESCAPADA 1). CRUD da trilha
 * ordenada de um curso. Valida que o curso existe (do mesmo tenant), mapeia a colisão de position
 * (UNIQUE course_id, position) para 409 duplicate_position, audita e invalida o
 * {@link CursosContextCache}. Análogo ao AcademiaClassService (camada 7.7).
 */
@Service
public class CursosModuleService {

    private final CursosModuleRepository repository;
    private final CursosCourseRepository courseRepository;
    private final AuditLogger auditLogger;
    private final CursosContextCache contextCache;

    public CursosModuleService(CursosModuleRepository repository, CursosCourseRepository courseRepository,
                               AuditLogger auditLogger, CursosContextCache contextCache) {
        this.repository = repository;
        this.courseRepository = courseRepository;
        this.auditLogger = auditLogger;
        this.contextCache = contextCache;
    }

    /** Módulo não encontrado / de outro tenant (→ 404). */
    public static class ModuleNotFoundException extends RuntimeException {}

    /** Curso não encontrado / de outro tenant (→ 404). */
    public static class CourseNotFoundException extends RuntimeException {}

    /** position já usado nesse curso (UNIQUE course_id, position) (→ 409 duplicate_position). */
    public static class DuplicatePositionException extends RuntimeException {}

    public List<CursosModule> listByCourse(UUID companyId, UUID courseId) {
        if (courseRepository.findById(companyId, courseId).isEmpty()) {
            throw new CourseNotFoundException();
        }
        return repository.listByCourse(companyId, courseId);
    }

    @Transactional
    public CursosModule create(UUID companyId, UUID userId, UUID courseId, int position, String title, String content) {
        if (courseRepository.findById(companyId, courseId).isEmpty()) {
            throw new CourseNotFoundException();
        }
        CursosModule created;
        try {
            created = repository.insert(companyId, courseId, position, title, content);
        } catch (DuplicateKeyException e) {
            throw new DuplicatePositionException();
        }
        auditLogger.log(companyId, userId, "cursos_module_created", "cursos_module",
            created.id(), Map.of("title", created.title(), "position", position));
        contextCache.invalidate(companyId);
        return created;
    }

    @Transactional
    public CursosModule update(UUID companyId, UUID userId, UUID id, Integer position, String title, String content) {
        CursosModule updated;
        try {
            updated = repository.update(companyId, id, position, title, content)
                .orElseThrow(ModuleNotFoundException::new);
        } catch (DuplicateKeyException e) {
            throw new DuplicatePositionException();
        }
        auditLogger.log(companyId, userId, "cursos_module_updated", "cursos_module", id, Map.of());
        contextCache.invalidate(companyId);
        return updated;
    }

    @Transactional
    public void delete(UUID companyId, UUID userId, UUID id) {
        if (!repository.delete(companyId, id)) {
            throw new ModuleNotFoundException();
        }
        auditLogger.log(companyId, userId, "cursos_module_deleted", "cursos_module", id, Map.of());
        contextCache.invalidate(companyId);
    }

    /**
     * Reordena a trilha do curso conforme a lista de ids (índice vira position 0..N). Valida que o
     * curso é do tenant; o reorder é transacional em duas fases (ver repositório). Devolve a lista
     * já reordenada. Invalida o cache de contexto.
     */
    @Transactional
    public List<CursosModule> reorder(UUID companyId, UUID userId, UUID courseId, List<UUID> orderedIds) {
        if (courseRepository.findById(companyId, courseId).isEmpty()) {
            throw new CourseNotFoundException();
        }
        repository.reorder(companyId, courseId, orderedIds);
        auditLogger.log(companyId, userId, "cursos_modules_reordered", "cursos_course", courseId,
            Map.of("count", orderedIds.size()));
        contextCache.invalidate(companyId);
        return repository.listByCourse(companyId, courseId);
    }
}
