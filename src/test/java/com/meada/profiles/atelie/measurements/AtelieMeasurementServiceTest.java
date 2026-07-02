package com.meada.profiles.atelie.measurements;

import com.meada.AbstractIntegrationTest;
import com.meada.profiles.atelie.measurements.AtelieMeasurementService.ContactNotFoundException;
import com.meada.profiles.atelie.measurements.AtelieMeasurementService.InvalidMeasurementException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Testa o AtelieMeasurementService (onda 2, backlog #9): upsert por (contato, lower(label)) —
 * regravar a mesma medida ATUALIZA o valor (reuso na recompra); contato de outro tenant →
 * contact_not_found; label/value inválidos → invalid_measurement.
 */
class AtelieMeasurementServiceTest extends AbstractIntegrationTest {

    @Autowired
    private AtelieMeasurementService service;

    private static final UUID COMPANY = UUID.fromString("a7e00000-0000-0000-0000-000000000084");
    private static final UUID OTHER_COMPANY = UUID.fromString("a7e00000-0000-0000-0000-000000000085");
    private static final UUID USER = UUID.fromString("d7e00000-0000-0000-0000-000000000084");

    private UUID contactId;
    private UUID otherTenantContactId;

    @BeforeEach
    void seed() {
        jdbcTemplate.update("insert into companies (id, name, slug, profile_id) values (?, ?, ?, 'atelie')",
            COMPANY, "Atelie Med", "atelie-med");
        jdbcTemplate.update("insert into companies (id, name, slug, profile_id) values (?, ?, ?, 'atelie')",
            OTHER_COMPANY, "Atelie Med 2", "atelie-med-2");
        // USER em auth.users + users (FK audit_log_user_id_fkey) — lição AuditLogger.
        jdbcTemplate.update("insert into auth.users (id) values (?) on conflict (id) do nothing", USER);
        jdbcTemplate.update("insert into users (id, company_id, email, role) values (?, ?, 'u@atelie-med.dev', 'admin')",
            USER, COMPANY);
        contactId = UUID.randomUUID();
        otherTenantContactId = UUID.randomUUID();
        jdbcTemplate.update("insert into contacts (id, company_id, phone_number, name) values (?, ?, ?, 'Marina')",
            contactId, COMPANY, "+5511999990182");
        jdbcTemplate.update("insert into contacts (id, company_id, phone_number, name) values (?, ?, ?, 'Outro')",
            otherTenantContactId, OTHER_COMPANY, "+5511999990183");
    }

    @Test
    @DisplayName("upsert cria; regravar a MESMA label (case-insensitive) atualiza o valor, não duplica")
    void upsert_updatesSameLabel() {
        service.upsert(COMPANY, USER, contactId, "Busto", "90 cm");
        service.upsert(COMPANY, USER, contactId, "Cintura", "70 cm");
        AtelieMeasurement updated = service.upsert(COMPANY, USER, contactId, "busto", "92 cm");

        assertThat(updated.value()).isEqualTo("92 cm");
        List<AtelieMeasurement> all = service.list(COMPANY, contactId);
        assertThat(all).hasSize(2);
        assertThat(all).extracting(AtelieMeasurement::value).contains("92 cm", "70 cm");
    }

    @Test
    @DisplayName("contato de OUTRO tenant (ou inexistente) → ContactNotFoundException")
    void crossTenantContact_notFound() {
        assertThatThrownBy(() -> service.upsert(COMPANY, USER, otherTenantContactId, "Busto", "90"))
            .isInstanceOf(ContactNotFoundException.class);
        assertThatThrownBy(() -> service.list(COMPANY, UUID.randomUUID()))
            .isInstanceOf(ContactNotFoundException.class);
    }

    @Test
    @DisplayName("label/value vazios → InvalidMeasurementException; delete remove")
    void invalidAndDelete() {
        assertThatThrownBy(() -> service.upsert(COMPANY, USER, contactId, " ", "90"))
            .isInstanceOf(InvalidMeasurementException.class);
        assertThatThrownBy(() -> service.upsert(COMPANY, USER, contactId, "Busto", " "))
            .isInstanceOf(InvalidMeasurementException.class);

        AtelieMeasurement m = service.upsert(COMPANY, USER, contactId, "Manga", "60 cm");
        service.delete(COMPANY, USER, m.id());
        assertThat(service.list(COMPANY, contactId)).isEmpty();
    }
}
