package com.meada.profiles.concessionaria.leads;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extrai a tag {@code <lead_carro>{...}</lead_carro>} da resposta da IA e REGISTRA um lead de compra
 * (camada 8.17). Mesma convenção de método do {@link com.meada.profiles.concessionaria.testdrives.TestDriveConfirmHandler}.
 *
 * <p>NÃO usa tool calling / responseSchema. {@code vehicle_id} vem da tag (a IA escolheu da vitrine);
 * o cliente vem do contato da conversa. O PREÇO é SEMPRE snapshot do catálogo (resolvido pelo
 * {@link ConcessionariaLeadService} a partir do veículo) — a tag NÃO carrega preço.
 *
 * <p>Qualquer falha (sem tag, JSON inválido, vehicle_id faltando/inválido, veículo indisponível,
 * condição de pagamento inválida) → {@link Optional#empty()} + warn — a mensagem da IA segue normal,
 * SEM lead criado.
 */
@Component
public class LeadCarroConfirmHandler {

    private static final Logger log = LoggerFactory.getLogger(LeadCarroConfirmHandler.class);

    private static final Pattern TAG = Pattern.compile(
        "<lead_carro>\\s*(\\{.*?\\})\\s*</lead_carro>", Pattern.DOTALL);

    private final ObjectMapper objectMapper;
    private final ConcessionariaLeadService leadService;

    public LeadCarroConfirmHandler(ObjectMapper objectMapper, ConcessionariaLeadService leadService) {
        this.objectMapper = objectMapper;
        this.leadService = leadService;
    }

    /** True se o texto contém a tag de lead (decisão rápida sem parsear). */
    public boolean hasLeadTag(String text) {
        return text != null && TAG.matcher(text).find();
    }

    /** Remove a tag {@code <lead_carro>...</lead_carro>} do texto (para não enviá-la ao cliente). */
    public String stripLeadTag(String text) {
        if (text == null) {
            return null;
        }
        return TAG.matcher(text).replaceAll("").stripTrailing();
    }

    /**
     * Extrai a tag, resolve o veículo da tag, snapshota o preço do catálogo e cria o lead 'novo'.
     * {@link Optional#empty()} quando: não há tag, JSON inválido, vehicle_id faltando/inválido,
     * veículo indisponível, ou condição de pagamento inválida. O preço NUNCA vem da tag.
     */
    public Optional<ConcessionariaLead> parseAndCreate(UUID companyId, UUID conversationId,
                                                       UUID contactId, String aiResponseText) {
        if (aiResponseText == null) {
            return Optional.empty();
        }
        Matcher m = TAG.matcher(aiResponseText);
        if (!m.find()) {
            return Optional.empty();   // conversa normal (interesse ainda em conversa).
        }

        JsonNode root;
        try {
            root = objectMapper.readTree(m.group(1));
        } catch (Exception e) {
            log.warn("concessionaria: tag <lead_carro> com JSON inválido p/ conversa {} ({}) — lead não criado",
                conversationId, e.getMessage());
            return Optional.empty();
        }

        UUID vehicleId = parseUuid(root.path("vehicle_id").asText(null));
        String paymentCondition = nullableText(root.path("payment_condition").asText(null));
        String notes = nullableText(root.path("notes").asText(null));
        if (vehicleId == null) {
            log.warn("concessionaria: tag <lead_carro> sem vehicle_id válido p/ conversa {} — lead não criado",
                conversationId);
            return Optional.empty();
        }

        try {
            ConcessionariaLead lead = leadService.createLead(companyId,
                new LeadInput(vehicleId, conversationId, contactId, paymentCondition, notes));
            log.info("concessionaria: lead {} registrado p/ conversa {} (veículo {})",
                lead.id(), conversationId, vehicleId);
            return Optional.of(lead);
        } catch (ConcessionariaLeadService.VehicleNotAvailableException e) {
            log.warn("concessionaria: <lead_carro> com veículo indisponível p/ conversa {} — lead não criado", conversationId);
            return Optional.empty();
        } catch (ConcessionariaLeadService.InvalidPaymentConditionException e) {
            log.warn("concessionaria: <lead_carro> com condição de pagamento inválida p/ conversa {} — lead não criado", conversationId);
            return Optional.empty();
        } catch (RuntimeException e) {
            log.warn("concessionaria: falha ao registrar lead p/ conversa {} ({}) — mensagem segue sem lead",
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
