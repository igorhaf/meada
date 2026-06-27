package com.meada.profiles.salon;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.meada.profiles.salon.appointments.SalonAppointment;
import com.meada.profiles.salon.appointments.SalonAppointmentRepository;
import com.meada.profiles.salon.config.SalonConfig;
import com.meada.profiles.salon.config.SalonConfigRepository;
import com.meada.profiles.salon.offerings.SalonOffering;
import com.meada.profiles.salon.offerings.SalonOfferingRepository;
import com.meada.profiles.salon.professionals.SalonProfessional;
import com.meada.profiles.salon.professionals.SalonProfessionalRepository;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Cache do bloco de contexto dinâmico injetado no prompt do SalãoBot (camada 7.5).
 *
 * <p>TTL 20s — entre o restaurant (15s) e o dental (30s): a agenda do salão tem múltiplos
 * profissionais e atualiza com frequência intermediária. Keyed por {@code (companyId, contactId)}.
 * Os services de profissional/serviço/agendamento/config chamam {@link #invalidate} (por company).
 *
 * <p>Conteúdo: serviços ativos (com duração/categoria/preço-se-houver) + profissionais ativos +
 * histórico do contato (se identificado) + slots livres POR PROFISSIONAL nos próximos 7 dias +
 * instruções de agendamento. Fuso America/Sao_Paulo (hardcoded).
 */
@Component
public class SalonContextCache {

    private static final ZoneId TENANT_ZONE = ZoneId.of("America/Sao_Paulo");
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM");
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");
    private static final int CONTEXT_DAYS = 7;
    private static final int SLOT_GRANULARITY_MIN = 15;   // decisão 4: slot de 15min.
    private static final int MAX_SLOTS_PER_PROF_DAY = 6;  // limita a lista no prompt.
    private static final int HISTORY_LIMIT = 5;

    private final SalonProfessionalRepository professionalRepository;
    private final SalonOfferingRepository offeringRepository;
    private final SalonAppointmentRepository appointmentRepository;
    private final SalonConfigRepository configRepository;
    private final Cache<String, String> cache;

    public SalonContextCache(SalonProfessionalRepository professionalRepository,
                             SalonOfferingRepository offeringRepository,
                             SalonAppointmentRepository appointmentRepository,
                             SalonConfigRepository configRepository) {
        this.professionalRepository = professionalRepository;
        this.offeringRepository = offeringRepository;
        this.appointmentRepository = appointmentRepository;
        this.configRepository = configRepository;
        this.cache = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofSeconds(20))
            .maximumSize(1000)
            .build();
    }

    public String contextSegment(UUID companyId, UUID contactId) {
        String key = companyId + ":" + (contactId == null ? "none" : contactId.toString());
        return cache.get(key, k -> buildSegment(companyId, contactId));
    }

    /** Invalida todas as entradas de uma empresa (mutação de profissional/serviço/agendamento/config). */
    public void invalidate(UUID companyId) {
        String prefix = companyId + ":";
        cache.asMap().keySet().removeIf(k -> k.startsWith(prefix));
    }

    private String buildSegment(UUID companyId, UUID contactId) {
        SalonConfig config = configRepository.findByCompany(companyId);
        List<SalonOffering> offerings = offeringRepository.listByCompany(companyId, true);
        List<SalonProfessional> pros = professionalRepository.listByCompany(companyId, true);

        StringBuilder sb = new StringBuilder();

        // --- SERVIÇOS ---
        if (offerings.isEmpty()) {
            sb.append("SERVIÇOS DO SALÃO: (nenhum serviço ativo no momento.)\n\n");
        } else {
            sb.append("SERVIÇOS DO SALÃO (use o service_id EXATO na tag):\n");
            for (SalonOffering o : offerings) {
                sb.append("- ").append(o.id()).append(" · ").append(o.name())
                    .append(": ").append(o.durationMinutes()).append("min");
                if (o.priceCents() != null) {
                    sb.append(" (R$ ").append(formatBrl(o.priceCents())).append(")");
                }
                if (o.category() != null && !o.category().isBlank()) {
                    sb.append(" [").append(o.category()).append("]");
                }
                sb.append("\n");
            }
            sb.append("\n");
        }

        // --- PROFISSIONAIS ---
        if (pros.isEmpty()) {
            sb.append("PROFISSIONAIS: (nenhum profissional ativo.)\n\n");
        } else {
            sb.append("PROFISSIONAIS (use o professional_id EXATO na tag):\n");
            for (SalonProfessional p : pros) {
                sb.append("- ").append(p.id()).append(" · ").append(p.name());
                if (p.specialty() != null && !p.specialty().isBlank()) {
                    sb.append(" (").append(p.specialty()).append(")");
                }
                sb.append("\n");
            }
            sb.append("\n");
        }

        // --- HISTÓRICO DO CLIENTE (se identificado) ---
        if (contactId != null) {
            List<SalonAppointment> history = appointmentRepository.listByContact(companyId, contactId, HISTORY_LIMIT);
            if (!history.isEmpty()) {
                sb.append("HISTÓRICO DO CLIENTE:\n");
                for (SalonAppointment a : history) {
                    ZonedDateTime z = a.startAt().atZone(TENANT_ZONE);
                    sb.append("- ").append(DATE_FMT.format(z)).append(": ").append(a.serviceName())
                        .append(" com ").append(a.professionalName()).append("\n");
                }
                sb.append("\n");
            } else {
                sb.append("HISTÓRICO DO CLIENTE: primeira vez (sem agendamentos anteriores).\n\n");
            }
        } else {
            sb.append("CLIENTE NÃO IDENTIFICADO pelo telefone. Peça o nome para registrar o "
                + "agendamento.\n\n");
        }

        // --- SLOTS LIVRES POR PROFISSIONAL ---
        sb.append(buildSlotsSegment(companyId, config, pros, offerings));

        // --- PERSONA + INSTRUÇÕES (decisão 6) ---
        sb.append("INSTRUÇÕES DE AGENDAMENTO:\n")
            .append("Quando o cliente PEDIR agendamento, sugira profissionais disponíveis pro serviço "
                + "pedido (se houver mais de 1). NUNCA recomende serviço que o cliente não pediu. NUNCA "
                + "opine sobre a aparência do cliente, sem promessa de resultado estético. Confirme "
                + "serviço + profissional + dia + hora. Quando tudo estiver definido, sua ÚLTIMA "
                + "mensagem deve TERMINAR com a tag (em uma linha própria, sem markdown):\n")
            .append("<agendamento>{\"professional_id\":\"UUID\",\"service_id\":\"UUID\","
                + "\"date\":\"YYYY-MM-DD\",\"start_time\":\"HH:MM\",\"notes\":\"...\"}</agendamento>\n")
            .append("Use os ids EXATOS das listas acima. Só emita a tag na confirmação final.\n\n");

        return sb.toString();
    }

    /**
     * Slots livres por profissional nos próximos {@value #CONTEXT_DAYS} dias. Para cada profissional,
     * para cada dia, gera slots de {@value #SLOT_GRANULARITY_MIN}min entre opens/closes e remove os
     * ocupados por agendamentos ATIVOS daquele profissional. Lista resumida.
     */
    private String buildSlotsSegment(UUID companyId, SalonConfig config, List<SalonProfessional> pros,
                                     List<SalonOffering> offerings) {
        StringBuilder sb = new StringBuilder("HORÁRIOS LIVRES (próximos ")
            .append(CONTEXT_DAYS).append(" dias, por profissional):\n");
        if (pros.isEmpty() || offerings.isEmpty()) {
            sb.append("(sem profissionais ou serviços ativos — não há disponibilidade.)\n\n");
            return sb.toString();
        }
        Instant now = Instant.now();
        Instant until = now.plus(Duration.ofDays(CONTEXT_DAYS));
        ZonedDateTime startDay = now.atZone(TENANT_ZONE).toLocalDate().atStartOfDay(TENANT_ZONE);

        for (SalonProfessional p : pros) {
            List<SalonAppointment> active =
                appointmentRepository.listActiveByProfessional(companyId, p.id(), now, until);
            List<String> dayChunks = new ArrayList<>();
            for (int d = 0; d < CONTEXT_DAYS && dayChunks.size() < CONTEXT_DAYS; d++) {
                LocalDate day = startDay.plusDays(d).toLocalDate();
                List<String> free = new ArrayList<>();
                LocalTime t = config.opensAt();
                while (t.plusMinutes(SLOT_GRANULARITY_MIN).compareTo(config.closesAt()) <= 0
                        && free.size() < MAX_SLOTS_PER_PROF_DAY) {
                    ZonedDateTime slotStart = day.atTime(t).atZone(TENANT_ZONE);
                    ZonedDateTime slotEnd = slotStart.plusMinutes(SLOT_GRANULARITY_MIN);
                    boolean inFuture = slotStart.toInstant().isAfter(now);
                    boolean occupied = active.stream().anyMatch(a ->
                        !(a.endAt().compareTo(slotStart.toInstant()) <= 0
                            || a.startAt().compareTo(slotEnd.toInstant()) >= 0));
                    if (inFuture && !occupied) {
                        free.add(TIME_FMT.format(t));
                    }
                    t = t.plusMinutes(SLOT_GRANULARITY_MIN);
                }
                if (!free.isEmpty()) {
                    dayChunks.add(DATE_FMT.format(day.atStartOfDay(TENANT_ZONE)) + " " + String.join(", ", free));
                }
            }
            if (!dayChunks.isEmpty()) {
                sb.append("- ").append(p.name()).append(": ")
                    .append(String.join(" | ", dayChunks)).append("\n");
            }
        }
        sb.append("\n");
        return sb.toString();
    }

    private static String formatBrl(int cents) {
        return String.format("%d,%02d", cents / 100, cents % 100);
    }
}
