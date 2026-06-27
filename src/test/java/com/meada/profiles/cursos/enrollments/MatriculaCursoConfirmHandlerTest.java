package com.meada.profiles.cursos.enrollments;

import com.meada.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testa o MatriculaCursoConfirmHandler (camada 8.20 / perfil cursos): tag válida → cria com snapshots
 * (preço da IA descartado); curso inválido → empty; já matriculado → empty; sem tag → empty. Tag
 * {@code <matricula_curso>}. Clone do MatriculaConfirmHandlerTest (camada 7.7).
 */
class MatriculaCursoConfirmHandlerTest extends AbstractIntegrationTest {

    @Autowired
    private MatriculaCursoConfirmHandler handler;

    private static final UUID COMPANY = UUID.fromString("cc100000-0000-0000-0000-000000000002");
    private UUID conversationId;
    private UUID contactId;
    private UUID course;

    @BeforeEach
    void seed() {
        jdbcTemplate.update("insert into companies (id, name, slug, profile_id) values (?, ?, ?, 'cursos')",
            COMPANY, "Curso H", "curso-h");
        UUID instance = UUID.randomUUID();
        contactId = UUID.randomUUID();
        conversationId = UUID.randomUUID();
        jdbcTemplate.update("insert into whatsapp_instances (id, company_id, instance_name, evolution_token) values (?, ?, ?, ?)",
            instance, COMPANY, "inst", "tok");
        jdbcTemplate.update("insert into contacts (id, company_id, phone_number, name) values (?, ?, ?, ?)",
            contactId, COMPANY, "+5511999991105", "Lucas");
        jdbcTemplate.update("insert into conversations (id, company_id, contact_id, whatsapp_instance_id, status, handled_by) "
            + "values (?, ?, ?, ?, 'open', 'ai')", conversationId, COMPANY, contactId, instance);
        course = UUID.randomUUID();
        jdbcTemplate.update("insert into cursos_courses (id, company_id, title, category, monthly_cents) "
            + "values (?, ?, 'Inglês Básico', 'idiomas', 15000)", course, COMPANY);
    }

    @Test
    @DisplayName("tag <matricula_curso> válida → cria ativa com snapshots; preço da IA descartado")
    void parseAndCreate_ok() {
        // a IA chuta um preço diferente no JSON — deve ser ignorado (snapshot vem do catálogo).
        String aiText = "Pronto, Lucas! Matriculei você no Inglês Básico. Bons estudos!\n"
            + "<matricula_curso>{\"course_id\":\"" + course + "\",\"student_name\":\"Lucas\",\"monthly_cents\":99999}</matricula_curso>";

        Optional<CursosEnrollment> e = handler.parseAndCreate(COMPANY, conversationId, contactId, aiText);

        assertThat(e).isPresent();
        assertThat(e.get().status()).isEqualTo("ativa");
        assertThat(e.get().courseTitle()).isEqualTo("Inglês Básico");
        assertThat(e.get().courseMonthlyCents()).isEqualTo(15000);   // do catálogo, NÃO 99999.
        assertThat(e.get().studentName()).isEqualTo("Lucas");
    }

    @Test
    @DisplayName("tag com course_id inexistente → Optional.empty")
    void parseAndCreate_unknownCourse() {
        String aiText = "<matricula_curso>{\"course_id\":\"" + UUID.randomUUID() + "\",\"student_name\":\"Lucas\"}</matricula_curso>";
        Optional<CursosEnrollment> e = handler.parseAndCreate(COMPANY, conversationId, contactId, aiText);
        assertThat(e).isEmpty();
    }

    @Test
    @DisplayName("já matriculado no curso → Optional.empty (anti-dupla)")
    void parseAndCreate_alreadyEnrolled() {
        String aiText = "<matricula_curso>{\"course_id\":\"" + course + "\",\"student_name\":\"Lucas\"}</matricula_curso>";
        assertThat(handler.parseAndCreate(COMPANY, conversationId, contactId, aiText)).isPresent();
        // 2ª vez no mesmo curso/contato → empty.
        assertThat(handler.parseAndCreate(COMPANY, conversationId, contactId, aiText)).isEmpty();
    }

    @Test
    @DisplayName("texto sem tag → Optional.empty (conversa normal)")
    void parseAndCreate_noTag() {
        Optional<CursosEnrollment> e = handler.parseAndCreate(
            COMPANY, conversationId, contactId, "Oi! Quer conhecer nossos cursos?");
        assertThat(e).isEmpty();
    }
}
