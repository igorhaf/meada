package com.meada.admin.companies;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.meada.admin.AbstractAdminIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Testa /admin/companies (lista paginada + CRUD do drill-down — camadas 4.2 e 6.1). Casos:
 * <ol>
 *   <li>GET super-admin → 200 + página {items,total,page,pageSize} (ordenada por created_at DESC);
 *   <li>GET com filtro status → só as que casam;
 *   <li>GET com filtro q (ilike name/slug) → só as que casam;
 *   <li>GET tenant-admin → 403 forbidden_not_super_admin;
 *   <li>GET sem token → 401 missing_auth_header;
 *   <li>POST cria/duplica/inválido (4.2 — inalterado);
 *   <li>GET detalhe com contadores;
 *   <li>PATCH update + slug conflict 409;
 *   <li>POST suspend (+409 já suspensa) / reactivate;
 *   <li>DELETE hard delete (conversas+mensagens+contatos → tudo apagado + COMPANY_DELETED no log);
 *   <li>CRUD de notas.
 * </ol>
 */
class CompanyAdminControllerIntegrationTest extends AbstractAdminIntegrationTest {

    private static final UUID SUB = UUID.fromString("33333333-3333-3333-3333-333333333333");

    @Autowired
    private ObjectMapper objectMapper;

    /** Insere uma company com created_at + palette_id explícitos (determinismo da
     *  ordenação DESC e da asserção de paletteId no GET). */
    private void seedCompany(String name, String slug, Instant createdAt, String paletteId) {
        jdbcTemplate.update(
            "insert into companies (id, name, slug, created_at, palette_id) values (?, ?, ?, ?, ?)",
            UUID.randomUUID(), name, slug, Timestamp.from(createdAt), paletteId);
    }

    /** Insere uma company e devolve o id (para os testes de detalhe/update/delete). */
    private UUID seedCompanyReturningId(String name, String slug) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
            "insert into companies (id, name, slug, palette_id) values (?, ?, ?, 'meada-default')",
            id, name, slug);
        return id;
    }

    // ---- GET /admin/companies (lista paginada) ------------------------------

    @Test
    @DisplayName("super-admin → 200 página com items (mais novas primeiro, com paletteId)")
    void superAdmin_listsAllCompanies() throws Exception {
        Instant now = Instant.now();
        seedCompany("Empresa Antiga", "empresa-antiga", now.minusSeconds(3600), "meada-default");
        seedCompany("Empresa Nova", "empresa-nova", now, "oceano");

        String token = mintValidToken(SUPER_ADMIN_EMAIL, SUB);
        mockMvc.perform(get("/admin/companies").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.total").value(2))
            .andExpect(jsonPath("$.page").value(0))
            .andExpect(jsonPath("$.pageSize").value(20))
            .andExpect(jsonPath("$.items").isArray())
            .andExpect(jsonPath("$.items.length()").value(2))
            // ORDER BY created_at DESC → a mais nova vem primeiro
            .andExpect(jsonPath("$.items[0].name").value("Empresa Nova"))
            .andExpect(jsonPath("$.items[0].slug").value("empresa-nova"))
            .andExpect(jsonPath("$.items[0].paletteId").value("oceano"))
            .andExpect(jsonPath("$.items[1].name").value("Empresa Antiga"))
            .andExpect(jsonPath("$.items[1].paletteId").value("meada-default"));
    }

    @Test
    @DisplayName("filtro status=suspended → só as suspensas")
    void list_filterByStatus() throws Exception {
        UUID suspended = seedCompanyReturningId("Suspensa", "suspensa");
        jdbcTemplate.update("update companies set status = 'suspended' where id = ?", suspended);
        seedCompanyReturningId("Ativa", "ativa");

        String token = mintValidToken(SUPER_ADMIN_EMAIL, SUB);
        mockMvc.perform(get("/admin/companies?status=suspended")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.total").value(1))
            .andExpect(jsonPath("$.items.length()").value(1))
            .andExpect(jsonPath("$.items[0].slug").value("suspensa"));
    }

    @Test
    @DisplayName("filtro q (ilike name/slug) → só as que casam")
    void list_filterByQuery() throws Exception {
        seedCompanyReturningId("Acme Corp", "acme-corp");
        seedCompanyReturningId("Beta LTDA", "beta-ltda");

        String token = mintValidToken(SUPER_ADMIN_EMAIL, SUB);
        mockMvc.perform(get("/admin/companies?q=acme")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.total").value(1))
            .andExpect(jsonPath("$.items[0].slug").value("acme-corp"));
    }

    @Test
    @DisplayName("tenant-admin → 403 forbidden_not_super_admin")
    void tenantAdmin_returns403() throws Exception {
        seedTenantAdmin(TENANT_ADMIN_EMAIL, SUB);
        String token = mintValidToken(TENANT_ADMIN_EMAIL, SUB);
        mockMvc.perform(get("/admin/companies").header("Authorization", "Bearer " + token))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.reason").value("forbidden_not_super_admin"));
    }

    @Test
    @DisplayName("sem token → 401 missing_auth_header (endpoint atrás do filtro)")
    void noToken_returns401() throws Exception {
        mockMvc.perform(get("/admin/companies"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.reason").value("missing_auth_header"));
    }

    // ---- POST /admin/companies (4.2) ----------------------------------------

    @Test
    @DisplayName("super-admin cria empresa → 201 + persistida (com paletteId)")
    void create_superAdmin_returns201_andPersists() throws Exception {
        String token = mintValidToken(SUPER_ADMIN_EMAIL, SUB);
        mockMvc.perform(post("/admin/companies")
                .header("Authorization", "Bearer " + token)
                .contentType(APPLICATION_JSON)
                .content("{\"name\":\"Acme Corp\",\"slug\":\"acme-corp\",\"paletteId\":\"oceano\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.name").value("Acme Corp"))
            .andExpect(jsonPath("$.slug").value("acme-corp"))
            .andExpect(jsonPath("$.status").value("active"))
            .andExpect(jsonPath("$.id").exists())
            .andExpect(jsonPath("$.createdAt").exists())
            .andExpect(jsonPath("$.paletteId").value("oceano"));

        String persistedPalette = jdbcTemplate.queryForObject(
            "select palette_id from companies where slug = ?", String.class, "acme-corp");
        assertThat(persistedPalette).isEqualTo("oceano");

        Long auditCount = jdbcTemplate.queryForObject(
            "select count(*) from audit_log where entity = 'company' and action = 'created'",
            Long.class);
        assertThat(auditCount).isEqualTo(1L);
    }

    @Test
    @DisplayName("tenant-admin tenta criar → 403 forbidden_not_super_admin (nada persistido)")
    void create_tenantAdmin_returns403() throws Exception {
        seedTenantAdmin(TENANT_ADMIN_EMAIL, SUB);
        String token = mintValidToken(TENANT_ADMIN_EMAIL, SUB);
        mockMvc.perform(post("/admin/companies")
                .header("Authorization", "Bearer " + token)
                .contentType(APPLICATION_JSON)
                .content("{\"name\":\"Acme Corp\",\"slug\":\"acme-corp\",\"paletteId\":\"oceano\"}"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.reason").value("forbidden_not_super_admin"));

        Long count = jdbcTemplate.queryForObject(
            "select count(*) from companies where slug = ?", Long.class, "acme-corp");
        assertThat(count).isZero();
    }

    @Test
    @DisplayName("slug duplicado → 409 slug_already_exists (não duplica)")
    void create_duplicateSlug_returns409() throws Exception {
        seedCompany("Já Existe", "acme-corp", Instant.now(), "meada-default");
        String token = mintValidToken(SUPER_ADMIN_EMAIL, SUB);
        mockMvc.perform(post("/admin/companies")
                .header("Authorization", "Bearer " + token)
                .contentType(APPLICATION_JSON)
                .content("{\"name\":\"Acme Corp\",\"slug\":\"acme-corp\",\"paletteId\":\"oceano\"}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.reason").value("slug_already_exists"));

        Long count = jdbcTemplate.queryForObject(
            "select count(*) from companies where slug = ?", Long.class, "acme-corp");
        assertThat(count).isEqualTo(1L);
    }

    @Test
    @DisplayName("payload inválido (slug com maiúscula/espaço) → 400 (nada persistido)")
    void create_invalidPayload_returns400() throws Exception {
        String token = mintValidToken(SUPER_ADMIN_EMAIL, SUB);
        mockMvc.perform(post("/admin/companies")
                .header("Authorization", "Bearer " + token)
                .contentType(APPLICATION_JSON)
                .content("{\"name\":\"Acme Corp\",\"slug\":\"Acme Corp\",\"paletteId\":\"oceano\"}"))
            .andExpect(status().isBadRequest());

        Long count = jdbcTemplate.queryForObject("select count(*) from companies", Long.class);
        assertThat(count).isZero();
    }

    @Test
    @DisplayName("paletteId em branco → 400 (validação @NotBlank; nada persistido)")
    void create_blankPaletteId_returns400() throws Exception {
        String token = mintValidToken(SUPER_ADMIN_EMAIL, SUB);
        mockMvc.perform(post("/admin/companies")
                .header("Authorization", "Bearer " + token)
                .contentType(APPLICATION_JSON)
                .content("{\"name\":\"Acme Corp\",\"slug\":\"acme-corp\",\"paletteId\":\"\"}"))
            .andExpect(status().isBadRequest());

        Long count = jdbcTemplate.queryForObject("select count(*) from companies", Long.class);
        assertThat(count).isZero();
    }

    @Test
    @DisplayName("paletteId ausente do body → 400")
    void create_missingPaletteId_returns400() throws Exception {
        String token = mintValidToken(SUPER_ADMIN_EMAIL, SUB);
        mockMvc.perform(post("/admin/companies")
                .header("Authorization", "Bearer " + token)
                .contentType(APPLICATION_JSON)
                .content("{\"name\":\"Acme Corp\",\"slug\":\"acme-corp\"}"))
            .andExpect(status().isBadRequest());

        Long count = jdbcTemplate.queryForObject("select count(*) from companies", Long.class);
        assertThat(count).isZero();
    }

    // ---- GET /admin/companies/{id} (detalhe com contadores) -----------------

    @Test
    @DisplayName("detalhe → 200 com contadores (users, contacts, open conversations) e owner")
    void detail_returnsCountsAndOwner() throws Exception {
        UUID companyId = seedCompanyReturningId("Detalhe Co", "detalhe-co");
        jdbcTemplate.update(
            "update companies set max_admins = 3, max_faqs = 10, max_conversations_month = 500 "
                + "where id = ?", companyId);

        // owner: 1 user role owner + companies.owner_id apontando para ele
        UUID ownerId = UUID.randomUUID();
        jdbcTemplate.update("insert into auth.users (id) values (?) on conflict (id) do nothing",
            ownerId);
        jdbcTemplate.update(
            "insert into users (id, company_id, email, full_name, role) "
                + "values (?, ?, 'owner@detalhe.co', 'Dona Detalhe', 'owner')",
            ownerId, companyId);
        jdbcTemplate.update("update companies set owner_id = ? where id = ?", ownerId, companyId);

        // 1 instância + 1 contato + 1 conversa aberta + 1 mensagem
        UUID instanceId = UUID.randomUUID();
        jdbcTemplate.update(
            "insert into whatsapp_instances (id, company_id, instance_name, evolution_token) "
                + "values (?, ?, ?, 'tok')",
            instanceId, companyId, "inst-detalhe-co");
        UUID contactId = UUID.randomUUID();
        jdbcTemplate.update(
            "insert into contacts (id, company_id, phone_number, name) values (?, ?, ?, ?)",
            contactId, companyId, "+5511999990000", "Cliente X");
        UUID convId = UUID.randomUUID();
        jdbcTemplate.update(
            "insert into conversations (id, company_id, contact_id, whatsapp_instance_id, status, "
                + "last_message_at) values (?, ?, ?, ?, 'open', now())",
            convId, companyId, contactId, instanceId);
        jdbcTemplate.update(
            "insert into messages (company_id, conversation_id, direction, sender, content) "
                + "values (?, ?, 'inbound', 'contact', 'oi')",
            companyId, convId);

        String token = mintValidToken(SUPER_ADMIN_EMAIL, SUB);
        mockMvc.perform(get("/admin/companies/" + companyId)
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("Detalhe Co"))
            .andExpect(jsonPath("$.maxAdmins").value(3))
            .andExpect(jsonPath("$.maxFaqs").value(10))
            .andExpect(jsonPath("$.maxConversationsMonth").value(500))
            .andExpect(jsonPath("$.usersCount").value(1))
            .andExpect(jsonPath("$.contactsCount").value(1))
            .andExpect(jsonPath("$.openConversations").value(1))
            .andExpect(jsonPath("$.messagesLast30d").value(1))
            .andExpect(jsonPath("$.ownerEmail").value("owner@detalhe.co"))
            .andExpect(jsonPath("$.ownerName").value("Dona Detalhe"))
            .andExpect(jsonPath("$.lastActivityAt").exists());
    }

    @Test
    @DisplayName("detalhe de empresa inexistente → 404 company_not_found")
    void detail_notFound_returns404() throws Exception {
        String token = mintValidToken(SUPER_ADMIN_EMAIL, SUB);
        mockMvc.perform(get("/admin/companies/" + UUID.randomUUID())
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.reason").value("company_not_found"));
    }

    // ---- PATCH /admin/companies/{id} (update) -------------------------------

    @Test
    @DisplayName("update → 200 + persistido + COMPANY_UPDATED no admin_action_log")
    void update_persistsAndLogs() throws Exception {
        UUID companyId = seedCompanyReturningId("Antigo Nome", "antigo-slug");
        String token = mintValidToken(SUPER_ADMIN_EMAIL, SUB);

        mockMvc.perform(patch("/admin/companies/" + companyId)
                .header("Authorization", "Bearer " + token)
                .contentType(APPLICATION_JSON)
                .content("{\"name\":\"Novo Nome\",\"slug\":\"novo-slug\",\"paletteId\":\"oceano\","
                    + "\"maxAdmins\":5,\"maxFaqs\":20,\"maxConversationsMonth\":1000}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("Novo Nome"))
            .andExpect(jsonPath("$.slug").value("novo-slug"))
            .andExpect(jsonPath("$.maxAdmins").value(5));

        String name = jdbcTemplate.queryForObject(
            "select name from companies where id = ?", String.class, companyId);
        assertThat(name).isEqualTo("Novo Nome");

        Long logCount = jdbcTemplate.queryForObject(
            "select count(*) from admin_action_log where action = 'COMPANY_UPDATED' "
                + "and target_id = ?", Long.class, companyId);
        assertThat(logCount).isEqualTo(1L);
    }

    @Test
    @DisplayName("update com slug já em uso → 409 slug_already_exists (não loga efetivo)")
    void update_duplicateSlug_returns409() throws Exception {
        seedCompanyReturningId("Outra", "slug-ocupado");
        UUID companyId = seedCompanyReturningId("Editanda", "editanda");
        String token = mintValidToken(SUPER_ADMIN_EMAIL, SUB);

        mockMvc.perform(patch("/admin/companies/" + companyId)
                .header("Authorization", "Bearer " + token)
                .contentType(APPLICATION_JSON)
                .content("{\"name\":\"Editanda\",\"slug\":\"slug-ocupado\",\"paletteId\":\"oceano\"}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.reason").value("slug_already_exists"));

        // rollback: nem o nome mudou nem o log COMPANY_UPDATED persistiu (atômico).
        String slug = jdbcTemplate.queryForObject(
            "select slug from companies where id = ?", String.class, companyId);
        assertThat(slug).isEqualTo("editanda");
        Long logCount = jdbcTemplate.queryForObject(
            "select count(*) from admin_action_log where action = 'COMPANY_UPDATED' "
                + "and target_id = ?", Long.class, companyId);
        assertThat(logCount).isZero();
    }

    // ---- PATCH profileId (camada 7.0) ---------------------------------------

    @Test
    @DisplayName("update com profileId válido → 200 + persiste profile_id + detalhe traz profileId")
    void update_validProfileId() throws Exception {
        UUID companyId = seedCompanyReturningId("Vira Legal", "vira-legal");
        String token = mintValidToken(SUPER_ADMIN_EMAIL, SUB);

        mockMvc.perform(patch("/admin/companies/" + companyId)
                .header("Authorization", "Bearer " + token)
                .contentType(APPLICATION_JSON)
                .content("{\"name\":\"Vira Legal\",\"slug\":\"vira-legal\",\"paletteId\":\"indigo\","
                    + "\"profileId\":\"legal\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.profileId").value("legal"));

        String profile = jdbcTemplate.queryForObject(
            "select profile_id from companies where id = ?", String.class, companyId);
        assertThat(profile).isEqualTo("legal");
    }

    @Test
    @DisplayName("update com profileId inválido → 400 invalid_profile_id (não persiste)")
    void update_invalidProfileId() throws Exception {
        UUID companyId = seedCompanyReturningId("Profile Ruim", "profile-ruim");
        String token = mintValidToken(SUPER_ADMIN_EMAIL, SUB);

        mockMvc.perform(patch("/admin/companies/" + companyId)
                .header("Authorization", "Bearer " + token)
                .contentType(APPLICATION_JSON)
                .content("{\"name\":\"Profile Ruim\",\"slug\":\"profile-ruim\",\"paletteId\":\"oceano\","
                    + "\"profileId\":\"inexistente\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.reason").value("invalid_profile_id"));

        // permaneceu 'generic' (default) — nada persistiu.
        String profile = jdbcTemplate.queryForObject(
            "select profile_id from companies where id = ?", String.class, companyId);
        assertThat(profile).isEqualTo("generic");
    }

    // ---- suspend / reactivate ------------------------------------------------

    @Test
    @DisplayName("suspend → 204 + status suspended + COMPANY_SUSPENDED; 2ª vez → 409 already_suspended")
    void suspend_thenConflict() throws Exception {
        UUID companyId = seedCompanyReturningId("Vai Suspender", "vai-suspender");
        String token = mintValidToken(SUPER_ADMIN_EMAIL, SUB);

        mockMvc.perform(post("/admin/companies/" + companyId + "/suspend")
                .header("Authorization", "Bearer " + token)
                .contentType(APPLICATION_JSON)
                .content("{\"reason\":\"inadimplência\"}"))
            .andExpect(status().isNoContent());

        String status = jdbcTemplate.queryForObject(
            "select status from companies where id = ?", String.class, companyId);
        assertThat(status).isEqualTo("suspended");
        Long logCount = jdbcTemplate.queryForObject(
            "select count(*) from admin_action_log where action = 'COMPANY_SUSPENDED' "
                + "and target_id = ?", Long.class, companyId);
        assertThat(logCount).isEqualTo(1L);

        // 2ª suspensão → 409
        mockMvc.perform(post("/admin/companies/" + companyId + "/suspend")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.reason").value("already_suspended"));
    }

    @Test
    @DisplayName("reactivate empresa suspensa → 204 + status active + COMPANY_REACTIVATED")
    void reactivate_returnsActive() throws Exception {
        UUID companyId = seedCompanyReturningId("Vai Reativar", "vai-reativar");
        jdbcTemplate.update("update companies set status = 'suspended' where id = ?", companyId);
        String token = mintValidToken(SUPER_ADMIN_EMAIL, SUB);

        mockMvc.perform(post("/admin/companies/" + companyId + "/reactivate")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isNoContent());

        String status = jdbcTemplate.queryForObject(
            "select status from companies where id = ?", String.class, companyId);
        assertThat(status).isEqualTo("active");
        Long logCount = jdbcTemplate.queryForObject(
            "select count(*) from admin_action_log where action = 'COMPANY_REACTIVATED' "
                + "and target_id = ?", Long.class, companyId);
        assertThat(logCount).isEqualTo(1L);
    }

    // ---- DELETE (hard delete FK-seguro) -------------------------------------

    @Test
    @DisplayName("hard delete → 204 + empresa e TODOS os filhos apagados + COMPANY_DELETED no log")
    void hardDelete_removesEverythingAndLogs() throws Exception {
        UUID companyId = seedCompanyReturningId("Apagar Tudo", "apagar-tudo");

        // user (FK RESTRICT → companies)
        UUID userId = UUID.randomUUID();
        jdbcTemplate.update("insert into auth.users (id) values (?) on conflict (id) do nothing",
            userId);
        jdbcTemplate.update(
            "insert into users (id, company_id, email, role) values (?, ?, 'u@apagar.co', 'admin')",
            userId, companyId);

        // instância + contato + conversa + mensagem (a cadeia RESTRICT crítica)
        UUID instanceId = UUID.randomUUID();
        jdbcTemplate.update(
            "insert into whatsapp_instances (id, company_id, instance_name, evolution_token) "
                + "values (?, ?, ?, 'tok')",
            instanceId, companyId, "inst-apagar-tudo");
        UUID contactId = UUID.randomUUID();
        jdbcTemplate.update(
            "insert into contacts (id, company_id, phone_number, name) values (?, ?, ?, ?)",
            contactId, companyId, "+5511988880000", "Cliente Y");
        UUID convId = UUID.randomUUID();
        jdbcTemplate.update(
            "insert into conversations (id, company_id, contact_id, whatsapp_instance_id) "
                + "values (?, ?, ?, ?)",
            convId, companyId, contactId, instanceId);
        jdbcTemplate.update(
            "insert into messages (company_id, conversation_id, direction, sender, content) "
                + "values (?, ?, 'inbound', 'contact', 'oi')",
            companyId, convId);
        // audit_log (FK RESTRICT → companies)
        jdbcTemplate.update(
            "insert into audit_log (company_id, action, entity) values (?, 'x', 'y')", companyId);
        // nota interna (CASCADE, mas apagada explícito)
        jdbcTemplate.update(
            "insert into admin_notes (company_id, super_admin_user_id, content) "
                + "values (?, ?, 'nota')", companyId, SUB);

        String token = mintValidToken(SUPER_ADMIN_EMAIL, SUB);
        mockMvc.perform(delete("/admin/companies/" + companyId)
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isNoContent());

        // tudo apagado
        assertCountZero("select count(*) from companies where id = ?", companyId);
        assertCountZero("select count(*) from users where company_id = ?", companyId);
        assertCountZero("select count(*) from messages where company_id = ?", companyId);
        assertCountZero("select count(*) from conversations where company_id = ?", companyId);
        assertCountZero("select count(*) from contacts where company_id = ?", companyId);
        assertCountZero("select count(*) from whatsapp_instances where company_id = ?", companyId);
        assertCountZero("select count(*) from audit_log where company_id = ?", companyId);
        assertCountZero("select count(*) from admin_notes where company_id = ?", companyId);

        // o rastro sobrevive (admin_action_log NÃO tem FK para companies)
        Long logCount = jdbcTemplate.queryForObject(
            "select count(*) from admin_action_log where action = 'COMPANY_DELETED' "
                + "and target_id = ?", Long.class, companyId);
        assertThat(logCount).isEqualTo(1L);
    }

    private void assertCountZero(String sql, UUID companyId) {
        Long n = jdbcTemplate.queryForObject(sql, Long.class, companyId);
        assertThat(n).isZero();
    }

    // ---- notas internas (CRUD) -----------------------------------------------

    @Test
    @DisplayName("notas CRUD → cria/lista/edita/apaga + ações no admin_action_log")
    void notes_crud() throws Exception {
        UUID companyId = seedCompanyReturningId("Notas Co", "notas-co");
        String token = mintValidToken(SUPER_ADMIN_EMAIL, SUB);

        // criar
        String body = mockMvc.perform(post("/admin/companies/" + companyId + "/notes")
                .header("Authorization", "Bearer " + token)
                .contentType(APPLICATION_JSON)
                .content("{\"content\":\"Cliente VIP\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.content").value("Cliente VIP"))
            .andExpect(jsonPath("$.id").exists())
            .andReturn().getResponse().getContentAsString();
        UUID noteId = UUID.fromString(objectMapper.readTree(body).get("id").asText());

        // listar
        mockMvc.perform(get("/admin/companies/" + companyId + "/notes")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].content").value("Cliente VIP"));

        // editar
        mockMvc.perform(patch("/admin/companies/" + companyId + "/notes/" + noteId)
                .header("Authorization", "Bearer " + token)
                .contentType(APPLICATION_JSON)
                .content("{\"content\":\"Cliente VIP (atualizado)\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content").value("Cliente VIP (atualizado)"));

        // apagar
        mockMvc.perform(delete("/admin/companies/" + companyId + "/notes/" + noteId)
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isNoContent());

        assertCountZero("select count(*) from admin_notes where company_id = ?", companyId);

        // as 3 ações (NOTE_CREATED/UPDATED/DELETED) ficaram no log
        Long logCount = jdbcTemplate.queryForObject(
            "select count(*) from admin_action_log where target_type = 'note' "
                + "and action in ('NOTE_CREATED','NOTE_UPDATED','NOTE_DELETED')", Long.class);
        assertThat(logCount).isEqualTo(3L);
    }

    @Test
    @DisplayName("editar nota inexistente → 404 note_not_found")
    void updateNote_notFound_returns404() throws Exception {
        UUID companyId = seedCompanyReturningId("Sem Nota", "sem-nota");
        String token = mintValidToken(SUPER_ADMIN_EMAIL, SUB);
        mockMvc.perform(patch("/admin/companies/" + companyId + "/notes/" + UUID.randomUUID())
                .header("Authorization", "Bearer " + token)
                .contentType(APPLICATION_JSON)
                .content("{\"content\":\"x\"}"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.reason").value("note_not_found"));
    }
}
