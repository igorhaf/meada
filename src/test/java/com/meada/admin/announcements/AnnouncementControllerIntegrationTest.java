package com.meada.admin.announcements;

import com.meada.admin.AbstractAdminIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Testa os anúncios cross-tenant (camada 6.7): CRUD do super-admin + log, feed filtrado do
 * tenant, dismiss persiste e some, e o guard de super-admin.
 */
class AnnouncementControllerIntegrationTest extends AbstractAdminIntegrationTest {

    private static final UUID SUPER_SUB = UUID.fromString("88888888-8888-8888-8888-888888888888");

    private String superToken() {
        return mintValidToken(SUPER_ADMIN_EMAIL, SUPER_SUB);
    }

    /** Insere um anúncio publicado não-expirado e devolve o id. */
    private UUID seedAnnouncement(String title, String severity) {
        return jdbcTemplate.queryForObject(
            "insert into announcements (title, body, severity) values (?, 'corpo', ?) returning id",
            UUID.class, title, severity);
    }

    @Test
    @DisplayName("CRUD admin: POST cria (201 + log) → GET lista → PATCH edita → DELETE expira")
    void admin_crud() throws Exception {
        // POST
        mockMvc.perform(post("/admin/announcements").header("Authorization", "Bearer " + superToken())
                .contentType("application/json")
                .content("{\"title\":\"Manutenção\",\"body\":\"Haverá manutenção\",\"severity\":\"warning\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.title").value("Manutenção"))
            .andExpect(jsonPath("$.severity").value("warning"))
            .andExpect(jsonPath("$.dismissable").value(true));

        // log ANNOUNCEMENT_CREATED
        Long logged = jdbcTemplate.queryForObject(
            "select count(*) from admin_action_log where action = 'ANNOUNCEMENT_CREATED'", Long.class);
        org.assertj.core.api.Assertions.assertThat(logged).isEqualTo(1L);

        UUID id = jdbcTemplate.queryForObject(
            "select id from announcements where title = 'Manutenção'", UUID.class);

        // GET lista
        mockMvc.perform(get("/admin/announcements").header("Authorization", "Bearer " + superToken()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.total").value(1));

        // PATCH edita severity
        mockMvc.perform(patch("/admin/announcements/" + id).header("Authorization", "Bearer " + superToken())
                .contentType("application/json").content("{\"severity\":\"critical\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.severity").value("critical"));

        // DELETE → soft (expires_at = now())
        mockMvc.perform(delete("/admin/announcements/" + id).header("Authorization", "Bearer " + superToken()))
            .andExpect(status().isNoContent());
        Long expired = jdbcTemplate.queryForObject(
            "select count(*) from announcements where id = ? and expires_at is not null", Long.class, id);
        org.assertj.core.api.Assertions.assertThat(expired).isEqualTo(1L);
    }

    @Test
    @DisplayName("tenant feed: lista publicados não-expirados; expirado NÃO aparece")
    void tenant_feedFiltered() throws Exception {
        UUID tenantSub = UUID.randomUUID();
        seedTenantAdmin(TENANT_ADMIN_EMAIL, tenantSub);
        String t = mintValidToken(TENANT_ADMIN_EMAIL, tenantSub);

        seedAnnouncement("Ativo", "info");
        // um expirado (expires_at no passado) — não deve aparecer
        jdbcTemplate.update(
            "insert into announcements (title, body, severity, expires_at) "
                + "values ('Expirado', 'corpo', 'info', now() - interval '1 day')");

        mockMvc.perform(get("/admin/me/announcements").header("Authorization", "Bearer " + t))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items.length()").value(1))
            .andExpect(jsonPath("$.items[0].title").value("Ativo"));
    }

    @Test
    @DisplayName("dismiss persiste em announcement_dismissals")
    void tenant_dismissPersists() throws Exception {
        UUID tenantSub = UUID.randomUUID();
        seedTenantAdmin(TENANT_ADMIN_EMAIL, tenantSub);
        String t = mintValidToken(TENANT_ADMIN_EMAIL, tenantSub);
        UUID id = seedAnnouncement("Para dispensar", "info");

        mockMvc.perform(post("/admin/me/announcements/" + id + "/dismiss")
                .header("Authorization", "Bearer " + t))
            .andExpect(status().isNoContent());

        Long rows = jdbcTemplate.queryForObject(
            "select count(*) from announcement_dismissals where announcement_id = ? and user_id = ?",
            Long.class, id, tenantSub);
        org.assertj.core.api.Assertions.assertThat(rows).isEqualTo(1L);
    }

    @Test
    @DisplayName("dismissed não aparece de novo no feed do usuário")
    void tenant_dismissedNotReturned() throws Exception {
        UUID tenantSub = UUID.randomUUID();
        seedTenantAdmin(TENANT_ADMIN_EMAIL, tenantSub);
        String t = mintValidToken(TENANT_ADMIN_EMAIL, tenantSub);
        UUID id = seedAnnouncement("Some depois", "info");

        // antes do dismiss: aparece
        mockMvc.perform(get("/admin/me/announcements").header("Authorization", "Bearer " + t))
            .andExpect(jsonPath("$.items.length()").value(1));

        mockMvc.perform(post("/admin/me/announcements/" + id + "/dismiss")
                .header("Authorization", "Bearer " + t))
            .andExpect(status().isNoContent());

        // depois do dismiss: some
        mockMvc.perform(get("/admin/me/announcements").header("Authorization", "Bearer " + t))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items.length()").value(0));
    }

    @Test
    @DisplayName("tenant → 403 nos endpoints admin de anúncios (mas vê o próprio feed)")
    void tenant_forbiddenOnAdmin() throws Exception {
        UUID tenantSub = UUID.randomUUID();
        seedTenantAdmin(TENANT_ADMIN_EMAIL, tenantSub);
        String t = mintValidToken(TENANT_ADMIN_EMAIL, tenantSub);

        mockMvc.perform(get("/admin/announcements").header("Authorization", "Bearer " + t))
            .andExpect(status().isForbidden());
        mockMvc.perform(post("/admin/announcements").header("Authorization", "Bearer " + t)
                .contentType("application/json").content("{\"title\":\"x\",\"body\":\"y\"}"))
            .andExpect(status().isForbidden());
        // mas o feed do próprio usuário continua acessível (não é super-admin-only)
        mockMvc.perform(get("/admin/me/announcements").header("Authorization", "Bearer " + t))
            .andExpect(status().isOk());
    }
}
