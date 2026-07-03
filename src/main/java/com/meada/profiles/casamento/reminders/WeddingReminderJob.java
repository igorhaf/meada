package com.meada.profiles.casamento.reminders;

import com.meada.admin.health.ScheduledJobRunRepository;
import com.meada.profiles.casamento.payments.WeddingPaymentRepository;
import com.meada.profiles.casamento.proposals.WeddingProposalNotifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Lembretes automáticos do casamento (onda 1 do backlog docs/FEATURES_SUGERIDAS_CASAMENTO.md #2):
 * o due_date do checklist e o vencimento das parcelas deixam de ser decorativos.
 *
 * <p>Diariamente (cron configurável), varre: (a) tarefas do CHECKLIST não concluídas com prazo nos
 * próximos {@value #WINDOW_DAYS} dias (D-3) e (b) PARCELAS/SINAIS não pagos vencendo na mesma
 * janela — de propostas VIVAS de tenants casamento com os toggles ligados — e dispara mensagem
 * outbound FIXA e defensiva pelo canal da proposta ({@link WeddingProposalNotifier}). NÃO passa
 * pela IA (trava do nicho: nada de prometer/negociar).
 *
 * <p>Idempotência por (linha, data): {@code reminded_due_date} guarda qual due_date já foi
 * lembrado — REMARCAR o prazo/vencimento rearma o lembrete (espelho ateliê mig 81 / academia mig
 * 72). Sem canal resolúvel (proposta manual) → marca mesmo assim (não revarre).
 * {@code EVOLUTION_DRY_RUN} em dev é honrado pelo EvolutionSender por baixo do notifier.
 *
 * <p>Molde dos jobs existentes: {@code @Scheduled} fino instrumentado por
 * {@link ScheduledJobRunRepository}; lógica pública testável sem o scheduler.
 */
@Component
public class WeddingReminderJob {

    private static final Logger log = LoggerFactory.getLogger(WeddingReminderJob.class);

    private static final ZoneId TENANT_ZONE = ZoneId.of("America/Sao_Paulo");
    private static final DateTimeFormatter DAY_MONTH = DateTimeFormatter.ofPattern("dd/MM");
    private static final int WINDOW_DAYS = 3;   // D-3 (ou depois, se o job esteve fora do ar)

    private final WeddingReminderRepository reminderRepository;
    private final WeddingPaymentRepository paymentRepository;
    private final WeddingProposalNotifier notifier;
    private final ScheduledJobRunRepository jobRunRepository;

    public WeddingReminderJob(WeddingReminderRepository reminderRepository,
                              WeddingPaymentRepository paymentRepository,
                              WeddingProposalNotifier notifier,
                              ScheduledJobRunRepository jobRunRepository) {
        this.reminderRepository = reminderRepository;
        this.paymentRepository = paymentRepository;
        this.notifier = notifier;
        this.jobRunRepository = jobRunRepository;
    }

    /** Tick agendado (cron configurável; default diário às 9h30). Delega ao método público p/ testes. */
    @Scheduled(cron = "${casamento.reminder-cron:0 30 9 * * *}")
    public void scheduledRun() {
        var runId = jobRunRepository.start("WeddingReminderJob");
        try {
            runReminders();
            jobRunRepository.finishSuccess(runId);
        } catch (RuntimeException e) {
            jobRunRepository.finishFailed(runId, e.getMessage());
            throw e;
        }
    }

    /**
     * Lembra tarefas do checklist e parcelas vencendo em até {@value #WINDOW_DAYS} dias, em todos os
     * tenants casamento. Público e direto para os testes.
     *
     * @return número de linhas marcadas como lembradas neste run (com ou sem canal)
     */
    public int runReminders() {
        LocalDate windowEnd = LocalDate.now(TENANT_ZONE).plusDays(WINDOW_DAYS);
        int touched = 0;

        List<WeddingReminderRepository.DueChecklistTask> tasks =
            reminderRepository.findDueChecklistTasks(windowEnd);
        for (WeddingReminderRepository.DueChecklistTask t : tasks) {
            try {
                if (t.conversationId() == null) {
                    log.info("casamento-reminder: tarefa {} sem canal (proposta manual) — marcada sem envio",
                        t.taskId());
                } else {
                    notifier.notifyStatus(t.companyId(), t.conversationId(),
                        "Lembrete dos preparativos: \"" + t.title() + "\" tem prazo até "
                            + DAY_MONTH.format(t.dueDate()) + ". Qualquer dúvida, é só chamar por aqui! 💍");
                }
                reminderRepository.markTaskReminded(t.taskId(), t.dueDate());
                touched++;
            } catch (Exception e) {
                log.warn("casamento-reminder: failed checklist task {} ({})", t.taskId(), e.getMessage());
            }
        }

        List<WeddingPaymentRepository.DuePayment> payments = paymentRepository.findDuePayments(windowEnd);
        for (WeddingPaymentRepository.DuePayment p : payments) {
            try {
                if (p.conversationId() == null) {
                    log.info("casamento-reminder: parcela {} sem canal (proposta manual) — marcada sem envio",
                        p.paymentId());
                } else {
                    String rotulo = p.label() != null && !p.label().isBlank()
                        ? p.label()
                        : ("sinal".equals(p.kind()) ? "o sinal" : "a parcela");
                    notifier.notifyStatus(p.companyId(), p.conversationId(),
                        "Lembrete: " + rotulo + " de " + brl(p.amountCents()) + " vence em "
                            + DAY_MONTH.format(p.dueDate())
                            + ". Se já acertou com a equipe, pode desconsiderar. 🤍");
                }
                paymentRepository.markReminded(p.paymentId(), p.dueDate());
                touched++;
            } catch (Exception e) {
                log.warn("casamento-reminder: failed payment {} ({})", p.paymentId(), e.getMessage());
            }
        }
        return touched;
    }

    private static String brl(int cents) {
        return "R$ " + String.format("%d,%02d", cents / 100, cents % 100);
    }
}
