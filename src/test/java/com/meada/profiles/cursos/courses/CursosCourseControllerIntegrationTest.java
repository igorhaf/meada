package com.meada.profiles.cursos.courses;

import com.meada.admin.AbstractAdminIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Testa os endpoints de cursos (camada 8.20 / perfil cursos): CRUD + profile guard 403 +
 * 409 course_in_use (curso com matrícula). Clone do AcademiaPlanControllerIntegrationTest (camada 7.7).
 */
class CursosCourseControllerIntegrationTest extends AbstractAdminIntegrationTest {

    private static final String JSON = "application/json";

    private UUID seedTenant(UUID sub, String email, String profileId) {
        UUID companyId = seedTenantAdmin(email, sub);
        jdbcTemplate.update("update companies set profile_id = ? where id = ?", profileId, companyId);
        return companyId;
    }

    @Test
    @DisplayName("CRUD: POST cria → GET lista → DELETE")
    void crud() throws Exception {
        UUID sub = UUID.randomUUID();
        seedTenant(sub, "cur@test.dev", "cursos");
        String t = mintValidToken("cur@test.dev", sub);

        mockMvc.perform(post("/api/cursos/courses").header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"title\":\"Inglês Básico\",\"category\":\"idiomas\",\"monthlyCents\":15000}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.title").value("Inglês Básico"))
            .andExpect(jsonPath("$.category").value("idiomas"));

        UUID id = jdbcTemplate.queryForObject("select id from cursos_courses where title = 'Inglês Básico'", UUID.class);

        mockMvc.perform(get("/api/cursos/courses").header("Authorization", "Bearer " + t))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items.length()").value(1));

        mockMvc.perform(delete("/api/cursos/courses/" + id).header("Authorization", "Bearer " + t))
            .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("DELETE curso com matrícula (FK restrict) → 409 course_in_use")
    void deleteInUse() throws Exception {
        UUID sub = UUID.randomUUID();
        UUID companyId = seedTenant(sub, "cur@test.dev", "cursos");
        String t = mintValidToken("cur@test.dev", sub);
        UUID course = UUID.randomUUID();
        jdbcTemplate.update("insert into cursos_courses (id, company_id, title, monthly_cents) values (?, ?, 'Inglês', 15000)",
            course, companyId);
        jdbcTemplate.update("insert into cursos_enrollments (id, company_id, course_id, student_name, course_title, "
            + "course_monthly_cents, status) values (?, ?, ?, 'Pedro', 'Inglês', 15000, 'ativa')",
            UUID.randomUUID(), companyId, course);

        mockMvc.perform(delete("/api/cursos/courses/" + course).header("Authorization", "Bearer " + t))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.reason").value("course_in_use"));
    }

    @Test
    @DisplayName("tenant NÃO-cursos (pousada) → /api/cursos/courses → 403")
    void wrongProfile() throws Exception {
        UUID sub = UUID.randomUUID();
        seedTenant(sub, "pousada@test.dev", "pousada");
        String t = mintValidToken("pousada@test.dev", sub);
        mockMvc.perform(get("/api/cursos/courses").header("Authorization", "Bearer " + t))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.reason").value("forbidden_wrong_profile"));
    }
}
