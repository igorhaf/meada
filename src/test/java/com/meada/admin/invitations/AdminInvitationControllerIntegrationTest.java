package com.meada.admin.invitations;

import com.meada.admin.AbstractAdminIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Testa /admin/invitations/all + /revoke (camada 6.2, super-admin global cross-tenant).
 * Distinto do InvitationController do tenant.
 */
class AdminInvitationControllerIntegrationTest extends AbstractAdminIntegrationTest {

    private static final UUID SUPER_SUB = UUID.fromString("77777777-7777-7777-7777-777777777777");

    private String superToken() {
        return mintValidToken(SUPER_ADMIN_EMAIL, SUPER_SUB);
    }

    private UUID seedInvitation(UUID companyId, String email, Instant expiresAt) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
            "insert into tenant_invitations (id, company_id, email, token, expires_at) "
                + "values (?, ?, ?, ?, ?)",
            id, companyId, email, "tok-" + id, Timestamp.from(expiresAt));
        return id;
    }

    @Test
    @DisplayName("GET /admin/invitations/all lista cross-tenant com companyName + status derivado")
    void listAll_crossTenant() throws Exception {
        UUID c1 = seedTenantAdmin("a@x.com", UUID.randomUUID());
        UUID c2 = seedTenantAdmin("b@y.com", UUID.randomUUID());
        seedInvitation(c1, "inv1@x.com", Instant.now().plus(7, ChronoUnit.DAYS));
        seedInvitation(c2, "inv2@y.com", Instant.now().plus(7, ChronoUnit.DAYS));

        mockMvc.perform(get("/admin/invitations/all").header("Authorization", "Bearer " + superToken()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.total").value(2))
            .andExpect(jsonPath("$.items[0].companyName").isNotEmpty())
            .andExpect(jsonPath("$.items[0].status").isNotEmpty());
    }

    @Test
    @DisplayName("filtro status=pending retorna só os pendentes")
    void listAll_filterPending() throws Exception {
        UUID c1 = seedTenantAdmin("a@x.com", UUID.randomUUID());
        seedInvitation(c1, "pend@x.com", Instant.now().plus(7, ChronoUnit.DAYS));
        UUID expired = seedInvitation(c1, "exp@x.com", Instant.now().minus(1, ChronoUnit.DAYS));

        mockMvc.perform(get("/admin/invitations/all?status=pending")
                .header("Authorization", "Bearer " + superToken()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.total").value(1))
            .andExpect(jsonPath("$.items[0].email").value("pend@x.com"));
    }

    @Test
    @DisplayName("revoke → 204 (revoked_at set + log); revoke de novo → 409")
    void revoke_thenConflict() throws Exception {
        UUID c1 = seedTenantAdmin("a@x.com", UUID.randomUUID());
        UUID inv = seedInvitation(c1, "rev@x.com", Instant.now().plus(7, ChronoUnit.DAYS));

        mockMvc.perform(post("/admin/invitations/" + inv + "/revoke")
                .header("Authorization", "Bearer " + superToken()))
            .andExpect(status().isNoContent());

        Object revokedAt = jdbcTemplate.queryForMap(
            "select revoked_at from tenant_invitations where id=?", inv).get("revoked_at");
        org.assertj.core.api.Assertions.assertThat(revokedAt).isNotNull();
        Long logged = jdbcTemplate.queryForObject(
            "select count(*) from admin_action_log where action='INVITATION_REVOKED' and target_id=?",
            Long.class, inv);
        org.assertj.core.api.Assertions.assertThat(logged).isEqualTo(1);

        mockMvc.perform(post("/admin/invitations/" + inv + "/revoke")
                .header("Authorization", "Bearer " + superToken()))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.reason").value("invitation_already_revoked"));
    }

    @Test
    @DisplayName("tenant → 403")
    void tenant_forbidden() throws Exception {
        UUID tenantSub = UUID.randomUUID();
        seedTenantAdmin(TENANT_ADMIN_EMAIL, tenantSub);
        mockMvc.perform(get("/admin/invitations/all")
                .header("Authorization", "Bearer " + mintValidToken(TENANT_ADMIN_EMAIL, tenantSub)))
            .andExpect(status().isForbidden());
    }
}
