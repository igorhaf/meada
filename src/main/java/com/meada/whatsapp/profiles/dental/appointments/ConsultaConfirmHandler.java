package com.meada.whatsapp.profiles.dental.appointments;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.meada.whatsapp.profiles.dental.patients.DentalPatient;
import com.meada.whatsapp.profiles.dental.patients.DentalPatientRepository;
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
 * Extrai a tag {@code <consulta>{...}</consulta>} da resposta da IA, resolve o paciente pelo contato
 * e cria a consulta (camada 7.4). Espelho do ReservationConfirmHandler do restaurant.
 *
 * <p>NÃO usa tool calling / responseSchema do Gemini (mesma restrição das outras tags). A IA emite
 * em texto livre; parseamos via regex.
 *
 * <p>{@code date}+{@code start_time} → instante no fuso America/Sao_Paulo (hardcoded — pendência).
 * Resolve o contato da conversa → {@code dental_patients.contact_id} → patient_id. Se o paciente NÃO
 * for encontrado (a IA não devia emitir tag sem paciente identificado, mas defensivo), ou o horário
 * conflitar/estiver fora do funcionamento, retorna {@link Optional#empty()} — a mensagem da IA segue
 * normal, SEM consulta criada (loga warn).
 */
@Component
public class ConsultaConfirmHandler {

    private static final Logger log = LoggerFactory.getLogger(ConsultaConfirmHandler.class);
    private static final ZoneId TENANT_ZONE = ZoneId.of("America/Sao_Paulo");

    private static final Pattern TAG = Pattern.compile("<consulta>\\s*(\\{.*?\\})\\s*</consulta>",
        Pattern.DOTALL);

    private final ObjectMapper objectMapper;
    private final DentalPatientRepository patientRepository;
    private final DentalAppointmentService appointmentService;

    public ConsultaConfirmHandler(ObjectMapper objectMapper, DentalPatientRepository patientRepository,
                                  DentalAppointmentService appointmentService) {
        this.objectMapper = objectMapper;
        this.patientRepository = patientRepository;
        this.appointmentService = appointmentService;
    }

    /** True se o texto contém a tag de consulta (decisão rápida sem parsear). */
    public boolean hasConsultaTag(String text) {
        return text != null && TAG.matcher(text).find();
    }

    /** Remove a tag {@code <consulta>...</consulta>} do texto (para não enviá-la ao paciente). */
    public String stripConsultaTag(String text) {
        if (text == null) {
            return null;
        }
        return TAG.matcher(text).replaceAll("").stripTrailing();
    }

    /**
     * Extrai a tag, resolve o paciente pelo contato e cria a consulta. {@link Optional#empty()}
     * quando: não há tag, JSON inválido, campos faltando, paciente não identificado, fora do horário,
     * ou conflito de slot.
     */
    public Optional<DentalAppointment> parseAndCreate(UUID companyId, UUID conversationId,
                                                      UUID contactId, String aiResponseText) {
        if (aiResponseText == null) {
            return Optional.empty();
        }
        Matcher m = TAG.matcher(aiResponseText);
        if (!m.find()) {
            return Optional.empty();   // conversa normal (agendamento ainda em negociação).
        }

        JsonNode root;
        try {
            root = objectMapper.readTree(m.group(1));
        } catch (Exception e) {
            log.warn("dental: tag <consulta> com JSON inválido p/ conversa {} ({}) — consulta não criada",
                conversationId, e.getMessage());
            return Optional.empty();
        }

        String date = root.path("date").asText(null);
        String startTime = root.path("start_time").asText(null);
        String type = root.path("type").asText(null);
        String notes = root.path("notes").asText(null);
        if (date == null || startTime == null || type == null || type.isBlank()) {
            log.warn("dental: tag <consulta> com campos faltando p/ conversa {} — consulta não criada",
                conversationId);
            return Optional.empty();
        }

        // Resolve o paciente pelo contato (a IA só devia emitir a tag com paciente identificado).
        Optional<DentalPatient> patient = patientRepository.findByContactId(companyId, contactId);
        if (patient.isEmpty()) {
            log.warn("dental: tag <consulta> sem paciente identificado (contato {}) p/ conversa {} — consulta não criada",
                contactId, conversationId);
            return Optional.empty();
        }

        Instant startAt;
        try {
            LocalDate d = LocalDate.parse(date);
            LocalTime t = LocalTime.parse(startTime);
            startAt = d.atTime(t).atZone(TENANT_ZONE).toInstant();
        } catch (RuntimeException e) {
            log.warn("dental: tag <consulta> com date/start_time inválido p/ conversa {} ({}) — consulta não criada",
                conversationId, e.getMessage());
            return Optional.empty();
        }

        try {
            DentalAppointment a = appointmentService.create(companyId, patient.get().id(),
                conversationId, startAt, type.strip(), notes);
            log.info("dental: consulta {} criada p/ conversa {} (paciente {}, tipo {})",
                a.id(), conversationId, patient.get().id(), type.strip());
            return Optional.of(a);
        } catch (DentalAppointmentService.ConflictException e) {
            log.warn("dental: <consulta> conflitou no slot p/ conversa {} (IA prometeu mas perdeu a corrida) — consulta não criada",
                conversationId);
            return Optional.empty();
        } catch (DentalAppointmentService.OutsideHoursException e) {
            log.warn("dental: <consulta> fora do horário p/ conversa {} — consulta não criada", conversationId);
            return Optional.empty();
        } catch (RuntimeException e) {
            log.warn("dental: falha ao criar consulta p/ conversa {} ({}) — mensagem segue sem consulta",
                conversationId, e.getMessage());
            return Optional.empty();
        }
    }
}
