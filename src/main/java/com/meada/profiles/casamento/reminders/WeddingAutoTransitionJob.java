package com.meada.profiles.casamento.reminders;

import com.meada.admin.health.ScheduledJobRunRepository;
import com.meada.profiles.casamento.proposals.WeddingProposalNotifier;
import com.meada.profiles.casamento.proposals.WeddingProposalService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/**
 * Auto-transição + aniversário do casamento (onda 1 do backlog #4/#16).
 *
 * <p>Diariamente (cron): (a) proposta FECHADA cujo {@code wedding_date} já passou vira REALIZADA
 * via {@link WeddingProposalService#updateStatus} (transição válida da máquina; 'realizada' é
 * SILENCIOSA — quem casou não recebe aviso burocrático) — toggle {@code auto_complete_enabled};
 * (b) no dia/mês do wedding_date de proposta REALIZADA (1+ ano), parabeniza o casal 1x/ano
 * ({@code anniversary_notified_year}) — toggle {@code anniversary_enabled}. Texto fixo e
 * defensivo, sem oferta agressiva; sem canal → marca sem enviar (não revarre).
 */
@Component
public class WeddingAutoTransitionJob {

    private static final Logger log = LoggerFactory.getLogger(WeddingAutoTransitionJob.class);

    private static final ZoneId TENANT_ZONE = ZoneId.of("America/Sao_Paulo");

    private final WeddingReminderRepository reminderRepository;
    private final WeddingProposalService proposalService;
    private final WeddingProposalNotifier notifier;
    private final ScheduledJobRunRepository jobRunRepository;

    public WeddingAutoTransitionJob(WeddingReminderRepository reminderRepository,
                                    WeddingProposalService proposalService,
                                    WeddingProposalNotifier notifier,
                                    ScheduledJobRunRepository jobRunRepository) {
        this.reminderRepository = reminderRepository;
        this.proposalService = proposalService;
        this.notifier = notifier;
        this.jobRunRepository = jobRunRepository;
    }

    /** Tick agendado (cron configurável; default diário às 8h). Delega ao método público p/ testes. */
    @Scheduled(cron = "${casamento.auto-transition-cron:0 0 8 * * *}")
    public void scheduledRun() {
        var runId = jobRunRepository.start("WeddingAutoTransitionJob");
        try {
            runAutoTransitions();
            jobRunRepository.finishSuccess(runId);
        } catch (RuntimeException e) {
            jobRunRepository.finishFailed(runId, e.getMessage());
            throw e;
        }
    }

    /**
     * Aplica auto-realizada (#4) + aniversário (#16) em todos os tenants casamento. Público e
     * direto para os testes.
     *
     * @return número de propostas tocadas (realizadas + parabenizadas)
     */
    public int runAutoTransitions() {
        LocalDate today = LocalDate.now(TENANT_ZONE);
        int touched = 0;

        // (a) fechada com a festa já acontecida → realizada (silencioso; notifica nada).
        List<WeddingReminderRepository.CompletableProposal> completable =
            reminderRepository.findCompletableProposals(today);
        for (WeddingReminderRepository.CompletableProposal p : completable) {
            try {
                proposalService.updateStatus(p.companyId(), p.proposalId(), "realizada");
                touched++;
            } catch (RuntimeException e) {
                log.warn("casamento-auto: realizar proposta {} falhou ({})", p.proposalId(), e.getMessage());
            }
        }

        // (b) aniversário de casamento — 1 parabéns por ano.
        List<WeddingReminderRepository.AnniversaryProposal> anniversaries =
            reminderRepository.findAnniversaries(today);
        for (WeddingReminderRepository.AnniversaryProposal a : anniversaries) {
            try {
                if (a.conversationId() == null) {
                    log.info("casamento-auto: aniversário da proposta {} sem canal — marcado sem envio",
                        a.proposalId());
                } else {
                    notifier.notifyStatus(a.companyId(), a.conversationId(),
                        "Feliz aniversário de casamento! 🥂 A equipe que cuidou do grande dia de vocês "
                            + "manda um abraço — contem com a gente sempre que quiserem celebrar de novo.");
                }
                reminderRepository.markAnniversaryNotified(a.proposalId(), today.getYear());
                touched++;
            } catch (Exception e) {
                log.warn("casamento-auto: aniversário da proposta {} falhou ({})", a.proposalId(), e.getMessage());
            }
        }
        return touched;
    }
}
