package com.meada.profiles.casamento.payments;

import com.meada.AbstractIntegrationTest;
import com.meada.profiles.casamento.payments.WeddingPaymentService.InvalidPaymentException;
import com.meada.profiles.casamento.payments.WeddingPaymentService.ProposalLockedException;
import com.meada.profiles.casamento.proposals.WeddingProposalService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Testa o plano de pagamento do casamento (onda 1, backlog #1): CRUD + setPaid com paid_at; GATE do
 * fechamento (aprovada→fechada com 'sinal' não pago → deposit_required; pago → fecha; sem sinal no
 * plano → livre); plano segue mutável após fechada; recusada/cancelada travam.
 */
class WeddingPaymentServiceTest extends AbstractIntegrationTest {

    @Autowired
    private WeddingPaymentService service;
    @Autowired
    private WeddingProposalService proposalService;

    private static final UUID COMPANY = UUID.fromString("cf000000-0000-0000-0000-000000000084");
    private static final UUID USER = UUID.fromString("df000000-0000-0000-0000-000000000084");

    @BeforeEach
    void seed() {
        jdbcTemplate.update("insert into companies (id, name, slug, profile_id) values (?, ?, ?, 'casamento')",
            COMPANY, "Casamento Pay", "casamento-pay");
        // USER em auth.users + users (FK audit_log_user_id_fkey) — lição AuditLogger.
        jdbcTemplate.update("insert into auth.users (id) values (?) on conflict (id) do nothing", USER);
        jdbcTemplate.update("insert into users (id, company_id, email, role) values (?, ?, 'u@cas-pay.dev', 'admin')",
            USER, COMPANY);
    }

    /** Proposta APROVADA (rascunho→orcada→aprovada) com 1 item de orçamento, sem conversa. */
    private UUID approvedProposal() {
        UUID id = proposalService.open(COMPANY, null, "Ana & João", null, null, "clássico",
            LocalDate.now().plusMonths(6), 100, "briefing", null).id();
        proposalService.addItem(COMPANY, id, "Assessoria completa", 1, 1000000);
        proposalService.updateStatus(COMPANY, id, "orcada");
        proposalService.updateStatus(COMPANY, id, "aprovada");
        return id;
    }

    @Test
    @DisplayName("GATE: aprovada→fechada com sinal NÃO pago → deposit_required; pago → fecha")
    void gate_depositRequired() {
        UUID proposal = approvedProposal();
        WeddingPayment sinal = service.create(COMPANY, USER, proposal, "sinal", "Sinal",
            LocalDate.now().plusDays(7), 300000);
        assertThat(sinal.paid()).isFalse();

        assertThatThrownBy(() -> proposalService.updateStatus(COMPANY, proposal, "fechada"))
            .isInstanceOf(WeddingProposalService.DepositRequiredException.class);

        WeddingPayment pago = service.setPaid(COMPANY, USER, proposal, sinal.id(), true);
        assertThat(pago.paid()).isTrue();
        assertThat(pago.paidAt()).isNotNull();
        assertThat(proposalService.updateStatus(COMPANY, proposal, "fechada").status()).isEqualTo("fechada");
    }

    @Test
    @DisplayName("sem SINAL no plano (só parcelas ou plano vazio) → fechamento livre")
    void gate_freeWithoutSinal() {
        UUID semPlano = approvedProposal();
        assertThat(proposalService.updateStatus(COMPANY, semPlano, "fechada").status()).isEqualTo("fechada");

        UUID soParcelas = approvedProposal();
        service.create(COMPANY, USER, soParcelas, "parcela", "Parcela 1",
            LocalDate.now().plusMonths(1), 100000);
        assertThat(proposalService.updateStatus(COMPANY, soParcelas, "fechada").status()).isEqualTo("fechada");
    }

    @Test
    @DisplayName("plano segue MUTÁVEL depois de fechada (parcelas vencem até o casamento)")
    void payments_editableAfterClosed() {
        UUID proposal = approvedProposal();
        proposalService.updateStatus(COMPANY, proposal, "fechada");

        WeddingPayment parcela = service.create(COMPANY, USER, proposal, "parcela", "Parcela 2",
            LocalDate.now().plusMonths(2), 200000);
        assertThat(service.list(COMPANY, proposal)).hasSize(1);
        service.setPaid(COMPANY, USER, proposal, parcela.id(), true);
        assertThat(service.list(COMPANY, proposal).get(0).paid()).isTrue();
    }

    @Test
    @DisplayName("proposta CANCELADA trava o plano → ProposalLockedException; valor <= 0 → invalid")
    void locked_andInvalid() {
        UUID proposal = approvedProposal();
        assertThatThrownBy(() -> service.create(COMPANY, USER, proposal, "sinal", null,
            LocalDate.now(), 0)).isInstanceOf(InvalidPaymentException.class);

        proposalService.updateStatus(COMPANY, proposal, "cancelada");
        assertThatThrownBy(() -> service.create(COMPANY, USER, proposal, "sinal", null,
            LocalDate.now(), 100)).isInstanceOf(ProposalLockedException.class);
    }
}
