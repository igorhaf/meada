package com.meada.profiles.papelaria.orders;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.meada.profiles.papelaria.PapelariaFulfillment;
import com.meada.profiles.papelaria.PapelariaPeriod;
import com.meada.profiles.papelaria.catalog.PapelariaCatalogItem;
import com.meada.profiles.papelaria.catalog.PapelariaCatalogItemRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extrai a tag {@code <pedido_papelaria>{...}</pedido_papelaria>} da resposta da IA, valida os itens
 * contra o catálogo do tenant e cria o pedido (camada 8.15 / perfil papelaria). Clone de
 * {@link com.meada.profiles.padaria.orders.EncomendaPadariaConfirmHandler} (camada 8.8;
 * mesmos nomes de método: {@code hasOrderTag}/{@code stripOrderTag}/{@code parseAndCreate}) adaptado
 * às escapadas da papelaria: fulfillment (retirada/entrega), data CONDICIONAL (pickup_or_delivery_date
 * só obrigatória se há item sob encomenda — validação fina no repositório), TIRAGEM (quantity escala o
 * line total), texto personalizado (custom_text por item).
 *
 * <p>NÃO usa tool calling / responseSchema do Gemini (constraint: a API trata os dois como mutuamente
 * exclusivos, e o fluxo de outbound já usa responseSchema). A IA emite a tag em texto livre; aqui
 * parseamos via regex.
 *
 * <p>O {@code total} eventual do JSON é DESCARTADO — o repositório recalcula (base + Σ deltas das
 * opções, × tiragem). As OPÇÕES NÃO são validadas aqui; e a regra de lead time / endereço também é do
 * repo — se ele lançar ({@code InvalidOptionException}/{@code LeadTimeViolationException}/
 * {@code AddressRequiredException}), o catch devolve {@link Optional#empty()} (a mensagem da IA segue
 * normal, sem pedido).
 */
@Component
public class PedidoPapelariaConfirmHandler {

    private static final Logger log = LoggerFactory.getLogger(PedidoPapelariaConfirmHandler.class);

    // <pedido_papelaria> ... </pedido_papelaria> — DOTALL para o JSON poder ter quebras de linha.
    private static final Pattern TAG = Pattern.compile(
        "<pedido_papelaria>\\s*(\\{.*?\\})\\s*</pedido_papelaria>", Pattern.DOTALL);

    private final ObjectMapper objectMapper;
    private final PapelariaCatalogItemRepository catalogRepository;
    private final PapelariaOrderService orderService;

    public PedidoPapelariaConfirmHandler(ObjectMapper objectMapper, PapelariaCatalogItemRepository catalogRepository,
                                         PapelariaOrderService orderService) {
        this.objectMapper = objectMapper;
        this.catalogRepository = catalogRepository;
        this.orderService = orderService;
    }

    /** True se o texto contém a tag de pedido (decisão rápida sem parsear). */
    public boolean hasOrderTag(String text) {
        return text != null && TAG.matcher(text).find();
    }

    /** Remove a tag {@code <pedido_papelaria>...</pedido_papelaria>} do texto (para não enviá-la ao cliente). */
    public String stripOrderTag(String text) {
        if (text == null) {
            return null;
        }
        return TAG.matcher(text).replaceAll("").stripTrailing();
    }

    /**
     * Extrai a tag, valida os itens e cria o pedido. {@link Optional#empty()} quando: não há tag,
     * JSON inválido, fulfillment inválido, período inválido, data no passado, nenhum item válido
     * (catalog_item inexistente ou indisponível), ou o repo recusa por opção inválida / lead time /
     * endereço ausente em 'entrega'.
     */
    public Optional<PapelariaOrder> parseAndCreate(UUID companyId, UUID conversationId, UUID contactId,
                                                  String aiResponseText) {
        if (aiResponseText == null) {
            return Optional.empty();
        }
        Matcher m = TAG.matcher(aiResponseText);
        if (!m.find()) {
            return Optional.empty();   // conversa normal (carrinho em construção).
        }

        JsonNode root;
        try {
            root = objectMapper.readTree(m.group(1));
        } catch (Exception e) {
            log.warn("papelaria: tag <pedido_papelaria> com JSON inválido p/ conversa {} ({}) — pedido não criado",
                conversationId, e.getMessage());
            return Optional.empty();
        }

        // fulfillment: retirada | entrega.
        String fulfillment = root.path("fulfillment").asText(null);
        if (PapelariaFulfillment.fromId(fulfillment).isEmpty()) {
            log.warn("papelaria: fulfillment inválido '{}' na tag <pedido_papelaria> p/ conversa {} — pedido não criado",
                fulfillment, conversationId);
            return Optional.empty();
        }

        // delivery_address: nullable (validado no repo conforme o fulfillment).
        String enderecoRaw = root.path("delivery_address").asText(null);
        String endereco = enderecoRaw == null || enderecoRaw.isBlank() ? null : enderecoRaw.strip();

        // pickup_or_delivery_date: CONDICIONAL (nullable). Se vier, parseia e rejeita passado; a
        // obrigatoriedade (item sob encomenda) é checada no repo (que também conhece o lead).
        LocalDate pickupOrDeliveryDate = null;
        JsonNode dateNode = root.path("pickup_or_delivery_date");
        if (!dateNode.isMissingNode() && !dateNode.isNull()) {
            String rawData = dateNode.asText(null);
            if (rawData != null && !rawData.isBlank()) {
                try {
                    pickupOrDeliveryDate = LocalDate.parse(rawData);
                } catch (Exception e) {
                    log.warn("papelaria: pickup_or_delivery_date inválida '{}' na tag <pedido_papelaria> p/ conversa {} — pedido não criado",
                        rawData, conversationId);
                    return Optional.empty();
                }
                LocalDate hoje = LocalDate.now(java.time.ZoneId.of("America/Sao_Paulo"));
                if (pickupOrDeliveryDate.isBefore(hoje)) {
                    log.warn("papelaria: pickup_or_delivery_date no passado ({}) na tag <pedido_papelaria> p/ conversa {} — pedido não criado",
                        pickupOrDeliveryDate, conversationId);
                    return Optional.empty();
                }
            }
        }

        // delivery_period: nullable; se vier, deve estar em PapelariaPeriod.
        String periodo = null;
        JsonNode periodNode = root.path("delivery_period");
        if (!periodNode.isMissingNode() && !periodNode.isNull()) {
            String rawPeriod = periodNode.asText(null);
            if (rawPeriod != null && !rawPeriod.isBlank()) {
                if (PapelariaPeriod.fromId(rawPeriod).isEmpty()) {
                    log.warn("papelaria: delivery_period inválido '{}' na tag <pedido_papelaria> p/ conversa {} — pedido não criado",
                        rawPeriod, conversationId);
                    return Optional.empty();
                }
                periodo = rawPeriod;
            }
        }

        String notesRaw = root.path("notes").asText(null);
        String notes = notesRaw == null || notesRaw.isBlank() ? null : notesRaw.strip();

        JsonNode itemsNode = root.path("items");
        if (!itemsNode.isArray() || itemsNode.isEmpty()) {
            log.warn("papelaria: tag <pedido_papelaria> sem items p/ conversa {} — pedido não criado", conversationId);
            return Optional.empty();
        }

        // Valida cada item: existe no catálogo do tenant E está disponível. As opções NÃO são
        // validadas aqui (passam adiante; o repo recusa opção fantasma na criação).
        List<OrderLineInput> lines = new ArrayList<>();
        for (JsonNode itemNode : itemsNode) {
            String rawId = itemNode.path("catalog_item_id").asText(null);
            int qtd = itemNode.path("quantity").asInt(0);
            if (rawId == null || qtd <= 0) {
                log.warn("papelaria: item inválido (id/quantity) na tag <pedido_papelaria> p/ conversa {} — pedido não criado",
                    conversationId);
                return Optional.empty();
            }
            UUID itemId;
            try {
                itemId = UUID.fromString(rawId);
            } catch (IllegalArgumentException e) {
                log.warn("papelaria: catalog_item_id não-UUID '{}' na tag <pedido_papelaria> p/ conversa {} — pedido não criado",
                    rawId, conversationId);
                return Optional.empty();
            }
            Optional<PapelariaCatalogItem> catalogItem = catalogRepository.findById(companyId, itemId);
            if (catalogItem.isEmpty() || !catalogItem.get().available()) {
                log.warn("papelaria: item {} inexistente/indisponível na tag <pedido_papelaria> p/ conversa {} — pedido não criado",
                    itemId, conversationId);
                return Optional.empty();
            }

            // Parse das opções (array de objetos {option_id}; opcional — item sem opção → lista vazia).
            List<UUID> optionIds = new ArrayList<>();
            JsonNode optionsNode = itemNode.path("options");
            if (optionsNode.isArray()) {
                for (JsonNode optNode : optionsNode) {
                    String rawOpt = optNode.path("option_id").asText(null);
                    if (rawOpt == null || rawOpt.isBlank()) {
                        continue;
                    }
                    try {
                        optionIds.add(UUID.fromString(rawOpt));
                    } catch (IllegalArgumentException e) {
                        log.warn("papelaria: option_id não-UUID '{}' na tag <pedido_papelaria> p/ conversa {} — pedido não criado",
                            rawOpt, conversationId);
                        return Optional.empty();
                    }
                }
            }

            // custom_text: texto personalizado livre, opcional (nullable).
            String customTextRaw = itemNode.path("custom_text").asText(null);
            String customText = customTextRaw == null || customTextRaw.isBlank() ? null : customTextRaw.strip();

            lines.add(new OrderLineInput(itemId, qtd, optionIds, customText));
        }

        try {
            PapelariaOrder order = orderService.create(companyId, conversationId, contactId,
                fulfillment, endereco, lines, pickupOrDeliveryDate, periodo, notes);
            log.info("papelaria: pedido {} criado p/ conversa {} ({} itens, fulfillment {}, total {} cents)",
                order.id(), conversationId, lines.size(), fulfillment, order.totalCents());
            return Optional.of(order);
        } catch (RuntimeException e) {
            // Inclui InvalidOptionException, LeadTimeViolationException, AddressRequiredException e
            // IllegalArgumentException (sem linha válida).
            log.warn("papelaria: falha ao criar pedido p/ conversa {} ({}) — mensagem segue sem pedido",
                conversationId, e.getMessage());
            return Optional.empty();
        }
    }
}
