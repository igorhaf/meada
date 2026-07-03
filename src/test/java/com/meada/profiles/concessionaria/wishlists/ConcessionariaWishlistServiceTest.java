package com.meada.profiles.concessionaria.wishlists;

import com.meada.AbstractIntegrationTest;
import com.meada.outbound.EvolutionSender;
import com.meada.profiles.concessionaria.vehicles.ConcessionariaVehicleService;
import com.meada.profiles.concessionaria.wishlists.ConcessionariaWishlistService.InvalidWishlistException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Testa a lista de desejos (onda 1 da concessionária, backlog #1): criação (pelo menos brand OU
 * model), e o ALERTA one-shot — cadastrar um veículo DISPONÍVEL que casa (marca/modelo ILIKE +
 * teto de preço + ano mínimo) dispara a notificação e DESATIVA o desejo; veículo caro demais/de
 * outra marca não dispara; desejo sem canal marca sem enviar.
 */
@Import(ConcessionariaWishlistServiceTest.TestConfig.class)
class ConcessionariaWishlistServiceTest extends AbstractIntegrationTest {

    private static final UUID COMPANY = UUID.fromString("ce000000-0000-0000-0000-000000000086");
    private static final UUID USER = UUID.fromString("de000000-0000-0000-0000-000000000086");
    private static final UUID INSTANCE = UUID.fromString("ce100000-0000-0000-0000-000000000086");

    @Autowired
    private ConcessionariaWishlistService service;
    @Autowired
    private ConcessionariaVehicleService vehicleService;
    @Autowired
    private FakeEvolutionSender fakeEvolution;

    private UUID contactId;
    private UUID conversationId;

    @BeforeEach
    void seed() {
        fakeEvolution.reset();
        jdbcTemplate.update("insert into companies (id, name, slug, profile_id) values (?, ?, ?, 'concessionaria')",
            COMPANY, "Conc W", "conc-w");
        // USER em auth.users + users (FK audit_log_user_id_fkey) — lição AuditLogger.
        jdbcTemplate.update("insert into auth.users (id) values (?) on conflict (id) do nothing", USER);
        jdbcTemplate.update("insert into users (id, company_id, email, role) values (?, ?, 'u@conc-w.dev', 'admin')",
            USER, COMPANY);
        contactId = UUID.randomUUID();
        conversationId = UUID.randomUUID();
        jdbcTemplate.update("insert into whatsapp_instances (id, company_id, instance_name, evolution_token) values (?, ?, ?, ?)",
            INSTANCE, COMPANY, "inst-cw", "tok-cw");
        jdbcTemplate.update("insert into contacts (id, company_id, phone_number, name) values (?, ?, ?, 'Carlos')",
            contactId, COMPANY, "+5511999990186");
        jdbcTemplate.update("insert into conversations (id, company_id, contact_id, whatsapp_instance_id, status, handled_by) "
            + "values (?, ?, ?, ?, 'open', 'ai')", conversationId, COMPANY, contactId, INSTANCE);
    }

    @Test
    @DisplayName("veículo disponível que CASA dispara o alerta + desativa o desejo (one-shot)")
    void matchingVehicle_notifiesAndDeactivates() {
        service.create(COMPANY, null, contactId, conversationId, "Jeep", "Compass", 9000000, 2020, null);

        vehicleService.create(COMPANY, USER, "Jeep", "Compass Longitude", 2022, 30000, 8900000,
            "prata", null, null, null, null, null);

        assertThat(fakeEvolution.sent()).hasSize(1);
        assertThat(fakeEvolution.sent().get(0).text()).contains("Compass");
        List<ConcessionariaWishlist> all = service.list(COMPANY, false);
        assertThat(all).hasSize(1);
        assertThat(all.get(0).active()).isFalse();
        assertThat(all.get(0).notifiedAt()).isNotNull();
        assertThat(all.get(0).notifiedVehicleId()).isNotNull();
    }

    @Test
    @DisplayName("veículo caro demais / de outra marca NÃO dispara; desejo segue ativo")
    void nonMatching_noAlert() {
        service.create(COMPANY, null, contactId, conversationId, "Jeep", "Compass", 9000000, 2020, null);

        vehicleService.create(COMPANY, USER, "Jeep", "Compass Limited", 2023, 10000, 12000000,
            null, null, null, null, null, null);   // acima do teto
        vehicleService.create(COMPANY, USER, "Fiat", "Pulse", 2022, 20000, 8000000,
            null, null, null, null, null, null);   // outra marca/modelo

        assertThat(fakeEvolution.sent()).isEmpty();
        assertThat(service.list(COMPANY, true)).hasSize(1);
    }

    @Test
    @DisplayName("desejo SEM canal (painel) marca sem enviar; sem brand E sem model → invalid")
    void noChannel_andValidation() {
        service.create(COMPANY, USER, contactId, null, null, "HB20", null, null, null);
        vehicleService.create(COMPANY, USER, "Hyundai", "HB20 Sense", 2021, 40000, 7000000,
            null, null, null, null, null, null);

        assertThat(fakeEvolution.sent()).isEmpty();   // sem conversa → sem envio
        assertThat(service.list(COMPANY, false).get(0).active()).isFalse();   // mas marcou (one-shot)

        assertThatThrownBy(() -> service.create(COMPANY, USER, contactId, null, null, null, 100, null, null))
            .isInstanceOf(InvalidWishlistException.class);
    }

    record SentMessage(String instanceName, String token, String number, String text) {}

    static class FakeEvolutionSender implements EvolutionSender {
        private final List<SentMessage> sent = new CopyOnWriteArrayList<>();
        void reset() { sent.clear(); }
        List<SentMessage> sent() { return sent; }
        @Override
        public String sendText(String instanceName, String token, String number, String text) {
            sent.add(new SentMessage(instanceName, token, number, text));
            return "key-conc-wishlist";
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
