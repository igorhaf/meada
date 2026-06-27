package com.meada.profiles.eventos.proposals;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extrai a tag {@code <proposta_evento>{...}</proposta_evento>} da resposta da IA e ABRE a proposta
 * (camada 8.2). Espelho do AberturaOsConfirmHandler, mas com UM modo só: NÃO há sub-entidade tipo
 * veículo a cadastrar — o cliente é o próprio contact da conversa (snapshots customer_name/phone).
 *
 * <p>NÃO usa tool calling / responseSchema. Cria a proposta em 'rascunho' (total 0, SEM itens — o
 * cerimonialista monta o orçamento no painel; espelho da OS aberta sem itens do Oficina). Qualquer
 * falha → {@link Optional#empty()} + warn (a mensagem da IA segue sem efeito colateral).
 */
@Component
public class PropostaEventoConfirmHandler {

    private static final Logger log = LoggerFactory.getLogger(PropostaEventoConfirmHandler.class);

    private static final Pattern TAG = Pattern.compile("<proposta_evento>\\s*(\\{.*?\\})\\s*</proposta_evento>",
        Pattern.DOTALL);

    private final ObjectMapper objectMapper;
    private final EventProposalService proposalService;

    public PropostaEventoConfirmHandler(ObjectMapper objectMapper, EventProposalService proposalService) {
        this.objectMapper = objectMapper;
        this.proposalService = proposalService;
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
     * Extrai a tag e abre a proposta em 'rascunho'. {@link Optional#empty()} quando: não há tag, JSON
     * inválido, briefing faltando, ou a abertura falha. O {@code contactId} (cliente) vem da conversa.
     */
    public Optional<EventProposal> parseAndCreate(UUID companyId, UUID conversationId, UUID contactId,
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
            log.warn("eventos: tag <proposta_evento> com JSON inválido p/ conversa {} ({}) — proposta não aberta",
                conversationId, e.getMessage());
            return Optional.empty();
        }

        String briefing = root.path("briefing").asText(null);
        if (briefing == null || briefing.isBlank()) {
            log.warn("eventos: tag <proposta_evento> sem briefing p/ conversa {} — proposta não aberta", conversationId);
            return Optional.empty();
        }
        String eventType = textOrNull(root.path("event_type").asText(null));
        String notes = textOrNull(root.path("notes").asText(null));
        UUID plannerId = parseUuid(root.path("planner_id").asText(null));
        Integer guestCount = root.hasNonNull("guest_count") && root.get("guest_count").isNumber()
            ? root.get("guest_count").asInt() : null;
        if (guestCount != null && guestCount < 0) {
            guestCount = null;
        }
        LocalDate eventDate = parseDate(root.path("event_date").asText(null));

        try {
            EventProposal created = proposalService.open(companyId, contactId, null, plannerId, conversationId,
                eventType, eventDate, guestCount, briefing, notes);
            log.info("eventos: proposta {} aberta p/ conversa {} (cliente {})", created.id(), conversationId, contactId);
            return Optional.of(created);
        } catch (EventProposalService.PlannerNotFoundException | EventProposalService.InactivePlannerException e) {
            log.warn("eventos: <proposta_evento> com cerimonialista inválido/inativo p/ conversa {} — não aberta",
                conversationId);
            return Optional.empty();
        } catch (RuntimeException e) {
            log.warn("eventos: falha ao abrir proposta p/ conversa {} ({}) — mensagem segue sem proposta",
                conversationId, e.getMessage());
            return Optional.empty();
        }
    }

    private static String textOrNull(String raw) {
        if (raw == null || raw.isBlank() || "null".equalsIgnoreCase(raw)) {
            return null;
        }
        return raw;
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

    private static LocalDate parseDate(String raw) {
        if (raw == null || raw.isBlank() || "null".equalsIgnoreCase(raw)) {
            return null;
        }
        try {
            return LocalDate.parse(raw);
        } catch (RuntimeException e) {
            return null;
        }
    }
}
