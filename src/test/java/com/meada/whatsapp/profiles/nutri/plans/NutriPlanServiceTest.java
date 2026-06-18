package com.meada.whatsapp.profiles.nutri.plans;

import com.meada.whatsapp.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testa o NutriPlanService (camada 8.0): a REGRA-CHAVE de no máximo 1 plano ativo por paciente.
 * Criar um 2º plano ativo arquiva o 1º; archive muda status; getActiveByPatient devolve empty sem
 * ativo; criar com active=false não mexe no ativo existente.
 */
class NutriPlanServiceTest extends AbstractIntegrationTest {

    @Autowired
    private NutriPlanService service;

    private static final UUID COMPANY = UUID.fromString("cd000000-0000-0000-0000-000000000003");
    private static final UUID USER = UUID.fromString("dd000000-0000-0000-0000-000000000003");
    private UUID patientId;
    private UUID profId;

    @BeforeEach
    void seed() {
        jdbcTemplate.update("insert into companies (id, name, slug, profile_id) values (?, ?, ?, 'nutri')",
            COMPANY, "Nutri P", "nutri-p");
        jdbcTemplate.update("insert into auth.users (id) values (?) on conflict (id) do nothing", USER);
        jdbcTemplate.update("insert into users (id, company_id, email, role) values (?, ?, 'u@nutri-p.dev', 'admin')",
            USER, COMPANY);
        UUID contactId = UUID.randomUUID();
        jdbcTemplate.update("insert into contacts (id, company_id, phone_number, name) values (?, ?, ?, ?)",
            contactId, COMPANY, "+5511999990083", "Marina");
        patientId = UUID.randomUUID();
        jdbcTemplate.update("insert into nutri_patients (id, company_id, contact_id, name) values (?, ?, ?, 'Marina')",
            patientId, COMPANY, contactId);
        profId = UUID.randomUUID();
        jdbcTemplate.update("insert into nutri_professionals (id, company_id, name) values (?, ?, 'Carla')",
            profId, COMPANY);
    }

    @Test
    @DisplayName("create plano ativo → status 'ativo'")
    void create_active() {
        NutriPlan p = service.create(COMPANY, USER, patientId, profId, "Plano 1", "Corpo 1", null, null, true, null);
        assertThat(p.status()).isEqualTo("ativo");
    }

    @Test
    @DisplayName("2º plano ativo → 1º vira 'arquivado' (só 1 ativo)")
    void secondActive_archivesFirst() {
        NutriPlan first = service.create(COMPANY, USER, patientId, profId, "Plano 1", "Corpo 1", null, null, true, null);
        NutriPlan second = service.create(COMPANY, USER, patientId, profId, "Plano 2", "Corpo 2", null, null, true, null);

        Optional<NutriPlan> active = service.getActiveByPatient(COMPANY, patientId);
        assertThat(active).isPresent();
        assertThat(active.get().id()).isEqualTo(second.id());

        List<NutriPlan> arquivados = service.listByPatient(COMPANY, patientId, "arquivado");
        assertThat(arquivados).extracting(NutriPlan::id).contains(first.id());
        assertThat(arquivados).extracting(NutriPlan::status).containsOnly("arquivado");
    }

    @Test
    @DisplayName("archive plano → status 'arquivado'")
    void archive() {
        NutriPlan p = service.create(COMPANY, USER, patientId, profId, "Plano 1", "Corpo 1", null, null, true, null);
        NutriPlan archived = service.archive(COMPANY, USER, p.id());
        assertThat(archived.status()).isEqualTo("arquivado");
    }

    @Test
    @DisplayName("getActiveByPatient sem plano ativo → empty")
    void getActive_noneActive() {
        assertThat(service.getActiveByPatient(COMPANY, patientId)).isEmpty();
    }

    @Test
    @DisplayName("create com active=false → fica 'arquivado' e NÃO arquiva o ativo existente")
    void inactive_doesNotArchiveExisting() {
        NutriPlan active = service.create(COMPANY, USER, patientId, profId, "Plano 1", "Corpo 1", null, null, true, null);
        NutriPlan draft = service.create(COMPANY, USER, patientId, profId, "Rascunho", "Corpo 2", null, null, false, null);

        assertThat(draft.status()).isEqualTo("arquivado");
        Optional<NutriPlan> stillActive = service.getActiveByPatient(COMPANY, patientId);
        assertThat(stillActive).isPresent();
        assertThat(stillActive.get().id()).isEqualTo(active.id());
    }
}
