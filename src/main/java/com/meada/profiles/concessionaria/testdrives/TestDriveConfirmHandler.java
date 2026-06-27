package com.meada.profiles.concessionaria.testdrives;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
 * Extrai a tag {@code <testdrive_carro>{...}</testdrive_carro>} da resposta da IA e AGENDA o
 * test-drive (camada 8.17). Clone do ConsultaConfirmHandler do dental.
 *
 * <p>NÃO usa tool calling / responseSchema do Gemini (mesma restrição das outras tags). A IA emite em
 * texto livre; parseamos via regex. {@code date}+{@code start_time} → instante no fuso
 * America/Sao_Paulo (hardcoded — pendência). {@code vehicle_id} e {@code salesperson_id} vêm da tag (a
 * IA escolheu da vitrine + vendedores no contexto). O cliente vem do contato da conversa.
 *
 * <p>Qualquer falha (sem tag, JSON inválido, campos faltando, veículo indisponível, vendedor
 * inválido, fora do horário, conflito de slot) → {@link Optional#empty()} + warn — a mensagem da IA
 * segue normal, SEM test-drive criado.
 */
@Component
public class TestDriveConfirmHandler {

    private static final Logger log = LoggerFactory.getLogger(TestDriveConfirmHandler.class);
    private static final ZoneId TENANT_ZONE = ZoneId.of("America/Sao_Paulo");

    private static final Pattern TAG = Pattern.compile(
        "<testdrive_carro>\\s*(\\{.*?\\})\\s*</testdrive_carro>", Pattern.DOTALL);

    private final ObjectMapper objectMapper;
    private final ConcessionariaTestDriveService testDriveService;

    public TestDriveConfirmHandler(ObjectMapper objectMapper,
                                   ConcessionariaTestDriveService testDriveService) {
        this.objectMapper = objectMapper;
        this.testDriveService = testDriveService;
    }

    /** True se o texto contém a tag de test-drive (decisão rápida sem parsear). */
    public boolean hasTestdriveTag(String text) {
        return text != null && TAG.matcher(text).find();
    }

    /** Remove a tag {@code <testdrive_carro>...</testdrive_carro>} do texto (para não enviá-la ao cliente). */
    public String stripTestdriveTag(String text) {
        if (text == null) {
            return null;
        }
        return TAG.matcher(text).replaceAll("").stripTrailing();
    }

    /**
     * Extrai a tag, resolve veículo/vendedor da tag e cria o test-drive. {@link Optional#empty()}
     * quando: não há tag, JSON inválido, campos faltando, veículo indisponível, vendedor inválido,
     * fora do horário, ou conflito de slot.
     */
    public Optional<ConcessionariaTestDrive> parseAndCreate(UUID companyId, UUID conversationId,
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
            log.warn("concessionaria: tag <testdrive_carro> com JSON inválido p/ conversa {} ({}) — test-drive não criado",
                conversationId, e.getMessage());
            return Optional.empty();
        }

        UUID vehicleId = parseUuid(root.path("vehicle_id").asText(null));
        UUID salespersonId = parseUuid(root.path("salesperson_id").asText(null));
        String date = root.path("date").asText(null);
        String startTime = root.path("start_time").asText(null);
        String notes = nullableText(root.path("notes").asText(null));
        if (vehicleId == null || salespersonId == null || date == null || startTime == null) {
            log.warn("concessionaria: tag <testdrive_carro> com campos faltando/ inválidos p/ conversa {} — test-drive não criado",
                conversationId);
            return Optional.empty();
        }

        Instant startAt;
        try {
            LocalDate d = LocalDate.parse(date);
            LocalTime t = LocalTime.parse(startTime);
            startAt = d.atTime(t).atZone(TENANT_ZONE).toInstant();
        } catch (RuntimeException e) {
            log.warn("concessionaria: tag <testdrive_carro> com date/start_time inválido p/ conversa {} ({}) — test-drive não criado",
                conversationId, e.getMessage());
            return Optional.empty();
        }

        try {
            ConcessionariaTestDrive td = testDriveService.createTestDrive(companyId,
                new TestDriveInput(vehicleId, salespersonId, conversationId, contactId, startAt, notes));
            log.info("concessionaria: test-drive {} agendado p/ conversa {} (veículo {}, vendedor {})",
                td.id(), conversationId, vehicleId, salespersonId);
            return Optional.of(td);
        } catch (ConcessionariaTestDriveService.ConflictException e) {
            log.warn("concessionaria: <testdrive_carro> conflitou no slot do vendedor p/ conversa {} (IA prometeu mas perdeu a corrida) — não criado",
                conversationId);
            return Optional.empty();
        } catch (ConcessionariaTestDriveService.VehicleNotAvailableException e) {
            log.warn("concessionaria: <testdrive_carro> com veículo indisponível p/ conversa {} — não criado", conversationId);
            return Optional.empty();
        } catch (ConcessionariaTestDriveService.OutsideHoursException e) {
            log.warn("concessionaria: <testdrive_carro> fora do horário p/ conversa {} — não criado", conversationId);
            return Optional.empty();
        } catch (RuntimeException e) {
            log.warn("concessionaria: falha ao agendar test-drive p/ conversa {} ({}) — mensagem segue sem test-drive",
                conversationId, e.getMessage());
            return Optional.empty();
        }
    }

    private static UUID parseUuid(String raw) {
        if (raw == null || raw.isBlank() || "null".equalsIgnoreCase(raw)) {
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static String nullableText(String raw) {
        return raw == null || raw.isBlank() || "null".equalsIgnoreCase(raw) ? null : raw;
    }
}
