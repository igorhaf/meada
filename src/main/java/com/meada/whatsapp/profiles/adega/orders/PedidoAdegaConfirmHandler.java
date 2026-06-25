package com.meada.whatsapp.profiles.adega.orders;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.meada.whatsapp.profiles.adega.menu.AdegaMenuItem;
import com.meada.whatsapp.profiles.adega.menu.AdegaMenuItemRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extrai a tag {@code <pedido_adega>{...}</pedido_adega>} da resposta da IA, valida os itens
 * contra o cardápio do tenant e cria o pedido (camada 8.9). Clone do chassi comida + a ESCAPADA
 * +18 (trava de faixa etária na venda de álcool).
 *
 * <p>NÃO usa tool calling / responseSchema do Gemini (constraint: a API trata os dois como
 * mutuamente exclusivos, e o fluxo de outbound já usa responseSchema). A IA emite a tag em texto
 * livre; aqui parseamos via regex.
 *
 * <p><b>ESCAPADA +18 (trava de faixa etária):</b> a venda de bebida alcoólica exige confirmação de
 * MAIORIDADE. A tag carrega {@code age_confirmed} (boolean). Se ausente/false, o handler ABORTA SEM
 * criar pedido (devolve {@link Optional#empty()}) — NÃO há pedido "menor de idade" no banco. O flag
 * é parseado AQUI e propagado ao service/repo, que o persiste em {@code adega_orders.age_confirmed}
 * (NOT NULL) pra compliance. A trava vale mesmo pra carrinho 100% sem-álcool nesta SM.
 *
 * <p>O {@code total_cents} do JSON é DESCARTADO — o repositório recalcula (base + Σ deltas das
 * opções, defesa contra a IA chutar total). As OPÇÕES NÃO são validadas aqui — os optionIds passam
 * adiante; se o repo lançar {@code InvalidOptionException} ao criar (opção fantasma), o catch
 * devolve {@link Optional#empty()} (a mensagem da IA segue normal, sem pedido).
 */
@Component
public class PedidoAdegaConfirmHandler {

    private static final Logger log = LoggerFactory.getLogger(PedidoAdegaConfirmHandler.class);

    // <pedido_adega> ... </pedido_adega> — DOTALL para o JSON poder ter quebras de linha.
    private static final Pattern TAG = Pattern.compile(
        "<pedido_adega>\\s*(\\{.*?\\})\\s*</pedido_adega>", Pattern.DOTALL);

    private final ObjectMapper objectMapper;
    private final AdegaMenuItemRepository menuRepository;
    private final AdegaOrderService orderService;

    public PedidoAdegaConfirmHandler(ObjectMapper objectMapper, AdegaMenuItemRepository menuRepository,
                                      AdegaOrderService orderService) {
        this.objectMapper = objectMapper;
        this.menuRepository = menuRepository;
        this.orderService = orderService;
    }

    /** True se o texto contém a tag de pedido (decisão rápida sem parsear). */
    public boolean hasOrderTag(String text) {
        return text != null && TAG.matcher(text).find();
    }

    /** Remove a tag {@code <pedido_adega>...</pedido_adega>} do texto (para não enviá-la ao cliente). */
    public String stripOrderTag(String text) {
        if (text == null) {
            return null;
        }
        return TAG.matcher(text).replaceAll("").stripTrailing();
    }

    /**
     * Extrai a tag, valida os itens e cria o pedido. {@link Optional#empty()} quando: não há tag,
     * JSON inválido, nenhum item válido (item_id inexistente ou indisponível), endereço ausente, ou
     * o repo recusa por opção inválida.
     */
    public Optional<AdegaOrder> parseAndCreate(UUID companyId, UUID conversationId, UUID contactId,
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
            log.warn("adega: tag <pedido_adega> com JSON inválido p/ conversa {} ({}) — pedido não criado",
                conversationId, e.getMessage());
            return Optional.empty();
        }

        // ESCAPADA +18: a venda de álcool exige maioridade. Sem age_confirmed=true a tag é ABORTADA
        // (não há pedido de menor no banco). Aceita só o booleano true — qualquer outra coisa = false.
        boolean ageConfirmed = root.path("age_confirmed").asBoolean(false);
        if (!ageConfirmed) {
            log.warn("adega: tag <pedido_adega> SEM age_confirmed=true p/ conversa {} — trava +18, pedido não criado",
                conversationId);
            return Optional.empty();
        }

        String endereco = root.path("endereco").asText(null);
        if (endereco == null || endereco.isBlank()) {
            log.warn("adega: tag <pedido_adega> sem endereço p/ conversa {} — pedido não criado", conversationId);
            return Optional.empty();
        }

        JsonNode itemsNode = root.path("items");
        if (!itemsNode.isArray() || itemsNode.isEmpty()) {
            log.warn("adega: tag <pedido_adega> sem items p/ conversa {} — pedido não criado", conversationId);
            return Optional.empty();
        }

        // Valida cada item: existe no cardápio do tenant E está disponível. As opções NÃO são
        // validadas aqui (passam adiante; o repo recusa opção fantasma na criação).
        List<OrderLineInput> lines = new ArrayList<>();
        for (JsonNode itemNode : itemsNode) {
            String rawId = itemNode.path("item_id").asText(null);
            int qtd = itemNode.path("qtd").asInt(0);
            if (rawId == null || qtd <= 0) {
                log.warn("adega: item inválido (id/qtd) na tag <pedido_adega> p/ conversa {} — pedido não criado",
                    conversationId);
                return Optional.empty();
            }
            UUID itemId;
            try {
                itemId = UUID.fromString(rawId);
            } catch (IllegalArgumentException e) {
                log.warn("adega: item_id não-UUID '{}' na tag <pedido_adega> p/ conversa {} — pedido não criado",
                    rawId, conversationId);
                return Optional.empty();
            }
            Optional<AdegaMenuItem> menuItem = menuRepository.findById(companyId, itemId);
            if (menuItem.isEmpty() || !menuItem.get().available()) {
                log.warn("adega: item {} inexistente/indisponível na tag <pedido_adega> p/ conversa {} — pedido não criado",
                    itemId, conversationId);
                return Optional.empty();
            }

            // Parse das opções (array de UUIDs em string; opcional — item sem opção → lista vazia).
            List<UUID> optionIds = new ArrayList<>();
            JsonNode optionsNode = itemNode.path("options");
            if (optionsNode.isArray()) {
                for (JsonNode optNode : optionsNode) {
                    String rawOpt = optNode.asText(null);
                    if (rawOpt == null || rawOpt.isBlank()) {
                        continue;
                    }
                    try {
                        optionIds.add(UUID.fromString(rawOpt));
                    } catch (IllegalArgumentException e) {
                        log.warn("adega: option_id não-UUID '{}' na tag <pedido_adega> p/ conversa {} — pedido não criado",
                            rawOpt, conversationId);
                        return Optional.empty();
                    }
                }
            }
            lines.add(new OrderLineInput(itemId, qtd, optionIds));
        }

        try {
            AdegaOrder order = orderService.create(companyId, conversationId, contactId,
                endereco.strip(), lines, ageConfirmed, null);
            log.info("adega: pedido {} criado p/ conversa {} ({} itens, total {} cents)",
                order.id(), conversationId, lines.size(), order.totalCents());
            return Optional.of(order);
        } catch (RuntimeException e) {
            // Inclui InvalidOptionException (opção fantasma) e IllegalArgumentException (sem linha válida).
            log.warn("adega: falha ao criar pedido p/ conversa {} ({}) — mensagem segue sem pedido",
                conversationId, e.getMessage());
            return Optional.empty();
        }
    }
}
