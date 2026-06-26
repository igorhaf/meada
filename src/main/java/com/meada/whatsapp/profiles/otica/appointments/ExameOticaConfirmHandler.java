package com.meada.whatsapp.profiles.otica.appointments;

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
 * Extrai a tag {@code <exame_otica>{...}</exame_otica>} da resposta da IA e cria o exame de vista
 * (camada 8.12, perfil otica FLUXO A). Espelho do {@code ConsultaConfirmHandler} do dental — mas o
 * CLIENTE é o contact (sem sub-entidade de paciente): o {@code customer_name} é resolvido do contato
 * da conversa (snapshot).
 *
 * <p>NÃO usa tool calling / responseSchema do Gemini (mesma restrição das outras tags). A IA emite em
 * texto livre; parseamos via regex DOTALL.
 *
 * <p>{@code date}+{@code start_time} → instante no fuso America/Sao_Paulo (hardcoded — pendência). Se
 * o profissional não existir, o horário conflitar ou estiver fora do funcionamento, retorna
 * {@link Optional#empty()} — a mensagem da IA segue normal, SEM exame criado (loga warn).
 */
@Component
public class ExameOticaConfirmHandler {

    private static final Logger log = LoggerFactory.getLogger(ExameOticaConfirmHandler.class);
    private static final ZoneId TENANT_ZONE = ZoneId.of("America/Sao_Paulo");

    private static final Pattern TAG = Pattern.compile("<exame_otica>\\s*(\\{.*?\\})\\s*</exame_otica>",
        Pattern.DOTALL);

    private final ObjectMapper objectMapper;
    private final ContactRepository contactRepository;
    private final OticaExamService examService;

    public ExameOticaConfirmHandler(ObjectMapper objectMapper, ContactRepository contactRepository,
                                    OticaExamService examService) {
        this.objectMapper = objectMapper;
        this.contactRepository = contactRepository;
        this.examService = examService;
    }

    /** True se o texto contém a tag de exame (decisão rápida sem parsear). */
    public boolean hasTag(String text) {
        return text != null && TAG.matcher(text).find();
    }

    /** Remove a tag {@code <exame_otica>...</exame_otica>} do texto (para não enviá-la ao cliente). */
    public String stripTag(String text) {
        if (text == null) {
            return null;
        }
        return TAG.matcher(text).replaceAll("").stripTrailing();
    }

    /**
     * Extrai a tag, resolve o nome do cliente pelo contato e cria o exame. {@link Optional#empty()}
     * quando: não há tag, JSON inválido, campos faltando, profissional inexistente, fora do horário,
     * ou conflito de slot.
     */
    public Optional<OticaExamAppointment> parseAndCreate(UUID companyId, UUID conversationId,
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
            log.warn("otica: tag <exame_otica> com JSON inválido p/ conversa {} ({}) — exame não criado",
                conversationId, e.getMessage());
            return Optional.empty();
        }

        String rawProf = root.path("professional_id").asText(null);
        String date = root.path("date").asText(null);
        String startTime = root.path("start_time").asText(null);
        String notes = root.path("notes").asText(null);
        if (rawProf == null || date == null || startTime == null) {
            log.warn("otica: tag <exame_otica> com campos faltando p/ conversa {} — exame não criado", conversationId);
            return Optional.empty();
        }

        UUID professionalId;
        try {
            professionalId = UUID.fromString(rawProf);
        } catch (IllegalArgumentException e) {
            log.warn("otica: professional_id não-UUID '{}' na tag <exame_otica> p/ conversa {} — exame não criado",
                rawProf, conversationId);
            return Optional.empty();
        }

        Instant startAt;
        try {
            LocalDate d = LocalDate.parse(date);
            LocalTime t = LocalTime.parse(startTime);
            startAt = d.atTime(t).atZone(TENANT_ZONE).toInstant();
        } catch (RuntimeException e) {
            log.warn("otica: tag <exame_otica> com date/start_time inválido p/ conversa {} ({}) — exame não criado",
                conversationId, e.getMessage());
            return Optional.empty();
        }

        // O cliente é o contact (snapshot do nome — sem sub-entidade de paciente).
        String customerName = contactRepository.findNameByConversationId(conversationId)
            .filter(n -> !n.isBlank())
            .orElseGet(() -> contactRepository.findPhoneByConversationId(conversationId).orElse("Cliente"));

        try {
            OticaExamAppointment a = examService.create(companyId, professionalId, conversationId,
                contactId, customerName, startAt, notes);
            log.info("otica: exame {} criado p/ conversa {} (profissional {})",
                a.id(), conversationId, professionalId);
            return Optional.of(a);
        } catch (OticaExamService.ConflictException e) {
            log.warn("otica: <exame_otica> conflitou no slot p/ conversa {} (IA prometeu mas perdeu a corrida) — exame não criado",
                conversationId);
            return Optional.empty();
        } catch (OticaExamService.OutsideHoursException e) {
            log.warn("otica: <exame_otica> fora do horário p/ conversa {} — exame não criado", conversationId);
            return Optional.empty();
        } catch (OticaExamService.ProfessionalNotFoundException e) {
            log.warn("otica: <exame_otica> com profissional inexistente p/ conversa {} — exame não criado", conversationId);
            return Optional.empty();
        } catch (RuntimeException e) {
            log.warn("otica: falha ao criar exame p/ conversa {} ({}) — mensagem segue sem exame",
                conversationId, e.getMessage());
            return Optional.empty();
        }
    }
}
