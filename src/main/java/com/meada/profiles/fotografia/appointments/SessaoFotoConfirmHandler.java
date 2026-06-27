package com.meada.profiles.fotografia.appointments;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.meada.messaging.ContactRepository;
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
 * Extrai a tag {@code <sessao_foto>{...}</sessao_foto>} da resposta da IA e cria a sessão (camada
 * 8.16). Espelho do AgendamentoDermaConfirmHandler, com UM MODO só (sem new_patient — o CLIENTE é o
 * contact da conversa, espelho do AgendamentoEsteticaConfirmHandler do salon/estetica).
 *
 * <p>A sessão referencia {@code professional_id} + {@code package_id} (a duração/preço/delivery_days
 * vêm do pacote — SNAPSHOT; qualquer preço que a IA emita é DESCARTADO, o backend snapshota do
 * catálogo). NÃO usa tool calling / responseSchema (mesma restrição da Gemini). {@code date}+{@code
 * start_time} → instante America/Sao_Paulo (hardcoded). O cliente (name/phone) vem do contato da
 * conversa. Qualquer falha → {@link Optional#empty()} + warn (a mensagem da IA segue sem sessão).
 */
@Component
public class SessaoFotoConfirmHandler {

    private static final Logger log = LoggerFactory.getLogger(SessaoFotoConfirmHandler.class);
    private static final ZoneId TENANT_ZONE = ZoneId.of("America/Sao_Paulo");

    private static final Pattern TAG = Pattern.compile("<sessao_foto>\\s*(\\{.*?\\})\\s*</sessao_foto>",
        Pattern.DOTALL);

    private final ObjectMapper objectMapper;
    private final ContactRepository contactRepository;
    private final FotografiaAppointmentService appointmentService;

    public SessaoFotoConfirmHandler(ObjectMapper objectMapper, ContactRepository contactRepository,
                                    FotografiaAppointmentService appointmentService) {
        this.objectMapper = objectMapper;
        this.contactRepository = contactRepository;
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
     * Extrai a tag e cria a sessão. {@link Optional#empty()} quando: não há tag, JSON inválido, campos
     * faltando, ids/data inválidos, ou a criação falha (profissional/pacote inválido/inativo, fora do
     * horário, conflito). O {@code contactId} vem da conversa; o nome/telefone do cliente são
     * snapshotados do contato. Qualquer preço emitido pela IA é IGNORADO (snapshot do catálogo).
     */
    public Optional<FotografiaSessionAppointment> parseAndCreate(UUID companyId, UUID conversationId, UUID contactId,
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
            log.warn("fotografia: tag <sessao_foto> com JSON inválido p/ conversa {} ({}) — sessão não criada",
                conversationId, e.getMessage());
            return Optional.empty();
        }

        String rawProf = root.path("professional_id").asText(null);
        String rawPkg = root.path("package_id").asText(null);
        String date = root.path("date").asText(null);
        String startTime = root.path("start_time").asText(null);
        String notes = root.path("notes").asText(null);
        if (rawProf == null || rawPkg == null || date == null || startTime == null) {
            log.warn("fotografia: tag <sessao_foto> com campos faltando p/ conversa {} — sessão não criada",
                conversationId);
            return Optional.empty();
        }

        UUID professionalId;
        UUID packageId;
        Instant startAt;
        try {
            professionalId = UUID.fromString(rawProf);
            packageId = UUID.fromString(rawPkg);
            LocalDate d = LocalDate.parse(date);
            LocalTime t = LocalTime.parse(startTime);
            startAt = d.atTime(t).atZone(TENANT_ZONE).toInstant();
        } catch (RuntimeException e) {
            log.warn("fotografia: tag <sessao_foto> com id/data inválidos p/ conversa {} ({}) — sessão não criada",
                conversationId, e.getMessage());
            return Optional.empty();
        }

        String customerName = contactRepository.findNameByConversationId(conversationId)
            .filter(n -> n != null && !n.isBlank())
            .orElseGet(() -> contactRepository.findPhoneByConversationId(conversationId).orElse("Cliente"));
        String customerPhone = contactRepository.findPhoneByConversationId(conversationId).orElse(null);

        try {
            FotografiaSessionAppointment a = appointmentService.create(companyId, professionalId, packageId,
                contactId, conversationId, startAt, customerName, customerPhone, notes);
            log.info("fotografia: sessão {} criada p/ conversa {} (prof {}, pacote {})",
                a.id(), conversationId, professionalId, packageId);
            return Optional.of(a);
        } catch (FotografiaAppointmentService.ConflictException e) {
            log.warn("fotografia: <sessao_foto> conflitou no slot do profissional p/ conversa {} — não criada", conversationId);
            return Optional.empty();
        } catch (FotografiaAppointmentService.OutsideHoursException e) {
            log.warn("fotografia: <sessao_foto> fora do horário p/ conversa {} — não criada", conversationId);
            return Optional.empty();
        } catch (FotografiaAppointmentService.ProfessionalNotFoundException
                 | FotografiaAppointmentService.PackageNotFoundException
                 | FotografiaAppointmentService.InactiveProfessionalException
                 | FotografiaAppointmentService.InactivePackageException e) {
            log.warn("fotografia: <sessao_foto> com profissional/pacote inválido ou inativo p/ conversa {} — não criada",
                conversationId);
            return Optional.empty();
        } catch (RuntimeException e) {
            log.warn("fotografia: falha ao criar sessão p/ conversa {} ({}) — mensagem segue sem sessão",
                conversationId, e.getMessage());
            return Optional.empty();
        }
    }
}
