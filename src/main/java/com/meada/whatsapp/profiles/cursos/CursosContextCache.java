package com.meada.whatsapp.profiles.cursos;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.meada.whatsapp.profiles.cursos.courses.CursosCourse;
import com.meada.whatsapp.profiles.cursos.courses.CursosCourseRepository;
import com.meada.whatsapp.profiles.cursos.enrollments.CursosEnrollment;
import com.meada.whatsapp.profiles.cursos.enrollments.CursosEnrollmentRepository;
import com.meada.whatsapp.profiles.cursos.modules.CursosModuleRepository;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * Cache do bloco de contexto dinâmico injetado no prompt do CursosBot (camada 8.20 / perfil cursos).
 * Clone do AcademiaContextCache (camada 7.7).
 *
 * <p>TTL 60s (cursos mudam pouco). Keyed por {@code (companyId, contactId)}. Conteúdo: cursos ativos
 * (título + mensalidade + categoria + nº de módulos), as matrículas do contato (curso + status +
 * progresso "X/N módulos" + título do próximo módulo), persona + instruções + as 2 tags. Anti-dupla
 * hint (as matrículas ativas do contato). Os services chamam {@link #invalidate} ao mutar.
 */
@Component
public class CursosContextCache {

    private final CursosCourseRepository courseRepository;
    private final CursosModuleRepository moduleRepository;
    private final CursosEnrollmentRepository enrollmentRepository;
    private final Cache<String, String> cache;

    public CursosContextCache(CursosCourseRepository courseRepository,
                              CursosModuleRepository moduleRepository,
                              CursosEnrollmentRepository enrollmentRepository) {
        this.courseRepository = courseRepository;
        this.moduleRepository = moduleRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.cache = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofSeconds(60))
            .maximumSize(1000)
            .build();
    }

    public String contextSegment(UUID companyId, UUID contactId) {
        String key = companyId + ":" + (contactId == null ? "none" : contactId.toString());
        return cache.get(key, k -> buildSegment(companyId, contactId));
    }

    /** Invalida todas as entradas de uma empresa (mutação de curso/módulo/matrícula/config). */
    public void invalidate(UUID companyId) {
        String prefix = companyId + ":";
        cache.asMap().keySet().removeIf(k -> k.startsWith(prefix));
    }

    private String buildSegment(UUID companyId, UUID contactId) {
        List<CursosCourse> courses = courseRepository.listByCompany(companyId, true);

        StringBuilder sb = new StringBuilder();

        // --- CURSOS ---
        if (courses.isEmpty()) {
            sb.append("CURSOS ATIVOS: (nenhum curso ativo no momento.)\n\n");
        } else {
            sb.append("CURSOS ATIVOS (use o course_id EXATO na tag):\n");
            for (CursosCourse c : courses) {
                int modules = moduleRepository.listByCourse(companyId, c.id()).size();
                sb.append("- ").append(c.id()).append(" · ").append(c.title())
                    .append(": R$ ").append(formatBrl(c.monthlyCents())).append("/mês");
                if (c.category() != null && !c.category().isBlank()) {
                    sb.append(" [").append(c.category().strip()).append("]");
                }
                sb.append(", ").append(modules).append(" módulo(s)");
                if (c.description() != null && !c.description().isBlank()) {
                    sb.append(" — ").append(c.description().strip());
                }
                sb.append("\n");
            }
            sb.append("\n");
        }

        // --- MATRÍCULAS DO CLIENTE (anti-dupla + progresso) ---
        if (contactId != null) {
            List<CursosEnrollment> mine = enrollmentRepository.listByCompany(companyId, null, null, contactId, 50, 0);
            if (!mine.isEmpty()) {
                sb.append("MATRÍCULAS DO CLIENTE:\n");
                for (CursosEnrollment e : mine) {
                    int done = enrollmentRepository.doneCount(e.id());
                    int total = enrollmentRepository.totalModules(e.id());
                    String next = enrollmentRepository.findNextModule(e.id()).map(m -> m.title()).orElse(null);
                    sb.append("- matrícula ").append(e.id()).append(" · curso ").append(e.courseTitle())
                        .append(" (").append(e.status()).append("), progresso ").append(done).append("/").append(total);
                    if (next != null) {
                        sb.append(", próximo módulo: \"").append(next).append("\"");
                    } else {
                        sb.append(", trilha concluída");
                    }
                    sb.append("\n");
                }
                sb.append("NÃO ofereça nova matrícula num curso em que o cliente já está ATIVO.\n\n");
            }
        }

        // --- PERSONA + INSTRUÇÕES ---
        sb.append("INSTRUÇÕES DE MATRÍCULA E ENTREGA DE MÓDULO:\n")
            .append("Você atende a secretaria de uma escola/curso. Apresente os cursos com clareza "
                + "(mensalidade, categoria, nº de módulos). NUNCA prometa certificado, aprovação, nota "
                + "ou resultado de aprendizado. NUNCA invente curso, módulo, preço ou mensalidade fora "
                + "da lista acima. Confirme o curso + nome ANTES de emitir a tag de matrícula. "
                + "Quando o aluno pedir o próximo conteúdo/módulo e ele estiver matriculado, emita a tag "
                + "de entrega referenciando a matrícula DELE. As tags devem terminar a mensagem, em linha "
                + "própria, sem markdown:\n")
            .append("<matricula_curso>{\"course_id\":\"UUID\",\"student_name\":\"...\",\"notes\":\"...\"}</matricula_curso>\n")
            .append("<entrega_modulo>{\"enrollment_id\":\"UUID\"}</entrega_modulo>\n")
            .append("Use ids EXATOS das listas. Só emita a matrícula na confirmação final. Só emita a "
                + "entrega de módulo para uma matrícula DO PRÓPRIO cliente desta conversa.\n\n");

        return sb.toString();
    }

    private static String formatBrl(int cents) {
        return String.format("%d,%02d", cents / 100, cents % 100);
    }
}
