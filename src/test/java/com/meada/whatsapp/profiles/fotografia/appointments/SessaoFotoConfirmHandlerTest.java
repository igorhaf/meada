package com.meada.whatsapp.profiles.fotografia.appointments;

import com.meada.whatsapp.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testa o SessaoFotoConfirmHandler (camada 8.16): parse OK + create com snapshots (cliente do
 * contato, pacote/profissional do catálogo, PREÇO DA IA DESCARTADO), professional/pacote inválido →
 * empty, conflito → empty, sem tag → empty. UM MODO só (sem new_patient). Clone do
 * AgendamentoDermaConfirmHandlerTest.
 */
class SessaoFotoConfirmHandlerTest extends AbstractIntegrationTest {

    @Autowired
    private SessaoFotoConfirmHandler handler;

    private static final UUID COMPANY = UUID.fromString("f0000000-0000-0000-0000-000000000005");
    private UUID conversationId;
    private UUID contactId;
    private UUID profId;
    private UUID pkgId;

    @BeforeEach
    void seed() {
        jdbcTemplate.update("insert into companies (id, name, slug, profile_id) values (?, ?, ?, 'fotografia')",
            COMPANY, "Foto H", "foto-h");
        UUID instance = UUID.randomUUID();
        contactId = UUID.randomUUID();
        conversationId = UUID.randomUUID();
        jdbcTemplate.update("insert into whatsapp_instances (id, company_id, instance_name, evolution_token) values (?, ?, ?, ?)",
            instance, COMPANY, "inst", "tok");
        jdbcTemplate.update("insert into contacts (id, company_id, phone_number, name) values (?, ?, ?, ?)",
            contactId, COMPANY, "+5511999990295", "Marina");
        jdbcTemplate.update("insert into conversations (id, company_id, contact_id, whatsapp_instance_id, status, handled_by) "
            + "values (?, ?, ?, ?, 'open', 'ai')", conversationId, COMPANY, contactId, instance);
        profId = UUID.randomUUID();
        jdbcTemplate.update("insert into fotografia_professionals (id, company_id, name) values (?, ?, 'Carla')", profId, COMPANY);
        pkgId = UUID.randomUUID();
        jdbcTemplate.update("insert into fotografia_packages (id, company_id, name, duration_minutes, price_cents, delivery_days) "
            + "values (?, ?, 'Ensaio 1h', 60, 50000, 7)", pkgId, COMPANY);
    }

    @Test
    @DisplayName("tag válida → cria sessão agendada com snapshots; preço emitido pela IA é DESCARTADO")
    void parseAndCreate_valid() {
        // a IA emite um price_cents bobo de propósito — o backend deve ignorar e snapshotar 50000 do catálogo.
        String aiText = "Perfeito, Marina! Agendei seu ensaio com a Carla pra 01/07 às 11h.\n"
            + "<sessao_foto>{\"professional_id\":\"" + profId + "\",\"package_id\":\"" + pkgId
            + "\",\"date\":\"2026-07-01\",\"start_time\":\"11:00\",\"price_cents\":999,\"notes\":\"\"}</sessao_foto>";

        Optional<FotografiaSessionAppointment> a = handler.parseAndCreate(COMPANY, conversationId, contactId, aiText);

        assertThat(a).isPresent();
        assertThat(a.get().status()).isEqualTo("agendada");
        assertThat(a.get().professionalName()).isEqualTo("Carla");
        assertThat(a.get().customerName()).isEqualTo("Marina");
        assertThat(a.get().packageName()).isEqualTo("Ensaio 1h");
        // PREÇO DA IA DESCARTADO — vale o do catálogo.
        assertThat(a.get().priceCents()).isEqualTo(50000);
        assertThat(a.get().durationMinutes()).isEqualTo(60);
    }

    @Test
    @DisplayName("professional_id inexistente na tag → Optional.empty (não criado)")
    void parseAndCreate_invalidProfessional() {
        String aiText = "Agendado!\n<sessao_foto>{\"professional_id\":\"" + UUID.randomUUID()
            + "\",\"package_id\":\"" + pkgId + "\",\"date\":\"2026-07-01\",\"start_time\":\"11:00\"}</sessao_foto>";
        Optional<FotografiaSessionAppointment> a = handler.parseAndCreate(COMPANY, conversationId, contactId, aiText);
        assertThat(a).isEmpty();
        Long count = jdbcTemplate.queryForObject("select count(*) from fotografia_session_appointments", Long.class);
        assertThat(count).isZero();
    }

    @Test
    @DisplayName("package_id inexistente na tag → Optional.empty (não criado)")
    void parseAndCreate_invalidPackage() {
        String aiText = "Agendado!\n<sessao_foto>{\"professional_id\":\"" + profId
            + "\",\"package_id\":\"" + UUID.randomUUID() + "\",\"date\":\"2026-07-01\",\"start_time\":\"11:00\"}</sessao_foto>";
        Optional<FotografiaSessionAppointment> a = handler.parseAndCreate(COMPANY, conversationId, contactId, aiText);
        assertThat(a).isEmpty();
    }

    @Test
    @DisplayName("conflito no slot do profissional → Optional.empty (não criado)")
    void parseAndCreate_conflict() {
        java.time.Instant start = java.time.Instant.parse("2026-07-01T14:00:00Z"); // 11:00 BRT
        jdbcTemplate.update(
            "insert into fotografia_session_appointments (company_id, professional_id, package_id, contact_id, "
                + "customer_name, professional_name, package_name, price_cents, duration_minutes, delivery_days, "
                + "start_at, end_at, delivery_due_date, status) "
                + "values (?, ?, ?, ?, 'Outro', 'Carla', 'Ensaio 1h', 50000, 60, 7, ?, ?, ?, 'agendada')",
            COMPANY, profId, pkgId, contactId, java.sql.Timestamp.from(start),
            java.sql.Timestamp.from(start.plusSeconds(3600)), java.sql.Date.valueOf("2026-07-08"));

        String aiText = "Agendado!\n<sessao_foto>{\"professional_id\":\"" + profId + "\",\"package_id\":\"" + pkgId
            + "\",\"date\":\"2026-07-01\",\"start_time\":\"11:00\"}</sessao_foto>";
        Optional<FotografiaSessionAppointment> a = handler.parseAndCreate(COMPANY, conversationId, contactId, aiText);
        assertThat(a).isEmpty();
    }

    @Test
    @DisplayName("texto sem tag → Optional.empty (conversa normal)")
    void parseAndCreate_noTag() {
        Optional<FotografiaSessionAppointment> a = handler.parseAndCreate(
            COMPANY, conversationId, contactId, "Oi! Quer marcar um ensaio fotográfico?");
        assertThat(a).isEmpty();
    }
}
