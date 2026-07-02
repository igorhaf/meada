package com.meada.profiles.atelie.reminders;

import com.meada.admin.health.ScheduledJobRunRepository;
import com.meada.profiles.atelie.proposals.AtelieProposalNotifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Lembrete automático de prova/ajuste do Ateliê (onda do backlog docs/FEATURES_SUGERIDAS_ATELIE.md
 * #1). Cada prova furada empurra a entrega da peça inteira — este job fecha o maior vazamento
 * operacional do nicho lembrando o cliente NA VÉSPERA.
 *
 * <p>Diariamente (cron configurável), varre as {@code atelie_fittings} 'pendente' com
 * {@code due_date} = AMANHÃ (America/Sao_Paulo), do perfil atelie, com o lembrete LIGADO na config
 * (default ligado) e cuja proposta segue viva, e dispara uma mensagem outbound FIXA e defensiva pelo
 * canal da proposta ({@link AtelieProposalNotifier} — resolve telefone+credenciais pela conversa e
 * persiste em {@code messages}). NÃO passa pela IA (trava do nicho: nada de prometer prazo/medida).
 *
 * <p>Idempotência por (prova, data): {@code reminded_due_date} guarda qual due_date já foi lembrado —
 * remarcar a prova pra outra data REARMA o lembrete (espelho do {@code overdue_notified_month} da
 * academia). Sem canal resolúvel (proposta manual, sem conversa) → marca mesmo assim, evitando
 * revarredura eterna (padrão ReminderJob/ReactivationJob). {@code EVOLUTION_DRY_RUN} em dev é honrado
 * pelo EvolutionSender por baixo do notifier (lição do incidente Baileys).
 *
 * <p>Segue o MOLDE dos jobs existentes: método {@code @Scheduled} fino que só instrumenta via
 * {@link ScheduledJobRunRepository}; a lógica real fica em {@link #runFittingReminders()} público,
 * que os testes chamam direto (sem depender do scheduler).
 */
@Component
public class AtelieFittingReminderJob {

    private static final Logger log = LoggerFactory.getLogger(AtelieFittingReminderJob.class);

    private static final ZoneId TENANT_ZONE = ZoneId.of("America/Sao_Paulo");
    private static final DateTimeFormatter DAY_MONTH = DateTimeFormatter.ofPattern("dd/MM");

    private final AtelieFittingReminderRepository reminderRepository;
    private final AtelieProposalNotifier notifier;
    private final ScheduledJobRunRepository jobRunRepository;

    public AtelieFittingReminderJob(AtelieFittingReminderRepository reminderRepository,
                                    AtelieProposalNotifier notifier,
                                    ScheduledJobRunRepository jobRunRepository) {
        this.reminderRepository = reminderRepository;
        this.notifier = notifier;
        this.jobRunRepository = jobRunRepository;
    }

    /** Tick agendado (cron configurável; default diário às 9h). Delega ao método público para os testes. */
    @Scheduled(cron = "${atelie.fitting-reminder-cron:0 0 9 * * *}")
    public void scheduledRun() {
        var runId = jobRunRepository.start("AtelieFittingReminderJob");
        try {
            runFittingReminders();
            jobRunRepository.finishSuccess(runId);
        } catch (RuntimeException e) {
            jobRunRepository.finishFailed(runId, e.getMessage());
            throw e;
        }
    }

    /**
     * Lembra as provas/ajustes de AMANHÃ em todos os tenants atelie. Público e direto para os testes
     * exercitarem a lógica sem o scheduler.
     *
     * @return número de provas marcadas como lembradas neste run (com ou sem canal)
     */
    public int runFittingReminders() {
        LocalDate tomorrow = LocalDate.now(TENANT_ZONE).plusDays(1);
        List<DueFitting> due = reminderRepository.findDueFittings(tomorrow);
        int touched = 0;
        for (DueFitting f : due) {
            try {
                if (f.conversationId() == null) {
                    log.info("atelie-reminder: prova {} sem canal (proposta manual) — marcada sem envio",
                        f.fittingId());
                } else {
                    // best-effort: falha de envio loga no notifier e NÃO impede a marcação (igual academia).
                    notifier.notifyStatus(f.companyId(), f.conversationId(), buildReminderText(f));
                }
                reminderRepository.markReminded(f.fittingId(), f.dueDate());
                touched++;
            } catch (Exception e) {
                log.warn("atelie-reminder: failed fitting {} ({})", f.fittingId(), e.getMessage());
            }
        }
        return touched;
    }

    /** Texto fixo e defensivo — sem promessa de prazo/medida/resultado (trava do nicho). */
    private static String buildReminderText(DueFitting f) {
        return "Olá, " + f.customerName() + "! Lembrete do ateliê: \"" + f.title()
            + "\" está previsto para amanhã (" + DAY_MONTH.format(f.dueDate())
            + "). Podemos confirmar sua presença? Qualquer imprevisto, é só avisar por aqui. 🧵";
    }
}
