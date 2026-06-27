package com.meada.profiles.cursos.enrollments;

import com.meada.admin.AbstractAdminIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Testa os endpoints de matrículas (camada 8.20 / perfil cursos): 403 perfil errado, GET list, GET
 * detail (com progresso + próximo módulo), PATCH status, 409 transição inválida. NÃO há POST (a
 * matrícula vem da IA). Clone do AcademiaMembershipControllerIntegrationTest (camada 7.7).
 */
class CursosEnrollmentControllerIntegrationTest extends AbstractAdminIntegrationTest {

    private static final String JSON = "application/json";

    private UUID seedTenant(UUID sub, String email, String profileId) {
        UUID companyId = seedTenantAdmin(email, sub);
        jdbcTemplate.update("update companies set profile_id = ? where id = ?", profileId, companyId);
        return companyId;
    }

    private UUID seedCourseWithModule(UUID companyId) {
        UUID course = UUID.randomUUID();
        jdbcTemplate.update("insert into cursos_courses (id, company_id, title, monthly_cents) values (?, ?, 'Inglês', 15000)",
            course, companyId);
        jdbcTemplate.update("insert into cursos_modules (company_id, course_id, position, title, content) "
            + "values (?, ?, 0, 'Módulo 1', 'conteúdo 1')", companyId, course);
        return course;
    }

    private UUID seedEnrollment(UUID companyId, UUID course, String studentName) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("insert into cursos_enrollments (id, company_id, course_id, student_name, course_title, "
            + "course_monthly_cents, status) values (?, ?, ?, ?, 'Inglês', 15000, 'ativa')",
            id, companyId, course, studentName);
        return id;
    }

    @Test
    @DisplayName("tenant NÃO-cursos (pousada) → /api/cursos/enrollments → 403")
    void wrongProfile() throws Exception {
        UUID sub = UUID.randomUUID();
        seedTenant(sub, "pousada@test.dev", "pousada");
        String t = mintValidToken("pousada@test.dev", sub);
        mockMvc.perform(get("/api/cursos/enrollments").header("Authorization", "Bearer " + t))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.reason").value("forbidden_wrong_profile"));
    }

    @Test
    @DisplayName("GET list → 200 com a matrícula; GET detail → progresso 0/1 + próximo módulo")
    void listAndDetail() throws Exception {
        UUID sub = UUID.randomUUID();
        UUID companyId = seedTenant(sub, "cur@test.dev", "cursos");
        String t = mintValidToken("cur@test.dev", sub);
        UUID course = seedCourseWithModule(companyId);
        UUID enrollment = seedEnrollment(companyId, course, "Pedro");

        mockMvc.perform(get("/api/cursos/enrollments").header("Authorization", "Bearer " + t))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items.length()").value(1));

        mockMvc.perform(get("/api/cursos/enrollments/" + enrollment).header("Authorization", "Bearer " + t))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.enrollment.status").value("ativa"))
            .andExpect(jsonPath("$.progress.doneCount").value(0))
            .andExpect(jsonPath("$.progress.totalModules").value(1))
            .andExpect(jsonPath("$.progress.nextModuleTitle").value("Módulo 1"));
    }

    @Test
    @DisplayName("PATCH status ativa→concluida → 200; concluida→ativa (terminal) → 409")
    void patchStatus() throws Exception {
        UUID sub = UUID.randomUUID();
        UUID companyId = seedTenant(sub, "cur@test.dev", "cursos");
        String t = mintValidToken("cur@test.dev", sub);
        UUID course = seedCourseWithModule(companyId);
        UUID enrollment = seedEnrollment(companyId, course, "Pedro");

        mockMvc.perform(patch("/api/cursos/enrollments/" + enrollment + "/status").header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"newStatus\":\"concluida\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("concluida"));

        mockMvc.perform(patch("/api/cursos/enrollments/" + enrollment + "/status").header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"newStatus\":\"ativa\"}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.reason").value("invalid_status_transition"));
    }
}
