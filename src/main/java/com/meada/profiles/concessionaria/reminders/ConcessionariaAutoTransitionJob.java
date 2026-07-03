package com.meada.profiles.concessionaria.reminders;

import com.meada.admin.health.ScheduledJobRunRepository;
import com.meada.profiles.concessionaria.testdrives.ConcessionariaTestDriveNotifier;
import com.meada.profiles.concessionaria.testdrives.ConcessionariaTestDriveService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Auto-transição + follow-up de lead da concessionária (onda 1 do backlog #9/#2).
 *
 * <p>Periodicamente (cron horário): (a) test-drive 'confirmado' cujo {@code end_at} passou há mais
 * de {@value #GRACE_HOURS}h vira 'realizado' — via {@code ConcessionariaTestDriveService.updateStatus}
 * (transição válida; 'realizado' é SILENCIOSO) — toggle {@code auto_complete_enabled}; (b) lead
 * 'novo'/'em_negociacao' PARADO há followup_days (config) recebe follow-up gentil pelo canal da
 * conversa (texto fixo, defensivo — reengaja SEM fechar preço) — toggle {@code followup_enabled},
 * idempotência re-armável ({@code followup_sent_at} vs {@code status_updated_at}). Sem canal →
 * marca sem enviar.
 */
@Component
public class ConcessionariaAutoTransitionJob {

    private static final Logger log = LoggerFactory.getLogger(ConcessionariaAutoTransitionJob.class);

    private static final int GRACE_HOURS = 2;

    private final ConcessionariaJobsRepository jobsRepository;
    private final ConcessionariaTestDriveService testDriveService;
    private final ConcessionariaTestDriveNotifier notifier;
    private final ScheduledJobRunRepository jobRunRepository;

    public ConcessionariaAutoTransitionJob(ConcessionariaJobsRepository jobsRepository,
                                           ConcessionariaTestDriveService testDriveService,
                                           ConcessionariaTestDriveNotifier notifier,
                                           ScheduledJobRunRepository jobRunRepository) {
        this.jobsRepository = jobsRepository;
        this.testDriveService = testDriveService;
        this.notifier = notifier;
        this.jobRunRepository = jobRunRepository;
    }

    /** Tick agendado (cron configurável; default de hora em hora, aos 25min). */
    @Scheduled(cron = "${concessionaria.auto-transition-cron:0 25 * * * *}")
    public void scheduledRun() {
        var runId = jobRunRepository.start("ConcessionariaAutoTransitionJob");
        try {
            runAutoTransitions();
            jobRunRepository.finishSuccess(runId);
        } catch (RuntimeException e) {
            jobRunRepository.finishFailed(runId, e.getMessage());
            throw e;
        }
    }

    /**
     * Aplica auto-realizado (#9) + follow-up de lead parado (#2) em todos os tenants concessionaria.
     * Público e direto para os testes.
     *
     * @return número de test-drives realizados + leads followupados neste run
     */
    public int runAutoTransitions() {
        int touched = 0;

        // (a) test-drive confirmado com end_at passado (graça) → realizado (silencioso).
        Instant cutoff = Instant.now().minus(Duration.ofHours(GRACE_HOURS));
        List<ConcessionariaJobsRepository.CompletableTestDrive> completable =
            jobsRepository.findCompletableTestDrives(cutoff);
        for (ConcessionariaJobsRepository.CompletableTestDrive t : completable) {
            try {
                testDriveService.updateStatus(t.companyId(), t.testDriveId(), "realizado");
                touched++;
            } catch (RuntimeException e) {
                log.warn("concessionaria-auto: realizar test-drive {} falhou ({})",
                    t.testDriveId(), e.getMessage());
            }
        }

        // (b) follow-up de lead parado (reengaja sem fechar preço — trava preservada).
        List<ConcessionariaJobsRepository.StaleLead> stale = jobsRepository.findStaleLeads();
        for (ConcessionariaJobsRepository.StaleLead l : stale) {
            try {
                if (l.conversationId() == null) {
                    log.info("concessionaria-followup: lead {} sem canal — marcado sem envio", l.leadId());
                } else {
                    notifier.notifyStatus(l.companyId(), l.conversationId(),
                        "Oi, " + l.customerName() + "! Ainda pensando no " + l.vehicleBrand() + " "
                            + l.vehicleModel() + "? Posso agendar um test-drive ou tirar qualquer dúvida "
                            + "com a equipe — sem compromisso. 🚗");
                }
                jobsRepository.markLeadFollowedUp(l.leadId());
                touched++;
            } catch (Exception e) {
                log.warn("concessionaria-followup: failed lead {} ({})", l.leadId(), e.getMessage());
            }
        }
        return touched;
    }
}
