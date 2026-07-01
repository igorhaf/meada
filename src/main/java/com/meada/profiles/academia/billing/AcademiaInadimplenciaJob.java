package com.meada.profiles.academia.billing;

import com.meada.admin.health.ScheduledJobRunRepository;
import com.meada.messaging.ContactRepository;
import com.meada.messaging.ConversationRepository;
import com.meada.messaging.EvolutionCredentials;
import com.meada.messaging.WhatsappInstanceRepository;
import com.meada.outbound.EvolutionSender;
import com.meada.profiles.academia.memberships.AcademiaMembershipService;
import com.meada.profiles.academia.payments.AcademiaPaymentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Régua de inadimplência da Academia (Onda 2 / piloto — backlog docs/FEATURES_SUGERIDAS_ACADEMIA.md).
 *
 * <p>Diariamente (cron configurável), para cada tenant academia com política em {@code academia_config},
 * varre as matrículas ATIVAS, calcula os meses em aberto (reusando {@link AcademiaPaymentService#summary})
 * e:
 * <ul>
 *   <li>envia UM lembrete de vencimento por mês de referência (idempotência via
 *       {@code overdue_notified_month}), respeitando os dias de tolerância (grace_days);
 *   <li>se o tenant ligou {@code auto_suspend_days} e o atraso passou do limite, SUSPENDE a matrícula
 *       (via {@link AcademiaMembershipService#updateStatus} — transição validada + notificação do nicho).
 * </ul>
 *
 * <p>Segue o MOLDE dos jobs existentes ({@code ReminderJob}/{@code ReactivationJob}): método
 * {@code @Scheduled} fino que só instrumenta via {@link ScheduledJobRunRepository}; a lógica real fica
 * em {@link #runBillingReminders()} público, que os testes chamam direto (sem depender do scheduler).
 * Envio pelo {@link EvolutionSender}, que HONRA {@code EVOLUTION_DRY_RUN} em dev (lição do incidente
 * Baileys). A COBRANÇA real (link Pix/cartão) espera o gateway #50 — aqui só o lembrete + a suspensão
 * local, independentes do gateway.
 *
 * <p>Suspensa MANTÉM a vaga (regra cravada da 7.7); só o cancelamento libera. Este job nunca cancela.
 */
@Component
public class AcademiaInadimplenciaJob {

    private static final Logger log = LoggerFactory.getLogger(AcademiaInadimplenciaJob.class);

    private static final ZoneId TENANT_ZONE = ZoneId.of("America/Sao_Paulo");

    private final AcademiaBillingRepository billingRepository;
    private final AcademiaPaymentService paymentService;
    private final AcademiaMembershipService membershipService;
    private final ConversationRepository conversationRepository;
    private final ContactRepository contactRepository;
    private final WhatsappInstanceRepository whatsappInstanceRepository;
    private final EvolutionSender evolutionSender;
    private final ScheduledJobRunRepository jobRunRepository;

    public AcademiaInadimplenciaJob(AcademiaBillingRepository billingRepository,
                                    AcademiaPaymentService paymentService,
                                    AcademiaMembershipService membershipService,
                                    ConversationRepository conversationRepository,
                                    ContactRepository contactRepository,
                                    WhatsappInstanceRepository whatsappInstanceRepository,
                                    EvolutionSender evolutionSender,
                                    ScheduledJobRunRepository jobRunRepository) {
        this.billingRepository = billingRepository;
        this.paymentService = paymentService;
        this.membershipService = membershipService;
        this.conversationRepository = conversationRepository;
        this.contactRepository = contactRepository;
        this.whatsappInstanceRepository = whatsappInstanceRepository;
        this.evolutionSender = evolutionSender;
        this.jobRunRepository = jobRunRepository;
    }

    /** Tick agendado (cron configurável; default diário às 10h). Delega ao método público para os testes. */
    @Scheduled(cron = "${academia.billing-cron:0 0 10 * * *}")
    public void scheduledRun() {
        UUID runId = jobRunRepository.start("AcademiaInadimplenciaJob");
        try {
            runBillingReminders();
            jobRunRepository.finishSuccess(runId);
        } catch (RuntimeException e) {
            jobRunRepository.finishFailed(runId, e.getMessage());
            throw e;
        }
    }

    /**
     * Varre todos os tenants com política de cobrança e aplica a régua. Público e direto para os
     * testes exercitarem a lógica sem o scheduler.
     *
     * @return número de matrículas que receberam lembrete OU foram suspensas neste run
     */
    public int runBillingReminders() {
        List<BillingPolicy> policies = billingRepository.findBillingPolicies();
        int total = 0;
        for (BillingPolicy policy : policies) {
            try {
                total += processCompany(policy);
            } catch (Exception e) {
                log.warn("academia-billing: failed for company {} ({})", policy.companyId(), e.getMessage());
            }
        }
        return total;
    }

    /** Aplica a régua às matrículas ativas de UM tenant. Retorna quantas foram tocadas. */
    private int processCompany(BillingPolicy policy) {
        List<DueMembership> actives = billingRepository.findActiveMemberships(policy.companyId());
        LocalDate today = LocalDate.now(TENANT_ZONE);
        int touched = 0;
        for (DueMembership m : actives) {
            try {
                if (processOne(policy, m, today)) {
                    touched++;
                }
            } catch (Exception e) {
                log.warn("academia-billing: failed membership {} ({})", m.membershipId(), e.getMessage());
            }
        }
        return touched;
    }

    /**
     * Decide, para uma matrícula, se envia lembrete e/ou suspende. Retorna true se algo foi feito.
     *
     * <p>Meses em aberto vêm do {@link AcademiaPaymentService#summary} (fonte única). Sem meses em
     * aberto → nada a fazer. Com meses em aberto, só age depois dos dias de tolerância do MÊS CORRENTE.
     */
    private boolean processOne(BillingPolicy policy, DueMembership m, LocalDate today) {
        AcademiaPaymentService.PaymentSummary summary =
            paymentService.summary(m.companyId(), m.membershipId(), m.startDate());
        if (summary.monthsOpen() <= 0) {
            return false;   // em dia
        }
        // Só considera atraso depois de grace_days no mês corrente (evita cobrar no dia 01).
        LocalDate referenceMonth = today.withDayOfMonth(1);
        if (today.getDayOfMonth() <= policy.graceDays()) {
            // Ainda dentro da tolerância deste mês: só age se houver atraso de mês ANTERIOR
            // (monthsOpen conta o mês corrente; 2+ meses em aberto = já havia atraso antes).
            if (summary.monthsOpen() < 2) {
                return false;
            }
        }

        boolean acted = false;

        // 1) Suspensão automática (se o tenant ligou e o atraso passou do limite).
        if (policy.autoSuspendDays() != null) {
            long daysOverdue = (long) (summary.monthsOpen() - 1) * 30L + Math.max(0, today.getDayOfMonth() - policy.graceDays());
            if (daysOverdue >= policy.autoSuspendDays()) {
                try {
                    membershipService.updateStatus(m.companyId(), m.membershipId(), "suspensa");
                    log.info("academia-billing: suspended membership {} ({} months open)",
                        m.membershipId(), summary.monthsOpen());
                    // suspendeu → o updateStatus já notifica pelo nicho; não manda lembrete duplicado.
                    return true;
                } catch (RuntimeException e) {
                    log.warn("academia-billing: suspend failed for membership {} ({})",
                        m.membershipId(), e.getMessage());
                }
            }
        }

        // 2) Lembrete de vencimento (1x por mês de referência), se ligado.
        if (policy.reminderEnabled() && !referenceMonth.equals(m.overdueNotifiedMonth())) {
            String text = buildReminderText(m, summary);
            sendAndMark(m, referenceMonth, text);
            acted = true;
        }
        return acted;
    }

    private String buildReminderText(DueMembership m, AcademiaPaymentService.PaymentSummary summary) {
        String valor = String.format("R$ %.2f", m.planMonthlyCents() / 100.0).replace('.', ',');
        String meses = summary.monthsOpen() == 1 ? "1 mensalidade" : summary.monthsOpen() + " mensalidades";
        return "Olá, " + m.studentName() + "! Passando para lembrar que você tem " + meses
            + " em aberto (mensalidade de " + valor + "). Fale com a gente para regularizar e seguir treinando. 💪";
    }

    /**
     * Resolve o canal (telefone + credenciais via a conversa) e envia o lembrete; depois marca o mês
     * notificado. Canal irresolúvel → log + marca mesmo assim (evita revarredura eterna — igual ao
     * ReminderJob/ReactivationJob). EVOLUTION_DRY_RUN é honrado pelo EvolutionSender.
     */
    private void sendAndMark(DueMembership m, LocalDate referenceMonth, String text) {
        UUID conversationId = m.conversationId();
        Optional<EvolutionCredentials> creds = Optional.empty();
        Optional<String> phone = Optional.empty();
        if (conversationId != null) {
            phone = contactRepository.findPhoneByConversationId(conversationId);
            Optional<UUID> instanceId = conversationRepository.findInstanceIdByConversation(conversationId);
            creds = instanceId.isPresent()
                ? whatsappInstanceRepository.findEvolutionCredentials(instanceId.get())
                : Optional.empty();
        }
        if (phone.isEmpty() || phone.get().isBlank() || creds.isEmpty()) {
            log.info("academia-billing: membership {} sem canal resolúvel — lembrete marcado sem envio",
                m.membershipId());
            billingRepository.markOverdueNotified(m.membershipId(), referenceMonth);
            return;
        }
        try {
            evolutionSender.sendText(creds.get().instanceName(), creds.get().token(), phone.get(), text);
            log.info("academia-billing: sent billing reminder for membership {}", m.membershipId());
        } catch (RuntimeException e) {
            log.warn("academia-billing: send failed for membership {} ({}) — marking anyway",
                m.membershipId(), e.getMessage());
        }
        billingRepository.markOverdueNotified(m.membershipId(), referenceMonth);
    }
}
