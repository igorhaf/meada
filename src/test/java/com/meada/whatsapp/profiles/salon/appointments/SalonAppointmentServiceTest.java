package com.meada.whatsapp.profiles.salon.appointments;

import com.meada.whatsapp.AbstractIntegrationTest;
import com.meada.whatsapp.outbound.EvolutionSender;
import com.meada.whatsapp.profiles.salon.appointments.SalonAppointmentService.ConflictException;
import com.meada.whatsapp.profiles.salon.appointments.SalonAppointmentService.InactiveProfessionalException;
import com.meada.whatsapp.profiles.salon.appointments.SalonAppointmentService.OutsideHoursException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Testa o SalonAppointmentService (camada 7.5): create válida (snapshots), fora do horário,
 * profissional inativo, conflito MESMO PROFISSIONAL, MESMO HORÁRIO PROFISSIONAL DIFERENTE = OK
 * (chave da SM), e transição com notificação (com nome do profissional). EvolutionSender fake.
 */
@Import(SalonAppointmentServiceTest.TestConfig.class)
class SalonAppointmentServiceTest extends AbstractIntegrationTest {

    @Autowired
    private SalonAppointmentService service;
    @Autowired
    private FakeEvolutionSender fakeEvolution;

    private static final UUID COMPANY = UUID.fromString("ca000000-0000-0000-0000-000000000003");
    private UUID profCarla;
    private UUID profPatricia;
    private UUID offeringCorte;
    private UUID contactId;
    private UUID conversationId;

    // 2026-07-01T12:00-03:00 (BRT) → dentro de 09:00–20:00; Corte dura 45min.
    private static final Instant START = Instant.parse("2026-07-01T15:00:00Z");

    @BeforeEach
    void seed() {
        fakeEvolution.reset();
        jdbcTemplate.update("insert into companies (id, name, slug, profile_id) values (?, ?, ?, 'salon')",
            COMPANY, "Salão S", "salao-s");
        profCarla = UUID.randomUUID();
        profPatricia = UUID.randomUUID();
        jdbcTemplate.update("insert into salon_professionals (id, company_id, name, specialty) values (?, ?, 'Carla', 'Cabeleireira')",
            profCarla, COMPANY);
        jdbcTemplate.update("insert into salon_professionals (id, company_id, name, specialty) values (?, ?, 'Patrícia', 'Manicure')",
            profPatricia, COMPANY);
        offeringCorte = UUID.randomUUID();
        jdbcTemplate.update("insert into salon_offerings (id, company_id, name, category, duration_minutes, price_cents) "
            + "values (?, ?, 'Corte feminino', 'Cabelo', 45, 8000)", offeringCorte, COMPANY);
        UUID instance = UUID.randomUUID();
        contactId = UUID.randomUUID();
        conversationId = UUID.randomUUID();
        jdbcTemplate.update("insert into whatsapp_instances (id, company_id, instance_name, evolution_token) values (?, ?, ?, ?)",
            instance, COMPANY, "inst", "tok");
        jdbcTemplate.update("insert into contacts (id, company_id, phone_number, name) values (?, ?, ?, ?)",
            contactId, COMPANY, "+5511999990050", "Cliente");
        jdbcTemplate.update("insert into conversations (id, company_id, contact_id, whatsapp_instance_id, status, handled_by) "
            + "values (?, ?, ?, ?, 'open', 'ai')", conversationId, COMPANY, contactId, instance);
    }

    private SalonAppointment seedWithCarla() {
        return service.create(COMPANY, profCarla, offeringCorte, contactId, conversationId, START, "Cliente", "+5511999990050", null);
    }

    @Test
    @DisplayName("create válida → agendado, com snapshots de profissional/serviço/preço/duração")
    void create_agendado() {
        SalonAppointment a = seedWithCarla();
        assertThat(a.status()).isEqualTo("agendado");
        assertThat(a.professionalName()).isEqualTo("Carla");
        assertThat(a.serviceName()).isEqualTo("Corte feminino");
        assertThat(a.priceCents()).isEqualTo(8000);
        assertThat(a.durationMinutes()).isEqualTo(45);
    }

    @Test
    @DisplayName("create fora do horário (07:00 BRT) → OutsideHoursException (400)")
    void create_outsideHours() {
        // 2026-07-01T07:00-03:00 → 10:00 UTC = 07:00 BRT; antes de opens_at 09:00 BRT.
        Instant early = Instant.parse("2026-07-01T10:00:00Z");
        assertThatThrownBy(() -> service.create(COMPANY, profCarla, offeringCorte, null, null, early, "C", null, null))
            .isInstanceOf(OutsideHoursException.class);
    }

    @Test
    @DisplayName("create com profissional inativo → InactiveProfessionalException (400)")
    void create_inactiveProfessional() {
        jdbcTemplate.update("update salon_professionals set active = false where id = ?", profCarla);
        assertThatThrownBy(() -> service.create(COMPANY, profCarla, offeringCorte, null, null, START, "C", null, null))
            .isInstanceOf(InactiveProfessionalException.class);
    }

    @Test
    @DisplayName("conflito MESMO PROFISSIONAL (Carla, mesmo horário) → ConflictException (409)")
    void create_conflictSameProfessional() {
        seedWithCarla();   // Carla 12:00–12:45 BRT
        assertThatThrownBy(() -> service.create(COMPANY, profCarla, offeringCorte, null, null, START, "Outro", null, null))
            .isInstanceOf(ConflictException.class);
    }

    @Test
    @DisplayName("MESMO HORÁRIO, PROFISSIONAL DIFERENTE (Patrícia) → OK (paralelismo, chave da SM)")
    void create_sameSlotDifferentProfessional() {
        seedWithCarla();   // Carla 12:00 BRT
        // Patrícia no MESMO horário não conflita (conflito é por profissional).
        assertThatCode(() -> service.create(COMPANY, profPatricia, offeringCorte, null, null, START, "Outra", null, null))
            .doesNotThrowAnyException();
        Long count = jdbcTemplate.queryForObject("select count(*) from salon_appointments where company_id = ?",
            Long.class, COMPANY);
        assertThat(count).isEqualTo(2L);   // ambos criados no mesmo horário.
    }

    @Test
    @DisplayName("updateStatus agendado→confirmado → notifica com nome do profissional")
    void confirm_notifies() {
        SalonAppointment a = seedWithCarla();
        SalonAppointment confirmed = service.updateStatus(COMPANY, a.id(), "confirmado");
        assertThat(confirmed.status()).isEqualTo("confirmado");
        assertThat(fakeEvolution.sent()).hasSize(1);
        assertThat(fakeEvolution.sent().get(0).text()).contains("confirmado").contains("Carla");
    }

    record SentMessage(String instanceName, String token, String number, String text) {}

    static class FakeEvolutionSender implements EvolutionSender {
        private final List<SentMessage> sent = new CopyOnWriteArrayList<>();
        void reset() { sent.clear(); }
        List<SentMessage> sent() { return sent; }
        @Override
        public String sendText(String instanceName, String token, String number, String text) {
            sent.add(new SentMessage(instanceName, token, number, text));
            return "key-salon";
        }
    }

    @TestConfiguration
    static class TestConfig {
        @Bean
        @Primary
        FakeEvolutionSender fakeEvolutionSender() {
            return new FakeEvolutionSender();
        }
    }
}
