package com.meada.whatsapp.profiles.barbearia.queue;

import com.meada.whatsapp.AbstractIntegrationTest;
import com.meada.whatsapp.outbound.EvolutionSender;
import com.meada.whatsapp.profiles.barbearia.queue.BarberQueueService.InvalidStatusTransitionException;
import com.meada.whatsapp.profiles.barbearia.queue.BarberQueueService.QueueDisabledException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Testa o BarberQueueService (camada 8.1) — A ESCAPADA DESTA SM: a POSIÇÃO DERIVADA da fila de
 * walk-in. Prova que:
 * <ul>
 *   <li>3 tickets na mesma fila → posições 1,2,3 na ordem de enqueued_at;</li>
 *   <li>atender o 1º → o ex-2 vira 1, o ex-3 vira 2 (RECOMPUTA sem UPDATE de reordenação);</li>
 *   <li>desistir no meio → recomputa idem;</li>
 *   <li>regra de escopo "qualquer barbeiro" (geral vs específico);</li>
 *   <li>queue_enabled=false → enqueue → QueueDisabled;</li>
 *   <li>transição inválida → InvalidStatusTransition;</li>
 *   <li>aguardando→chamado grava called_at + notifica.</li>
 * </ul>
 */
@Import(BarberQueueServiceTest.TestConfig.class)
class BarberQueueServiceTest extends AbstractIntegrationTest {

    @Autowired
    private BarberQueueService service;
    @Autowired
    private FakeEvolutionSender fakeEvolution;

    private static final UUID COMPANY = UUID.fromString("cb000000-0000-0000-0000-000000000004");
    private UUID barberMarcelo;
    private UUID serviceCorte;
    private UUID contactId;
    private UUID conversationId;

    @BeforeEach
    void seed() {
        fakeEvolution.reset();
        jdbcTemplate.update("insert into companies (id, name, slug, profile_id) values (?, ?, ?, 'barbearia')",
            COMPANY, "Barbearia Q", "barbearia-q");
        // config com a fila LIGADA (default true, mas seedamos explícito).
        jdbcTemplate.update("insert into barber_config (company_id, queue_enabled) values (?, true)", COMPANY);
        barberMarcelo = UUID.randomUUID();
        jdbcTemplate.update("insert into barber_barbers (id, company_id, name) values (?, ?, 'Marcelo')",
            barberMarcelo, COMPANY);
        serviceCorte = UUID.randomUUID();
        jdbcTemplate.update("insert into barber_services (id, company_id, name, duration_minutes) "
            + "values (?, ?, 'Corte', 30)", serviceCorte, COMPANY);
        UUID instance = UUID.randomUUID();
        contactId = UUID.randomUUID();
        conversationId = UUID.randomUUID();
        jdbcTemplate.update("insert into whatsapp_instances (id, company_id, instance_name, evolution_token) values (?, ?, ?, ?)",
            instance, COMPANY, "inst", "tok");
        jdbcTemplate.update("insert into contacts (id, company_id, phone_number, name) values (?, ?, ?, ?)",
            contactId, COMPANY, "+5511999990070", "Cliente");
        jdbcTemplate.update("insert into conversations (id, company_id, contact_id, whatsapp_instance_id, status, handled_by) "
            + "values (?, ?, ?, ?, 'open', 'ai')", conversationId, COMPANY, contactId, instance);
    }

    /** Insere um ticket 'aguardando' diretamente com enqueued_at controlado (pro escalonamento). */
    private UUID seedTicket(UUID barberId, Instant enqueuedAt, String guest) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
            "insert into barber_queue_tickets (id, company_id, barber_id, service_id, service_name, "
                + "duration_minutes, guest_name, enqueued_at) values (?, ?, ?, ?, 'Corte', 30, ?, ?)",
            id, COMPANY, barberId, serviceCorte, guest, Timestamp.from(enqueuedAt));
        return id;
    }

    @Test
    @DisplayName("3 tickets na fila do Marcelo → posições derivadas 1,2,3 na ordem de enqueued_at")
    void positions_1_2_3() {
        Instant base = Instant.parse("2026-07-01T12:00:00Z");
        UUID t1 = seedTicket(barberMarcelo, base, "A");
        UUID t2 = seedTicket(barberMarcelo, base.plusSeconds(60), "B");
        UUID t3 = seedTicket(barberMarcelo, base.plusSeconds(120), "C");

        assertThat(service.get(COMPANY, t1).orElseThrow().position()).isEqualTo(1);
        assertThat(service.get(COMPANY, t2).orElseThrow().position()).isEqualTo(2);
        assertThat(service.get(COMPANY, t3).orElseThrow().position()).isEqualTo(3);
    }

    @Test
    @DisplayName("atender o ticket 1 → o ex-2 vira 1, o ex-3 vira 2 (RECOMPUTA sem reorder) — D2")
    void atendido_recomputesPositions() {
        Instant base = Instant.parse("2026-07-01T12:00:00Z");
        UUID t1 = seedTicket(barberMarcelo, base, "A");
        UUID t2 = seedTicket(barberMarcelo, base.plusSeconds(60), "B");
        UUID t3 = seedTicket(barberMarcelo, base.plusSeconds(120), "C");

        // chama o 1 e marca atendido (aguardando→chamado→atendido).
        service.updateStatus(COMPANY, t1, "chamado");
        service.updateStatus(COMPANY, t1, "atendido");

        // sem nenhum UPDATE de posição: a query recomputa.
        assertThat(service.get(COMPANY, t2).orElseThrow().position()).isEqualTo(1);
        assertThat(service.get(COMPANY, t3).orElseThrow().position()).isEqualTo(2);
    }

    @Test
    @DisplayName("desistir do ticket do meio → recomputa: o de trás sobe")
    void desistiu_recomputesPositions() {
        Instant base = Instant.parse("2026-07-01T12:00:00Z");
        UUID t1 = seedTicket(barberMarcelo, base, "A");
        UUID t2 = seedTicket(barberMarcelo, base.plusSeconds(60), "B");
        UUID t3 = seedTicket(barberMarcelo, base.plusSeconds(120), "C");

        service.updateStatus(COMPANY, t2, "desistiu");   // o do meio sai

        assertThat(service.get(COMPANY, t1).orElseThrow().position()).isEqualTo(1);
        assertThat(service.get(COMPANY, t3).orElseThrow().position()).isEqualTo(2);   // subiu de 3 p/ 2
    }

    @Test
    @DisplayName("escopo 'qualquer barbeiro': ticket geral conta TODOS à frente; específico conta dele + gerais à frente")
    void scope_generalVsSpecific() {
        Instant base = Instant.parse("2026-07-01T12:00:00Z");
        // 1º: geral (qualquer barbeiro).
        UUID tGeral = seedTicket(null, base, "Geral");
        // 2º: específico do Marcelo (depois do geral).
        UUID tMarcelo = seedTicket(barberMarcelo, base.plusSeconds(60), "Marcelo-cli");

        // O geral é o 1 (ninguém à frente). O do Marcelo: o geral à frente PODE pegar o Marcelo → posição 2.
        assertThat(service.get(COMPANY, tGeral).orElseThrow().position()).isEqualTo(1);
        assertThat(service.get(COMPANY, tMarcelo).orElseThrow().position()).isEqualTo(2);
    }

    @Test
    @DisplayName("queue_enabled=false → enqueue → QueueDisabledException")
    void enqueue_disabled() {
        jdbcTemplate.update("update barber_config set queue_enabled = false where company_id = ?", COMPANY);
        assertThatThrownBy(() -> service.enqueue(COMPANY, barberMarcelo, serviceCorte, null, null, "X", null, null))
            .isInstanceOf(QueueDisabledException.class);
    }

    @Test
    @DisplayName("enqueue válido → ticket aguardando com posição + ETA derivados")
    void enqueue_ok() {
        // já há 1 ticket de 30min à frente — enqueued no PASSADO real (now()-1min) p/ ficar à frente
        // do ticket que service.enqueue vai criar com now().
        UUID ahead = UUID.randomUUID();
        jdbcTemplate.update(
            "insert into barber_queue_tickets (id, company_id, barber_id, service_id, service_name, "
                + "duration_minutes, guest_name, enqueued_at) "
                + "values (?, ?, ?, ?, 'Corte', 30, 'A', now() - interval '1 minute')",
            ahead, COMPANY, barberMarcelo, serviceCorte);
        BarberQueueTicket t = service.enqueue(COMPANY, barberMarcelo, serviceCorte, contactId, conversationId, "Novo", null, null);
        assertThat(t.status()).isEqualTo("aguardando");
        assertThat(t.position()).isEqualTo(2);          // atrás do seedado
        assertThat(t.etaMinutes()).isEqualTo(30);       // 30min do que está à frente
    }

    @Test
    @DisplayName("transição inválida do ticket (atendido→aguardando) → InvalidStatusTransitionException")
    void invalidTransition() {
        UUID t = seedTicket(barberMarcelo, Instant.parse("2026-07-01T12:00:00Z"), "A");
        service.updateStatus(COMPANY, t, "chamado");
        service.updateStatus(COMPANY, t, "atendido");
        assertThatThrownBy(() -> service.updateStatus(COMPANY, t, "aguardando"))
            .isInstanceOf(InvalidStatusTransitionException.class);
    }

    @Test
    @DisplayName("aguardando→chamado grava called_at e dispara notificação 'chegou sua vez' com o barbeiro")
    void chamado_notifies() {
        // ticket vinculado à conversa pra notificar.
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
            "insert into barber_queue_tickets (id, company_id, barber_id, barber_name, service_id, "
                + "service_name, duration_minutes, conversation_id, contact_id, guest_name, enqueued_at) "
                + "values (?, ?, ?, 'Marcelo', ?, 'Corte', 30, ?, ?, 'Cliente', now())",
            id, COMPANY, barberMarcelo, serviceCorte, conversationId, contactId);

        BarberQueueTicket called = service.updateStatus(COMPANY, id, "chamado");
        assertThat(called.status()).isEqualTo("chamado");
        assertThat(called.calledAt()).isNotNull();
        assertThat(fakeEvolution.sent()).hasSize(1);
        assertThat(fakeEvolution.sent().get(0).text()).contains("vez").contains("Marcelo");
    }

    record SentMessage(String instanceName, String token, String number, String text) {}

    static class FakeEvolutionSender implements EvolutionSender {
        private final List<SentMessage> sent = new CopyOnWriteArrayList<>();
        void reset() { sent.clear(); }
        List<SentMessage> sent() { return sent; }
        @Override
        public String sendText(String instanceName, String token, String number, String text) {
            sent.add(new SentMessage(instanceName, token, number, text));
            return "key-queue";
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
