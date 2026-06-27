package com.meada.profiles.nutri.appointments;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.meada.profiles.nutri.patients.NutriPatient;
import com.meada.profiles.nutri.patients.NutriPatientService;
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
 * Extrai a tag {@code <consulta_nutri>{...}</consulta_nutri>} da resposta da IA e cria a consulta
 * (camada 8.0). Espelho do confirm handler do pet, com os 2 MODOS:
 *
 * <ul>
 *   <li><b>patient_id</b> existente: agenda direto para um paciente já cadastrado.</li>
 *   <li><b>new_patient</b> {name, goal?}: cadastra o paciente (sub-entidade do contato da conversa) e,
 *       em seguida, agenda — tudo no mesmo turno.</li>
 * </ul>
 *
 * <p>NÃO usa tool calling / responseSchema (mesma restrição da Gemini). {@code date}+{@code
 * start_time} → instante America/Sao_Paulo (hardcoded). O paciente vem do contato da conversa.
 * Qualquer falha → {@link Optional#empty()} + warn (a mensagem da IA segue sem consulta).
 */
@Component
public class AgendamentoNutriConfirmHandler {

    private static final Logger log = LoggerFactory.getLogger(AgendamentoNutriConfirmHandler.class);
    private static final ZoneId TENANT_ZONE = ZoneId.of("America/Sao_Paulo");

    private static final Pattern TAG = Pattern.compile("<consulta_nutri>\\s*(\\{.*?\\})\\s*</consulta_nutri>",
        Pattern.DOTALL);

    private final ObjectMapper objectMapper;
    private final NutriPatientService patientService;
    private final NutriAppointmentService appointmentService;

    public AgendamentoNutriConfirmHandler(ObjectMapper objectMapper, NutriPatientService patientService,
                                          NutriAppointmentService appointmentService) {
        this.objectMapper = objectMapper;
        this.patientService = patientService;
        this.appointmentService = appointmentService;
    }

    public boolean hasTag(String text) {
        return text != null && TAG.matcher(text).find();
    }

    public String stripTag(String text) {
        if (text == null) {
            return null;
        }
        return TAG.matcher(text).replaceAll("").stripTrailing();
    }

    /**
     * Extrai a tag e cria a consulta. Resolve o paciente por um dos 2 modos. {@link Optional#empty()}
     * quando: não há tag, JSON inválido, campos faltando, paciente/cadastro inválido, ou a criação da
     * consulta falha (profissional/paciente inválido/inativo, tipo inválido, fora do horário, conflito).
     * O {@code contactId} vem da conversa.
     */
    public Optional<NutriAppointment> parseAndCreate(UUID companyId, UUID conversationId, UUID contactId,
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
            log.warn("nutri: tag <consulta_nutri> com JSON inválido p/ conversa {} ({}) — consulta não criada",
                conversationId, e.getMessage());
            return Optional.empty();
        }

        String rawProf = root.path("professional_id").asText(null);
        String appointmentType = root.path("appointment_type").asText(null);
        String date = root.path("date").asText(null);
        String startTime = root.path("start_time").asText(null);
        String notes = root.path("notes").asText(null);
        if (rawProf == null || appointmentType == null || date == null || startTime == null) {
            log.warn("nutri: tag <consulta_nutri> com campos faltando p/ conversa {} — consulta não criada",
                conversationId);
            return Optional.empty();
        }

        UUID professionalId;
        Instant startAt;
        try {
            professionalId = UUID.fromString(rawProf);
            LocalDate d = LocalDate.parse(date);
            LocalTime t = LocalTime.parse(startTime);
            startAt = d.atTime(t).atZone(TENANT_ZONE).toInstant();
        } catch (RuntimeException e) {
            log.warn("nutri: tag <consulta_nutri> com id/data inválidos p/ conversa {} ({}) — consulta não criada",
                conversationId, e.getMessage());
            return Optional.empty();
        }

        // Resolve o paciente: modo patient_id (existente) OU new_patient (cadastra e agenda).
        UUID patientId;
        try {
            patientId = resolvePatient(companyId, contactId, conversationId, root);
        } catch (ResolvePatientException e) {
            return Optional.empty();
        }
        if (patientId == null) {
            return Optional.empty();
        }

        try {
            NutriAppointment a = appointmentService.create(companyId, professionalId, patientId,
                conversationId, appointmentType, startAt, null, notes);
            log.info("nutri: consulta {} criada p/ conversa {} (prof {}, paciente {}, tipo {})",
                a.id(), conversationId, professionalId, patientId, appointmentType);
            return Optional.of(a);
        } catch (NutriAppointmentService.ConflictException e) {
            log.warn("nutri: <consulta_nutri> conflitou no slot do profissional p/ conversa {} — não criada", conversationId);
            return Optional.empty();
        } catch (NutriAppointmentService.OutsideHoursException e) {
            log.warn("nutri: <consulta_nutri> fora do horário p/ conversa {} — não criada", conversationId);
            return Optional.empty();
        } catch (NutriAppointmentService.InvalidTypeException e) {
            log.warn("nutri: <consulta_nutri> com tipo inválido p/ conversa {} — não criada", conversationId);
            return Optional.empty();
        } catch (NutriAppointmentService.ProfessionalNotFoundException
                 | NutriAppointmentService.PatientNotFoundException
                 | NutriAppointmentService.InactiveProfessionalException
                 | NutriAppointmentService.InactivePatientException e) {
            log.warn("nutri: <consulta_nutri> com profissional/paciente inválido ou inativo p/ conversa {} — não criada",
                conversationId);
            return Optional.empty();
        } catch (RuntimeException e) {
            log.warn("nutri: falha ao criar consulta p/ conversa {} ({}) — mensagem segue sem consulta",
                conversationId, e.getMessage());
            return Optional.empty();
        }
    }

    private static class ResolvePatientException extends RuntimeException {}

    /**
     * Modo patient_id: valida UUID e usa direto (a criação da consulta revalida que é do tenant).
     * Modo new_patient: cadastra o paciente como sub-entidade do contato da conversa e retorna o id
     * criado. Sem contato resolvido → não dá pra cadastrar. Dados inválidos → empty.
     */
    private UUID resolvePatient(UUID companyId, UUID contactId, UUID conversationId, JsonNode root) {
        String rawPatient = root.path("patient_id").asText(null);
        if (rawPatient != null && !rawPatient.isBlank()) {
            try {
                return UUID.fromString(rawPatient);
            } catch (RuntimeException e) {
                log.warn("nutri: <consulta_nutri> com patient_id inválido p/ conversa {} — não criada", conversationId);
                throw new ResolvePatientException();
            }
        }

        JsonNode newPatient = root.path("new_patient");
        if (newPatient.isMissingNode() || !newPatient.isObject()) {
            log.warn("nutri: <consulta_nutri> sem patient_id nem new_patient p/ conversa {} — não criada", conversationId);
            throw new ResolvePatientException();
        }
        if (contactId == null) {
            log.warn("nutri: <consulta_nutri> new_patient sem contato resolvido p/ conversa {} — não criada", conversationId);
            throw new ResolvePatientException();
        }
        String name = newPatient.path("name").asText(null);
        String goal = newPatient.path("goal").asText(null);
        if (name == null || name.isBlank()) {
            log.warn("nutri: <consulta_nutri> new_patient com nome faltando p/ conversa {} — não criada", conversationId);
            throw new ResolvePatientException();
        }
        try {
            // userId null: cadastro disparado pela IA (sem ator humano). Audita com actor nulo.
            NutriPatient created = patientService.create(companyId, null, contactId, name, goal, null, null, null);
            log.info("nutri: paciente {} cadastrado pela IA p/ conversa {} (contato {})", created.id(), conversationId, contactId);
            return created.id();
        } catch (NutriPatientService.ContactNotFoundException e) {
            log.warn("nutri: <consulta_nutri> new_patient com contato inexistente p/ conversa {} — não criada", conversationId);
            throw new ResolvePatientException();
        } catch (RuntimeException e) {
            log.warn("nutri: falha ao cadastrar new_patient p/ conversa {} ({}) — não criada", conversationId, e.getMessage());
            throw new ResolvePatientException();
        }
    }
}
