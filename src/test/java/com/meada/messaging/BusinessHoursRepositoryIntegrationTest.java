package com.meada.messaging;

import com.meada.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class BusinessHoursRepositoryIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private BusinessHoursRepository repository;

    private static final UUID COMPANY_A = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID COMPANY_B = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");

    @BeforeEach
    void seedCompanies() {
        jdbcTemplate.update("insert into companies (id, name, slug) values (?, ?, ?)",
            COMPANY_A, "Empresa A", "empresa-a");
        jdbcTemplate.update("insert into companies (id, name, slug) values (?, ?, ?)",
            COMPANY_B, "Empresa B", "empresa-b");
    }

    private void insertWindow(UUID company, int weekday, String opens, String closes) {
        jdbcTemplate.update(
            "insert into business_hours (company_id, weekday, closed, opens_at, closes_at) "
                + "values (?, ?, false, ?::time, ?::time)",
            company, weekday, opens, closes);
    }

    private void insertClosed(UUID company, int weekday) {
        jdbcTemplate.update(
            "insert into business_hours (company_id, weekday, closed, opens_at, closes_at) "
                + "values (?, ?, true, null, null)",
            company, weekday);
    }

    @Test
    @DisplayName("retorna horários do tenant ordenados por weekday,opens_at; múltiplas janelas no dia; não vaza outro tenant")
    void findByCompany_happyPath_multiWindowOrderingIsolation() {
        // weekday 1 (segunda) com duas janelas (manhã + tarde, pausa de almoço)
        insertWindow(COMPANY_A, 1, "14:00", "18:00");   // inserida fora de ordem de propósito
        insertWindow(COMPANY_A, 1, "09:00", "12:00");
        insertWindow(COMPANY_A, 2, "09:00", "18:00");   // terça
        insertWindow(COMPANY_B, 1, "08:00", "17:00");   // outro tenant

        List<BusinessHours> result = repository.findByCompany(COMPANY_A);

        // 3 janelas de A (2 de segunda + 1 de terça), ordenadas weekday,opens_at; sem B
        assertThat(result).hasSize(3);
        assertThat(result.get(0).weekday()).isEqualTo(1);
        assertThat(result.get(0).opensAt()).isEqualTo(LocalTime.of(9, 0));   // 09:00 antes de 14:00
        assertThat(result.get(1).weekday()).isEqualTo(1);
        assertThat(result.get(1).opensAt()).isEqualTo(LocalTime.of(14, 0));
        assertThat(result.get(2).weekday()).isEqualTo(2);
    }

    @Test
    @DisplayName("dia fechado (closed=true) retorna com horas null")
    void findByCompany_closedDay_nullHours() {
        insertClosed(COMPANY_A, 0);   // domingo fechado

        List<BusinessHours> result = repository.findByCompany(COMPANY_A);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).weekday()).isEqualTo(0);
        assertThat(result.get(0).closed()).isTrue();
        assertThat(result.get(0).opensAt()).isNull();
        assertThat(result.get(0).closesAt()).isNull();
    }

    @Test
    @DisplayName("tenant sem horários → lista vazia")
    void findByCompany_empty() {
        assertThat(repository.findByCompany(COMPANY_A)).isEmpty();
    }
}
