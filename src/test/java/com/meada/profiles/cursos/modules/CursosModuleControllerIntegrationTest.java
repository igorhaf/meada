package com.meada.profiles.cursos.modules;

import com.meada.admin.AbstractAdminIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Testa os endpoints de módulos (camada 8.20 / perfil cursos, ESCAPADA 1): CRUD aninhado sob o curso,
 * ordenação por position, 409 duplicate_position, profile guard 403. Rotas
 * /api/cursos/courses/{courseId}/modules.
 */
class CursosModuleControllerIntegrationTest extends AbstractAdminIntegrationTest {

    private static final String JSON = "application/json";

    private UUID seedTenant(UUID sub, String email, String profileId) {
        UUID companyId = seedTenantAdmin(email, sub);
        jdbcTemplate.update("update companies set profile_id = ? where id = ?", profileId, companyId);
        return companyId;
    }

    private UUID seedCourse(UUID companyId) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("insert into cursos_courses (id, company_id, title, monthly_cents) values (?, ?, 'Inglês', 15000)",
            id, companyId);
        return id;
    }

    @Test
    @DisplayName("POST 2 módulos (position 1 depois 0) → GET lista ordenado por position ASC")
    void createAndListOrdered() throws Exception {
        UUID sub = UUID.randomUUID();
        UUID companyId = seedTenant(sub, "cur@test.dev", "cursos");
        String t = mintValidToken("cur@test.dev", sub);
        UUID course = seedCourse(companyId);

        // insere fora de ordem.
        mockMvc.perform(post("/api/cursos/courses/" + course + "/modules").header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"position\":1,\"title\":\"Segundo\",\"content\":\"b\"}"))
            .andExpect(status().isCreated());
        mockMvc.perform(post("/api/cursos/courses/" + course + "/modules").header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"position\":0,\"title\":\"Primeiro\",\"content\":\"a\"}"))
            .andExpect(status().isCreated());

        mockMvc.perform(get("/api/cursos/courses/" + course + "/modules").header("Authorization", "Bearer " + t))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items.length()").value(2))
            .andExpect(jsonPath("$.items[0].title").value("Primeiro"))
            .andExpect(jsonPath("$.items[1].title").value("Segundo"));
    }

    @Test
    @DisplayName("POST com position já usada → 409 duplicate_position")
    void duplicatePosition() throws Exception {
        UUID sub = UUID.randomUUID();
        UUID companyId = seedTenant(sub, "cur@test.dev", "cursos");
        String t = mintValidToken("cur@test.dev", sub);
        UUID course = seedCourse(companyId);

        mockMvc.perform(post("/api/cursos/courses/" + course + "/modules").header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"position\":0,\"title\":\"Um\",\"content\":\"a\"}"))
            .andExpect(status().isCreated());
        mockMvc.perform(post("/api/cursos/courses/" + course + "/modules").header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"position\":0,\"title\":\"Colide\",\"content\":\"x\"}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.reason").value("duplicate_position"));
    }

    @Test
    @DisplayName("tenant NÃO-cursos (pousada) → /api/cursos/courses/{id}/modules → 403")
    void wrongProfile() throws Exception {
        UUID sub = UUID.randomUUID();
        seedTenant(sub, "pousada@test.dev", "pousada");
        String t = mintValidToken("pousada@test.dev", sub);
        mockMvc.perform(get("/api/cursos/courses/" + UUID.randomUUID() + "/modules").header("Authorization", "Bearer " + t))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.reason").value("forbidden_wrong_profile"));
    }
}
