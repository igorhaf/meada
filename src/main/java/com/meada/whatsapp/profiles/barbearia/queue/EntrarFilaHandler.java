package com.meada.whatsapp.profiles.barbearia.queue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.meada.whatsapp.messaging.ContactRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extrai a tag {@code <fila_barbearia>{...}</fila_barbearia>} da resposta da IA e ENFILEIRA o cliente
 * (camada 8.1). Namespace de tag próprio — distinto de todas as outras tags dos perfis.
 *
 * <p>O {@code barber_id} é OPCIONAL (null/ausente = "qualquer barbeiro" / fila geral). O service lê a
 * duração do serviço (snapshot), valida serviço/barbeiro (ativos) e que a fila está ligada
 * (queue_enabled). Qualquer falha → {@link Optional#empty()} + warn (a mensagem da IA segue sem
 * efeito colateral). Se a fila está desligada, é no-op silencioso (empty).
 *
 * <p>A resposta ao cliente ("você é o Nº X, espera ~Y min") é a mensagem da IA — instruída pelo
 * contexto; o handler só cria o ticket e devolve a posição/ETA derivados (no ticket retornado).
 */
@Component
public class EntrarFilaHandler {

    private static final Logger log = LoggerFactory.getLogger(EntrarFilaHandler.class);

    private static final Pattern TAG = Pattern.compile(
        "<fila_barbearia>\\s*(\\{.*?\\})\\s*</fila_barbearia>", Pattern.DOTALL);

    private final ObjectMapper objectMapper;
    private final ContactRepository contactRepository;
    private final BarberQueueService queueService;

    public EntrarFilaHandler(ObjectMapper objectMapper, ContactRepository contactRepository,
                             BarberQueueService queueService) {
        this.objectMapper = objectMapper;
        this.contactRepository = contactRepository;
        this.queueService = queueService;
    }

    public boolean hasFilaTag(String text) {
        return text != null && TAG.matcher(text).find();
    }

    public String stripFilaTag(String text) {
        if (text == null) {
            return null;
        }
        return TAG.matcher(text).replaceAll("").stripTrailing();
    }

    /**
     * Extrai a tag, resolve o contato e enfileira. {@link Optional#empty()} quando: não há tag, JSON
     * inválido, service faltando/inválido, ou a fila está desligada (no-op).
     */
    public Optional<BarberQueueTicket> parseAndEnqueue(UUID companyId, UUID conversationId, UUID contactId,
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
            log.warn("barbearia: tag <fila_barbearia> com JSON inválido p/ conversa {} ({}) — não enfileirado",
                conversationId, e.getMessage());
            return Optional.empty();
        }

        String rawService = root.path("service_id").asText(null);
        String rawBarber = root.path("barber_id").asText(null);
        String notes = root.path("notes").asText(null);
        if (rawService == null) {
            log.warn("barbearia: tag <fila_barbearia> sem service_id p/ conversa {} — não enfileirado", conversationId);
            return Optional.empty();
        }

        UUID serviceId;
        UUID barberId;
        try {
            serviceId = UUID.fromString(rawService);
            // barber_id ausente/null/"null" = qualquer barbeiro (fila geral).
            barberId = (rawBarber == null || rawBarber.isBlank() || "null".equalsIgnoreCase(rawBarber))
                ? null : UUID.fromString(rawBarber);
        } catch (RuntimeException e) {
            log.warn("barbearia: tag <fila_barbearia> com ids inválidos p/ conversa {} ({}) — não enfileirado",
                conversationId, e.getMessage());
            return Optional.empty();
        }

        String guestName = contactRepository.findNameByConversationId(conversationId)
            .filter(n -> n != null && !n.isBlank())
            .orElseGet(() -> contactRepository.findPhoneByConversationId(conversationId).orElse("Cliente"));
        String guestPhone = contactRepository.findPhoneByConversationId(conversationId).orElse(null);

        try {
            BarberQueueTicket t = queueService.enqueue(companyId, barberId, serviceId, contactId,
                conversationId, guestName, guestPhone, notes);
            log.info("barbearia: ticket {} enfileirado p/ conversa {} (barbeiro {}, posição {}, eta {}min)",
                t.id(), conversationId, barberId, t.position(), t.etaMinutes());
            return Optional.of(t);
        } catch (BarberQueueService.QueueDisabledException e) {
            log.warn("barbearia: <fila_barbearia> mas fila DESLIGADA p/ conversa {} — no-op", conversationId);
            return Optional.empty();
        } catch (BarberQueueService.ServiceNotFoundException
                 | BarberQueueService.BarberNotFoundException
                 | BarberQueueService.InactiveServiceException
                 | BarberQueueService.InactiveBarberException e) {
            log.warn("barbearia: <fila_barbearia> com serviço/barbeiro inválido ou inativo p/ conversa {} — não enfileirado",
                conversationId);
            return Optional.empty();
        } catch (RuntimeException e) {
            log.warn("barbearia: falha ao enfileirar p/ conversa {} ({}) — mensagem segue sem efeito",
                conversationId, e.getMessage());
            return Optional.empty();
        }
    }
}
