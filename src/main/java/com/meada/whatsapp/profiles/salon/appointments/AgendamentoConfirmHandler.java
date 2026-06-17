package com.meada.whatsapp.profiles.salon.appointments;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.meada.whatsapp.messaging.ContactRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extrai a tag {@code <agendamento>{...}</agendamento>} da resposta da IA e cria o agendamento
 * (camada 7.5). Espelho dos confirm handlers anteriores.
 *
 * <p>NÃO usa tool calling / responseSchema (mesma restrição). {@code date}+{@code start_time} →
 * instante no fuso America/Sao_Paulo (hardcoded). O guest_name vem do contact.name (snapshot). O
 * SalonAppointmentService lê a duração do serviço (snapshot) e valida profissional/serviço/janela/
 * conflito. Qualquer falha → {@link Optional#empty()} + warn (a mensagem da IA segue sem agendamento).
 */
@Component
public class AgendamentoConfirmHandler {

    private static final Logger log = LoggerFactory.getLogger(AgendamentoConfirmHandler.class);
    private static final ZoneId TENANT_ZONE = ZoneId.of("America/Sao_Paulo");

    private static final Pattern TAG = Pattern.compile("<agendamento>\\s*(\\{.*?\\})\\s*</agendamento>",
        Pattern.DOTALL);

    private final ObjectMapper objectMapper;
    private final ContactRepository contactRepository;
    private final SalonAppointmentService appointmentService;

    public AgendamentoConfirmHandler(ObjectMapper objectMapper, ContactRepository contactRepository,
                                     SalonAppointmentService appointmentService) {
        this.objectMapper = objectMapper;
        this.contactRepository = contactRepository;
        this.appointmentService = appointmentService;
    }

    public boolean hasAgendamentoTag(String text) {
        return text != null && TAG.matcher(text).find();
    }

    public String stripAgendamentoTag(String text) {
        if (text == null) {
            return null;
        }
        return TAG.matcher(text).replaceAll("").stripTrailing();
    }

    /**
     * Extrai a tag, resolve o contato e cria o agendamento. {@link Optional#empty()} quando: não há
     * tag, JSON inválido, campos faltando, ou a criação falha (profissional/serviço inválido/inativo,
     * fora do horário, conflito).
     */
    public Optional<SalonAppointment> parseAndCreate(UUID companyId, UUID conversationId, UUID contactId,
                                                     String aiResponseText) {
        if (aiResponseText == null) {
            return Optional.empty();
        }
        Matcher m = TAG.matcher(aiResponseText);
        if (!m.find()) {
            return Optional.empty();
        }

        JsonNode root;
        try {
            root = objectMapper.readTree(m.group(1));
        } catch (Exception e) {
            log.warn("salon: tag <agendamento> com JSON inválido p/ conversa {} ({}) — agendamento não criado",
                conversationId, e.getMessage());
            return Optional.empty();
        }

        String rawProf = root.path("professional_id").asText(null);
        String rawService = root.path("service_id").asText(null);
        String date = root.path("date").asText(null);
        String startTime = root.path("start_time").asText(null);
        String notes = root.path("notes").asText(null);
        if (rawProf == null || rawService == null || date == null || startTime == null) {
            log.warn("salon: tag <agendamento> com campos faltando p/ conversa {} — agendamento não criado",
                conversationId);
            return Optional.empty();
        }

        UUID professionalId;
        UUID serviceId;
        Instant startAt;
        try {
            professionalId = UUID.fromString(rawProf);
            serviceId = UUID.fromString(rawService);
            LocalDate d = LocalDate.parse(date);
            LocalTime t = LocalTime.parse(startTime);
            startAt = d.atTime(t).atZone(TENANT_ZONE).toInstant();
        } catch (RuntimeException e) {
            log.warn("salon: tag <agendamento> com ids/data inválidos p/ conversa {} ({}) — agendamento não criado",
                conversationId, e.getMessage());
            return Optional.empty();
        }

        // guest_name/phone: snapshot do contato (a IA só emite tag com cliente na conversa).
        String guestName = contactRepository.findNameByConversationId(conversationId)
            .filter(n -> n != null && !n.isBlank())
            .orElseGet(() -> contactRepository.findPhoneByConversationId(conversationId).orElse("Cliente"));
        String guestPhone = contactRepository.findPhoneByConversationId(conversationId).orElse(null);

        try {
            SalonAppointment a = appointmentService.create(companyId, professionalId, serviceId,
                contactId, conversationId, startAt, guestName, guestPhone, notes);
            log.info("salon: agendamento {} criado p/ conversa {} (prof {}, serviço {})",
                a.id(), conversationId, professionalId, serviceId);
            return Optional.of(a);
        } catch (SalonAppointmentService.ConflictException e) {
            log.warn("salon: <agendamento> conflitou no slot do profissional p/ conversa {} — não criado", conversationId);
            return Optional.empty();
        } catch (SalonAppointmentService.OutsideHoursException e) {
            log.warn("salon: <agendamento> fora do horário p/ conversa {} — não criado", conversationId);
            return Optional.empty();
        } catch (SalonAppointmentService.ProfessionalNotFoundException
                 | SalonAppointmentService.ServiceNotFoundException
                 | SalonAppointmentService.InactiveProfessionalException
                 | SalonAppointmentService.InactiveServiceException e) {
            log.warn("salon: <agendamento> com profissional/serviço inválido ou inativo p/ conversa {} — não criado",
                conversationId);
            return Optional.empty();
        } catch (RuntimeException e) {
            log.warn("salon: falha ao criar agendamento p/ conversa {} ({}) — mensagem segue sem agendamento",
                conversationId, e.getMessage());
            return Optional.empty();
        }
    }
}
