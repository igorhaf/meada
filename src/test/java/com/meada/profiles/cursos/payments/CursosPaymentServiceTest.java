package com.meada.profiles.cursos.payments;

import com.meada.AbstractIntegrationTest;
import com.meada.profiles.cursos.payments.CursosPaymentService.DuplicatePaymentException;
import com.meada.profiles.cursos.payments.CursosPaymentService.EnrollmentNotActiveException;
import com.meada.profiles.cursos.payments.CursosPaymentService.PaymentSummary;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Testa o CursosPaymentService (camada 8.20 / perfil cursos): record + summary, duplicate → 409,
 * pagamento em matrícula cancelada → 400. Clone do AcademiaPaymentServiceTest (camada 7.7).
 */
class CursosPaymentServiceTest extends AbstractIntegrationTest {

    @Autowired
    private CursosPaymentService service;

    private static final UUID COMPANY = UUID.fromString("cc100000-0000-0000-0000-000000000004");
    private static final UUID USER = UUID.fromString("dc100000-0000-0000-0000-000000000004");
    private UUID enrollment;

    @BeforeEach
    void seed() {
        jdbcTemplate.update("insert into companies (id, name, slug, profile_id) values (?, ?, ?, 'cursos')",
            COMPANY, "Curso P", "curso-p");
        jdbcTemplate.update("insert into auth.users (id) values (?) on conflict (id) do nothing", USER);
        jdbcTemplate.update("insert into users (id, company_id, email, role) values (?, ?, 'u@cur-p.dev', 'admin')",
            USER, COMPANY);
        UUID course = UUID.randomUUID();
        jdbcTemplate.update("insert into cursos_courses (id, company_id, title, monthly_cents) values (?, ?, 'Inglês', 15000)",
            course, COMPANY);
        enrollment = UUID.randomUUID();
        jdbcTemplate.update("insert into cursos_enrollments (id, company_id, course_id, student_name, course_title, "
            + "course_monthly_cents, start_date, status) values (?, ?, ?, 'Aluno', 'Inglês', 15000, current_date, 'ativa')",
            enrollment, COMPANY, course);
    }

    @Test
    @DisplayName("record + summary: registra pagamento e resume último mês/em aberto")
    void recordAndSummary() {
        LocalDate ref = LocalDate.now().withDayOfMonth(1);
        CursosPayment p = service.record(COMPANY, USER, enrollment, ref, 15000, "Pix", null);
        assertThat(p.amountCents()).isEqualTo(15000);
        assertThat(p.referenceMonth()).isEqualTo(ref);

        PaymentSummary s = service.summary(COMPANY, enrollment, LocalDate.now());
        assertThat(s.totalPayments()).isEqualTo(1);
        assertThat(s.lastPaidMonth()).isEqualTo(ref);
        assertThat(s.monthsOpen()).isEqualTo(0);   // 1 mês decorrido, 1 pago.
    }

    @Test
    @DisplayName("record duplicado mesmo reference_month → DuplicatePaymentException (409)")
    void duplicate() {
        LocalDate ref = LocalDate.now().withDayOfMonth(1);
        service.record(COMPANY, USER, enrollment, ref, 15000, "Pix", null);
        assertThatThrownBy(() -> service.record(COMPANY, USER, enrollment, ref, 15000, "dinheiro", null))
            .isInstanceOf(DuplicatePaymentException.class);
    }

    @Test
    @DisplayName("record em matrícula cancelada → EnrollmentNotActiveException (400)")
    void cancelledEnrollment() {
        jdbcTemplate.update("update cursos_enrollments set status = 'cancelada', end_date = current_date where id = ?", enrollment);
        LocalDate ref = LocalDate.now().withDayOfMonth(1);
        assertThatThrownBy(() -> service.record(COMPANY, USER, enrollment, ref, 15000, "Pix", null))
            .isInstanceOf(EnrollmentNotActiveException.class);
    }
}
