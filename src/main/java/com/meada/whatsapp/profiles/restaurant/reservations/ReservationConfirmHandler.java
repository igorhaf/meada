package com.meada.whatsapp.profiles.restaurant.reservations;

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
 * Extrai a tag {@code <reserva>{...}</reserva>} da resposta da IA, valida e cria a reserva (camada
 * 7.3). Espelho do OrderConfirmHandler do sushi.
 *
 * <p>NÃO usa tool calling / responseSchema do Gemini (mesma restrição do sushi: a API trata os dois
 * como mutuamente exclusivos, e o fluxo de outbound já usa responseSchema). A IA emite a tag em
 * texto livre; aqui parseamos via regex.
 *
 * <p>{@code date}+{@code start_time} são combinados em um instante no fuso America/Sao_Paulo
 * (hardcoded — pendência conhecida). Se a mesa não existe, o horário está fora de funcionamento, ou
 * o slot conflita (a IA prometeu mas perdeu a corrida), retorna {@link Optional#empty()} — a
 * mensagem da IA segue normal, SEM reserva criada (loga warn). O tenant vê que a reserva não entrou
 * no Kanban e contorna manualmente (risco aceito no MVP, decisão 5).
 */
@Component
public class ReservationConfirmHandler {

    private static final Logger log = LoggerFactory.getLogger(ReservationConfirmHandler.class);
    private static final ZoneId TENANT_ZONE = ZoneId.of("America/Sao_Paulo");

    // <reserva> ... </reserva> — DOTALL para o JSON poder ter quebras de linha.
    private static final Pattern TAG = Pattern.compile("<reserva>\\s*(\\{.*?\\})\\s*</reserva>",
        Pattern.DOTALL);

    private final ObjectMapper objectMapper;
    private final ReservationService reservationService;

    public ReservationConfirmHandler(ObjectMapper objectMapper, ReservationService reservationService) {
        this.objectMapper = objectMapper;
        this.reservationService = reservationService;
    }

    /** True se o texto contém a tag de reserva (decisão rápida sem parsear). */
    public boolean hasReservationTag(String text) {
        return text != null && TAG.matcher(text).find();
    }

    /** Remove a tag {@code <reserva>...</reserva>} do texto (para não enviá-la ao cliente). */
    public String stripReservationTag(String text) {
        if (text == null) {
            return null;
        }
        return TAG.matcher(text).replaceAll("").stripTrailing();
    }

    /**
     * Extrai a tag, valida e cria a reserva. {@link Optional#empty()} quando: não há tag, JSON
     * inválido, campos faltando/ inválidos, mesa inexistente, fora do horário, ou conflito de slot.
     */
    public Optional<Reservation> parseAndCreate(UUID companyId, UUID conversationId, UUID contactId,
                                                String guestName, String guestPhone,
                                                String aiResponseText) {
        if (aiResponseText == null) {
            return Optional.empty();
        }
        Matcher m = TAG.matcher(aiResponseText);
        if (!m.find()) {
            return Optional.empty();   // conversa normal (reserva ainda em negociação).
        }

        JsonNode root;
        try {
            root = objectMapper.readTree(m.group(1));
        } catch (Exception e) {
            log.warn("restaurant: tag <reserva> com JSON inválido p/ conversa {} ({}) — reserva não criada",
                conversationId, e.getMessage());
            return Optional.empty();
        }

        String rawTableId = root.path("table_id").asText(null);
        String date = root.path("date").asText(null);
        String startTime = root.path("start_time").asText(null);
        int numPeople = root.path("num_people").asInt(0);
        if (rawTableId == null || date == null || startTime == null || numPeople <= 0) {
            log.warn("restaurant: tag <reserva> com campos faltando p/ conversa {} — reserva não criada",
                conversationId);
            return Optional.empty();
        }

        UUID tableId;
        Instant startAt;
        try {
            tableId = UUID.fromString(rawTableId);
            LocalDate d = LocalDate.parse(date);
            LocalTime t = LocalTime.parse(startTime);
            startAt = d.atTime(t).atZone(TENANT_ZONE).toInstant();
        } catch (RuntimeException e) {
            log.warn("restaurant: tag <reserva> com table_id/date/start_time inválido p/ conversa {} ({}) — reserva não criada",
                conversationId, e.getMessage());
            return Optional.empty();
        }

        try {
            Reservation r = reservationService.create(companyId, tableId, conversationId, contactId,
                guestName, guestPhone, startAt, numPeople, null);
            log.info("restaurant: reserva {} criada p/ conversa {} (mesa {}, {} pessoas)",
                r.id(), conversationId, tableId, numPeople);
            return Optional.of(r);
        } catch (ReservationService.ConflictException e) {
            log.warn("restaurant: <reserva> conflitou no slot p/ conversa {} (IA prometeu mas perdeu a corrida) — reserva não criada",
                conversationId);
            return Optional.empty();
        } catch (ReservationService.OutsideHoursException e) {
            log.warn("restaurant: <reserva> fora do horário p/ conversa {} — reserva não criada", conversationId);
            return Optional.empty();
        } catch (ReservationService.TableNotFoundException e) {
            log.warn("restaurant: <reserva> com mesa inexistente {} p/ conversa {} — reserva não criada",
                tableId, conversationId);
            return Optional.empty();
        } catch (RuntimeException e) {
            log.warn("restaurant: falha ao criar reserva p/ conversa {} ({}) — mensagem segue sem reserva",
                conversationId, e.getMessage());
            return Optional.empty();
        }
    }
}
