package com.meada.profiles.floricultura;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.meada.profiles.floricultura.catalog.FloriculturaCatalogItem;
import com.meada.profiles.floricultura.catalog.FloriculturaCatalogItemRepository;
import com.meada.profiles.floricultura.catalog.FloriculturaCatalogOption;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Cache do bloco de cardápio+config injetado no prompt do FloriculturaBot (camada 8.4). Clone de
 * {@link com.meada.profiles.sushi.SushiCatalogCache} (Caffeine TTL 60s) — {@link FloriculturaCatalogService}
 * chama {@link #invalidate} ao mutar item/opção/config, então a IA vê a mudança na hora.
 *
 * <p>DIFERENÇA do sushi (ESCAPADA 2): sob cada item, lista os grupos de opção e seus deltas com os
 * option_id EXATOS — a IA precisa deles para emitir a tag {@code <pedido_flor>}. Formato por item:
 * <pre>
 * - &lt;item_id&gt; · &lt;name&gt; · R$ &lt;base&gt;
 *     [&lt;group_label&gt;] &lt;opt_id&gt; &lt;option_label&gt; (+R$ &lt;delta&gt;) | ...
 * </pre>
 */
@Component
public class FloriculturaCatalogCache {

    private final FloriculturaCatalogItemRepository catalogRepository;
    private final FloriculturaConfigRepository configRepository;
    private final Cache<UUID, String> cache;

    public FloriculturaCatalogCache(FloriculturaCatalogItemRepository catalogRepository,
                           FloriculturaConfigRepository configRepository) {
        this.catalogRepository = catalogRepository;
        this.configRepository = configRepository;
        this.cache = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofSeconds(60))
            .maximumSize(500)
            .build();
    }

    /** Bloco de cardápio+config+instruções para o prompt, cacheado por company (TTL 60s). */
    public String catalogSegment(UUID companyId) {
        return cache.get(companyId, this::buildSegment);
    }

    /** Invalida o cache de uma empresa (chamado pelo FloriculturaCatalogService ao mutar). */
    public void invalidate(UUID companyId) {
        cache.invalidate(companyId);
    }

    private String buildSegment(UUID companyId) {
        List<FloriculturaCatalogItem> items = catalogRepository.listByCompany(companyId, null, true);
        FloriculturaConfig config = configRepository.findByCompany(companyId);

        StringBuilder sb = new StringBuilder();
        if (items.isEmpty()) {
            sb.append("CARDÁPIO DISPONÍVEL HOJE: (nenhum item disponível no momento — informe o "
                + "cliente que o cardápio está indisponível e ofereça avisá-lo quando voltar.)\n\n");
        } else {
            sb.append("CARDÁPIO DISPONÍVEL HOJE:\n");
            String currentCategory = null;
            for (FloriculturaCatalogItem it : items) {
                if (!it.category().equals(currentCategory)) {
                    currentCategory = it.category();
                    sb.append("[").append(FloriculturaCategory.fromId(currentCategory)
                        .map(FloriculturaCategory::label).orElse(currentCategory)).append("]\n");
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

        sb.append("INSTRUÇÕES DE PEDIDO:\n")
            .append("Flor é presente AGENDADO pra OUTRA pessoa. ANTES de fechar, você PRECISA ter: os "
                + "itens, o ENDEREÇO de entrega, a DATA de entrega (formato YYYY-MM-DD, hoje ou futura), "
                + "o PERÍODO ('manha' ou 'tarde'), o NOME de quem vai RECEBER, e (opcional) a mensagem do "
                + "CARTÃO. Quando o cliente CONFIRMAR o pedido E você tiver TODOS esses dados, sua ÚLTIMA "
                + "mensagem deve TERMINAR com a tag (em uma linha própria, sem markdown):\n")
            .append("<pedido_flor>{\"items\":[{\"item_id\":\"UUID_EXATO_DO_CATÁLOGO\",\"qtd\":N,"
                + "\"options\":[\"UUID_DA_OPCAO\"]}],\"endereco\":\"...\",\"data_entrega\":\"YYYY-MM-DD\","
                + "\"periodo\":\"manha\",\"destinatario\":\"Nome de quem recebe\",\"cartao\":\"mensagem ou vazio\","
                + "\"total_cents\":NNN}</pedido_flor>\n")
            .append("Cada item pode ter \"options\" (UUIDs das opções de cor/tamanho escolhidas); item "
                + "sem opção → omita \"options\". Use os item_id e option_id EXATOS do catálogo acima. "
                + "ANTES da tag, escreva a confirmação humana normal (\"Confirmado: 1 Buquê de Rosas "
                + "(Grande), entrega 25/12 de manhã para Maria, na Rua Y, com cartão. Total R$ X.\"). NÃO "
                + "emita a tag sem TODOS os dados (itens+endereço+data+período+destinatário) — só na "
                + "confirmação final completa. A data NÃO pode ser no passado.\n");

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
        sb.append("Avise o cliente que o pedido será enviado para confirmação do restaurante.\n\n");

        return sb.toString();
    }

    /**
     * Lista as opções available=true do item, agrupadas por group_label (ordem de aparição já vem
     * por sort_order do repositório), uma linha por grupo: {@code [grupo] opt_id label (+R$ delta) | ...}.
     */
    private void appendOptions(StringBuilder sb, List<FloriculturaCatalogOption> options) {
        if (options == null || options.isEmpty()) {
            return;
        }
        Map<String, StringBuilder> byGroup = new LinkedHashMap<>();
        for (FloriculturaCatalogOption opt : options) {
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
