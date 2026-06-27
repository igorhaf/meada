package com.meada.profiles.adega;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.meada.profiles.adega.menu.AdegaMenuItem;
import com.meada.profiles.adega.menu.AdegaMenuItemRepository;
import com.meada.profiles.adega.menu.AdegaMenuOption;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Cache do bloco de cardápio+config injetado no prompt do AdegaBot (camada 8.4). Clone de
 * {@link com.meada.profiles.sushi.SushiMenuCache} (Caffeine TTL 60s) — {@link AdegaMenuService}
 * chama {@link #invalidate} ao mutar item/opção/config, então a IA vê a mudança na hora.
 *
 * <p>DIFERENÇA do sushi (ESCAPADA 2): sob cada item, lista os grupos de opção e seus deltas com os
 * option_id EXATOS — a IA precisa deles para emitir a tag {@code <pedido_adega>}. Formato por item:
 * <pre>
 * - &lt;item_id&gt; · &lt;name&gt; · R$ &lt;base&gt;
 *     [&lt;group_label&gt;] &lt;opt_id&gt; &lt;option_label&gt; (+R$ &lt;delta&gt;) | ...
 * </pre>
 */
@Component
public class AdegaMenuCache {

    private final AdegaMenuItemRepository menuRepository;
    private final AdegaConfigRepository configRepository;
    private final Cache<UUID, String> cache;

    public AdegaMenuCache(AdegaMenuItemRepository menuRepository,
                           AdegaConfigRepository configRepository) {
        this.menuRepository = menuRepository;
        this.configRepository = configRepository;
        this.cache = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofSeconds(60))
            .maximumSize(500)
            .build();
    }

    /** Bloco de cardápio+config+instruções para o prompt, cacheado por company (TTL 60s). */
    public String menuSegment(UUID companyId) {
        return cache.get(companyId, this::buildSegment);
    }

    /** Invalida o cache de uma empresa (chamado pelo AdegaMenuService ao mutar). */
    public void invalidate(UUID companyId) {
        cache.invalidate(companyId);
    }

    private String buildSegment(UUID companyId) {
        List<AdegaMenuItem> items = menuRepository.listByCompany(companyId, null, true);
        AdegaConfig config = configRepository.findByCompany(companyId);

        StringBuilder sb = new StringBuilder();
        if (items.isEmpty()) {
            sb.append("CARDÁPIO DISPONÍVEL HOJE: (nenhum item disponível no momento — informe o "
                + "cliente que o cardápio está indisponível e ofereça avisá-lo quando voltar.)\n\n");
        } else {
            sb.append("CARDÁPIO DISPONÍVEL HOJE:\n");
            String currentCategory = null;
            for (AdegaMenuItem it : items) {
                if (!it.category().equals(currentCategory)) {
                    currentCategory = it.category();
                    sb.append("[").append(AdegaCategory.fromId(currentCategory)
                        .map(AdegaCategory::label).orElse(currentCategory)).append("]\n");
                }
                sb.append("- ").append(it.id()).append(" · ").append(it.name())
                    .append(" · R$ ").append(formatBrl(it.priceCents()));
                if (it.description() != null && !it.description().isBlank()) {
                    sb.append(" · ").append(it.description().strip());
                }
                sb.append("\n");
                appendOptions(sb, it.options());
            }
            sb.append("\n");
        }

        sb.append("REGRA +18 (OBRIGATÓRIA — venda de bebida alcoólica): ANTES de fechar QUALQUER "
                + "pedido, confirme que o cliente é MAIOR DE 18 ANOS (pergunte de forma cordial se ele "
                + "confirma ser maior de idade). Se o cliente declarar ou indicar ser MENOR de 18, "
                + "RECUSE com gentileza, NÃO emita a tag e explique que a venda de bebida é proibida "
                + "para menores. Só emita a tag com \"age_confirmed\":true depois que o cliente "
                + "confirmar a maioridade. NUNCA invente um rótulo/marca/safra/volume/preço fora do "
                + "cardápio. Inclua \"Beba com moderação\" e NUNCA incentive consumo excessivo.\n");

        sb.append("INSTRUÇÕES DE PEDIDO:\n")
            .append("Quando o cliente CONFIRMAR o pedido (frases como \"pode mandar\", \"confirma\", "
                + "\"tá certo\", \"fechou\"), JÁ tiver confirmado a MAIORIDADE e informado o endereço "
                + "de entrega, sua ÚLTIMA mensagem deve TERMINAR com a tag (em uma linha própria, sem "
                + "markdown):\n")
            .append("<pedido_adega>{\"age_confirmed\":true,\"items\":[{\"item_id\":\"UUID_EXATO_DO_"
                + "CARDÁPIO\",\"qtd\":N,\"options\":[\"UUID_DA_OPCAO\"]}],\"endereco\":\"...\","
                + "\"total_cents\":NNN}</pedido_adega>\n")
            .append("O campo \"age_confirmed\":true é OBRIGATÓRIO — sem ele o pedido NÃO é criado "
                + "(trava de maioridade). Cada item pode ter \"options\" (lista de UUIDs das opções/"
                + "modifiers — Volume, Temperatura — escolhidas dos grupos acima); item sem opção → "
                + "omita \"options\" ou use lista vazia. Use os item_id e option_id EXATOS do cardápio "
                + "acima. ANTES da tag, escreva a confirmação humana normal (\"Confirmado: 1 Tinto "
                + "Reserva (1L) + 1 IPA gelada, total R$ X, entrega na Rua Y. Beba com moderação 🍷\"). "
                + "NÃO emita a tag enquanto o cliente ainda monta o pedido — só na confirmação final "
                + "COM maioridade confirmada E endereço.\n");

        if (config.deliveryFeeCents() > 0) {
            sb.append("Taxa de entrega: R$ ").append(formatBrl(config.deliveryFeeCents()))
                .append(" (some ao total).\n");
        }
        if (config.minOrderCents() > 0) {
            sb.append("Pedido mínimo: R$ ").append(formatBrl(config.minOrderCents()))
                .append(" (avise o cliente se o pedido ficar abaixo, mas não recuse — apenas oriente).\n");
        }
        sb.append("CONFIG: delivery_fee_cents=").append(config.deliveryFeeCents())
            .append(", min_order_cents=").append(config.minOrderCents()).append("\n");
        sb.append("Avise o cliente que o pedido será enviado para confirmação da loja.\n\n");

        return sb.toString();
    }

    /**
     * Lista as opções available=true do item, agrupadas por group_label (ordem de aparição já vem
     * por sort_order do repositório), uma linha por grupo: {@code [grupo] opt_id label (+R$ delta) | ...}.
     */
    private void appendOptions(StringBuilder sb, List<AdegaMenuOption> options) {
        if (options == null || options.isEmpty()) {
            return;
        }
        Map<String, StringBuilder> byGroup = new LinkedHashMap<>();
        for (AdegaMenuOption opt : options) {
            if (!opt.available()) {
                continue;
            }
            StringBuilder line = byGroup.computeIfAbsent(opt.groupLabel(), g -> new StringBuilder());
            if (line.length() > 0) {
                line.append(" | ");
            }
            line.append(opt.id()).append(" ").append(opt.optionLabel())
                .append(" (+R$ ").append(formatBrl(opt.priceDeltaCents())).append(")");
        }
        for (Map.Entry<String, StringBuilder> e : byGroup.entrySet()) {
            sb.append("    [").append(e.getKey()).append("] ").append(e.getValue()).append("\n");
        }
    }

    private static String formatBrl(int cents) {
        return String.format("%d,%02d", cents / 100, cents % 100);
    }
}
