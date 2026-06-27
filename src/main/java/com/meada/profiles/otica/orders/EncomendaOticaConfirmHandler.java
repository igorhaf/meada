package com.meada.profiles.otica.orders;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.meada.profiles.otica.catalog.OticaCatalogItem;
import com.meada.profiles.otica.catalog.OticaCatalogItemRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extrai a tag {@code <encomenda_otica>{...}</encomenda_otica>} da resposta da IA, valida os itens
 * contra o catálogo do tenant e cria a encomenda (camada 8.12, perfil otica FLUXO B). Clone do
 * {@code PedidoFlorConfirmHandler} + as escapadas da ótica: PRAZO ({@code ready_date}) e RECEITA
 * ({@code rx} + {@code prescription_pending}).
 *
 * <p>NÃO usa tool calling / responseSchema do Gemini. A IA emite a tag em texto livre; parseamos via
 * regex DOTALL.
 *
 * <p>O {@code total_cents} do JSON (se vier) é DESCARTADO — o repositório recalcula. A receita é
 * registrada AS-IS (a IA NÃO interpreta o grau): cada campo rx_* é persistido como veio; se a tag NÃO
 * traz bloco {@code rx} OU traz {@code prescription_pending=true} → {@code prescription_pending=true}
 * (a loja confirma a receita no painel antes de montar). As OPÇÕES não são validadas aqui — os
 * optionIds passam adiante; se o repo lançar InvalidOptionException (opção fantasma), devolvemos
 * {@link Optional#empty()}. Idem para a violação de prazo (LeadTimeViolationException).
 */
@Component
public class EncomendaOticaConfirmHandler {

    private static final Logger log = LoggerFactory.getLogger(EncomendaOticaConfirmHandler.class);

    private static final Pattern TAG = Pattern.compile(
        "<encomenda_otica>\\s*(\\{.*?\\})\\s*</encomenda_otica>", Pattern.DOTALL);

    private final ObjectMapper objectMapper;
    private final OticaCatalogItemRepository catalogRepository;
    private final OticaOrderService orderService;

    public EncomendaOticaConfirmHandler(ObjectMapper objectMapper, OticaCatalogItemRepository catalogRepository,
                                        OticaOrderService orderService) {
        this.objectMapper = objectMapper;
        this.catalogRepository = catalogRepository;
        this.orderService = orderService;
    }

    /** True se o texto contém a tag de encomenda (decisão rápida sem parsear). */
    public boolean hasTag(String text) {
        return text != null && TAG.matcher(text).find();
    }

    /** Remove a tag {@code <encomenda_otica>...</encomenda_otica>} do texto (para não enviá-la ao cliente). */
    public String stripTag(String text) {
        if (text == null) {
            return null;
        }
        return TAG.matcher(text).replaceAll("").stripTrailing();
    }

    /**
     * Extrai a tag, valida os itens e cria a encomenda. {@link Optional#empty()} quando: não há tag,
     * JSON inválido, nenhum item válido, opção fantasma, ou violação de prazo (ready_date ausente/cedo
     * demais p/ item sob encomenda).
     */
    public Optional<OticaOrder> parseAndCreate(UUID companyId, UUID conversationId, UUID contactId,
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
            log.warn("otica: tag <encomenda_otica> com JSON inválido p/ conversa {} ({}) — encomenda não criada",
                conversationId, e.getMessage());
            return Optional.empty();
        }

        JsonNode itemsNode = root.path("items");
        if (!itemsNode.isArray() || itemsNode.isEmpty()) {
            log.warn("otica: tag <encomenda_otica> sem items p/ conversa {} — encomenda não criada", conversationId);
            return Optional.empty();
        }

        // ready_date opcional ("YYYY-MM-DD" ou null). O repo decide se é obrigatória (item sob encomenda).
        LocalDate readyDate = null;
        String rawReady = root.path("ready_date").asText(null);
        if (rawReady != null && !rawReady.isBlank() && !"null".equalsIgnoreCase(rawReady)) {
            try {
                readyDate = LocalDate.parse(rawReady);
            } catch (Exception e) {
                log.warn("otica: ready_date inválida '{}' na tag <encomenda_otica> p/ conversa {} — encomenda não criada",
                    rawReady, conversationId);
                return Optional.empty();
            }
        }

        // RECEITA (registrada AS-IS; a IA NÃO interpreta o grau).
        OticaPrescription rx = parsePrescription(root);

        // Valida cada item: existe no catálogo do tenant E está disponível.
        List<OticaOrderLineInput> lines = new ArrayList<>();
        for (JsonNode itemNode : itemsNode) {
            String rawId = itemNode.path("catalog_item_id").asText(null);
            int qtd = itemNode.path("quantity").asInt(itemNode.path("qtd").asInt(0));
            if (rawId == null || qtd <= 0) {
                log.warn("otica: item inválido (id/quantity) na tag <encomenda_otica> p/ conversa {} — encomenda não criada",
                    conversationId);
                return Optional.empty();
            }
            UUID itemId;
            try {
                itemId = UUID.fromString(rawId);
            } catch (IllegalArgumentException e) {
                log.warn("otica: catalog_item_id não-UUID '{}' na tag <encomenda_otica> p/ conversa {} — encomenda não criada",
                    rawId, conversationId);
                return Optional.empty();
            }
            Optional<OticaCatalogItem> catalogItem = catalogRepository.findById(companyId, itemId);
            if (catalogItem.isEmpty() || !catalogItem.get().available()) {
                log.warn("otica: item {} inexistente/indisponível na tag <encomenda_otica> p/ conversa {} — encomenda não criada",
                    itemId, conversationId);
                return Optional.empty();
            }

            // Opções (array de objetos {option_id} OU strings; opcional — item sem opção → lista vazia).
            List<UUID> optionIds = new ArrayList<>();
            JsonNode optionsNode = itemNode.path("options");
            if (optionsNode.isArray()) {
                for (JsonNode optNode : optionsNode) {
                    String rawOpt = optNode.isObject() ? optNode.path("option_id").asText(null) : optNode.asText(null);
                    if (rawOpt == null || rawOpt.isBlank()) {
                        continue;
                    }
                    try {
                        optionIds.add(UUID.fromString(rawOpt));
                    } catch (IllegalArgumentException e) {
                        log.warn("otica: option_id não-UUID '{}' na tag <encomenda_otica> p/ conversa {} — encomenda não criada",
                            rawOpt, conversationId);
                        return Optional.empty();
                    }
                }
            }
            lines.add(new OticaOrderLineInput(itemId, qtd, optionIds));
        }

        String notes = root.path("notes").asText(null);

        try {
            OticaOrder order = orderService.create(companyId, conversationId, contactId, lines, notes, readyDate, rx);
            log.info("otica: encomenda {} criada p/ conversa {} ({} itens, total {} cents, prescription_pending={})",
                order.id(), conversationId, lines.size(), order.totalCents(), order.prescriptionPending());
            return Optional.of(order);
        } catch (OticaOrderService.LeadTimeViolationException e) {
            log.warn("otica: <encomenda_otica> com prazo inválido (ready_date < {}) p/ conversa {} — encomenda não criada",
                e.earliest(), conversationId);
            return Optional.empty();
        } catch (RuntimeException e) {
            // Inclui InvalidOptionException (opção fantasma) e IllegalArgumentException (sem linha válida).
            log.warn("otica: falha ao criar encomenda p/ conversa {} ({}) — mensagem segue sem encomenda",
                conversationId, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Lê o bloco {@code rx} (todos os campos NULLABLE). Se NÃO há bloco rx OU {@code prescription_pending}
     * = true → pending=true. O backend NÃO valida/interpreta o grau — só registra o que veio.
     */
    private OticaPrescription parsePrescription(JsonNode root) {
        boolean explicitPending = root.path("prescription_pending").asBoolean(false);
        JsonNode rxNode = root.path("rx");
        if (rxNode.isMissingNode() || rxNode.isNull() || !rxNode.isObject()) {
            // Sem grau → pendente (a loja confirma a receita no painel).
            return OticaPrescription.pendingEmpty();
        }
        JsonNode od = rxNode.path("od");
        JsonNode oe = rxNode.path("oe");
        OticaPrescription rx = new OticaPrescription(
            decimal(od.path("spherical")), decimal(od.path("cylindrical")), integer(od.path("axis")),
            decimal(oe.path("spherical")), decimal(oe.path("cylindrical")), integer(oe.path("axis")),
            decimal(rxNode.path("pd")),
            explicitPending);
        return rx;
    }

    private static BigDecimal decimal(JsonNode n) {
        if (n == null || n.isMissingNode() || n.isNull()) {
            return null;
        }
        try {
            return new BigDecimal(n.asText());
        } catch (NumberFormatException e) {
            return null;   // grau ilegível → não persiste número (a IA não interpreta).
        }
    }

    private static Integer integer(JsonNode n) {
        if (n == null || n.isMissingNode() || n.isNull()) {
            return null;
        }
        try {
            return Integer.valueOf(n.asText());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
