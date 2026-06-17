package com.meada.whatsapp.profiles.restaurant.tables;

import com.meada.whatsapp.AbstractIntegrationTest;
import com.meada.whatsapp.profiles.restaurant.tables.RestaurantTableService.LabelInUseException;
import com.meada.whatsapp.profiles.restaurant.tables.RestaurantTableService.TableInUseException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Testa o RestaurantTableService (camada 7.3): create + audit, toggle, delete em uso → 409.
 */
class RestaurantTableServiceTest extends AbstractIntegrationTest {

    @Autowired
    private RestaurantTableService service;

    private static final UUID COMPANY = UUID.fromString("c8000000-0000-0000-0000-000000000001");
    private static final UUID USER = UUID.fromString("d8000000-0000-0000-0000-000000000001");

    @BeforeEach
    void seed() {
        jdbcTemplate.update("insert into companies (id, name, slug, profile_id) values (?, ?, ?, 'restaurant')",
            COMPANY, "Restaurante Teste", "restaurante-teste");
        // USER em users (FK audit_log_user_id_fkey) — ver nota nos testes do sushi.
        jdbcTemplate.update("insert into auth.users (id) values (?) on conflict (id) do nothing", USER);
        jdbcTemplate.update("insert into users (id, company_id, email, role) values (?, ?, 'u@rest.dev', 'admin')",
            USER, COMPANY);
    }

    @Test
    @DisplayName("create válido → persiste + audita restaurant_table_created")
    void create_persistsAndAudits() {
        RestaurantTable t = service.create(COMPANY, USER, "Mesa 1", 4, "perto da janela");
        assertThat(t.label()).isEqualTo("Mesa 1");
        assertThat(t.capacity()).isEqualTo(4);
        assertThat(t.available()).isTrue();

        Long audit = jdbcTemplate.queryForObject(
            "select count(*) from audit_log where action = 'restaurant_table_created' and entity_id = ?",
            Long.class, t.id());
        assertThat(audit).isEqualTo(1L);
    }

    @Test
    @DisplayName("create com label duplicado → LabelInUseException (409)")
    void create_duplicateLabel() {
        service.create(COMPANY, USER, "Mesa 1", 2, null);
        assertThatThrownBy(() -> service.create(COMPANY, USER, "Mesa 1", 4, null))
            .isInstanceOf(LabelInUseException.class);
    }

    @Test
    @DisplayName("toggle desliga available")
    void toggle() {
        RestaurantTable t = service.create(COMPANY, USER, "Varanda 1", 8, null);
        RestaurantTable off = service.toggle(COMPANY, USER, t.id(), false);
        assertThat(off.available()).isFalse();
    }

    @Test
    @DisplayName("delete de mesa com reserva → TableInUseException (409)")
    void delete_inUse() {
        RestaurantTable t = service.create(COMPANY, USER, "Mesa 9", 2, null);
        // Semeia uma reserva referenciando a mesa (FK restrict).
        Instant start = Instant.parse("2026-07-01T20:00:00Z");
        jdbcTemplate.update(
            "insert into table_reservations (company_id, table_id, guest_name, start_at, duration_minutes, end_at, num_people) "
                + "values (?, ?, 'Cliente', ?, 120, ?, 2)",
            COMPANY, t.id(), java.sql.Timestamp.from(start),
            java.sql.Timestamp.from(start.plusSeconds(7200)));

        assertThatThrownBy(() -> service.delete(COMPANY, USER, t.id()))
            .isInstanceOf(TableInUseException.class);
    }
}
