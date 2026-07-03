package com.meada.profiles.concessionaria.wishlists;

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
 * Extrai a tag {@code <desejo_carro>{...}</desejo_carro>} da resposta da IA e registra a lista de
 * desejos (onda 1 da concessionária, backlog #1). Namespace próprio, distinto de
 * {@code <testdrive_carro>}/{@code <lead_carro>} e de todas as outras.
 *
 * <p>Campos: {@code brand}/{@code model} (pelo menos um), {@code max_price_cents} e {@code min_year}
 * OPCIONAIS — o teto de preço é o que o CLIENTE declarou (critério de busca, não precificação; a IA
 * continua sem fechar preço). Qualquer falha → {@link Optional#empty()} + warn (best-effort).
 */
@Component
public class DesejoCarroConfirmHandler {

    private static final Logger log = LoggerFactory.getLogger(DesejoCarroConfirmHandler.class);

    private static final Pattern TAG = Pattern.compile(
        "<desejo_carro>\\s*(\\{.*?\\})\\s*</desejo_carro>", Pattern.DOTALL);

    private final ObjectMapper objectMapper;
    private final ConcessionariaWishlistService wishlistService;

    public DesejoCarroConfirmHandler(ObjectMapper objectMapper,
                                     ConcessionariaWishlistService wishlistService) {
        this.objectMapper = objectMapper;
        this.wishlistService = wishlistService;
    }

    public boolean hasDesejoTag(String text) {
        return text != null && TAG.matcher(text).find();
    }

    public String stripDesejoTag(String text) {
        if (text == null) {
            return null;
        }
        return TAG.matcher(text).replaceAll("").stripTrailing();
    }

    public Optional<ConcessionariaWishlist> parseAndCreate(UUID companyId, UUID conversationId,
                                                           UUID contactId, String aiResponseText) {
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
            log.warn("concessionaria: tag <desejo_carro> com JSON inválido p/ conversa {} ({}) — não criado",
                conversationId, e.getMessage());
            return Optional.empty();
        }

        String brand = root.path("brand").asText(null);
        String model = root.path("model").asText(null);
        Integer maxPrice = root.hasNonNull("max_price_cents") ? root.path("max_price_cents").asInt() : null;
        Integer minYear = root.hasNonNull("min_year") ? root.path("min_year").asInt() : null;
        String notes = root.path("notes").asText(null);

        try {
            ConcessionariaWishlist w = wishlistService.create(companyId, null, contactId, conversationId,
                brand, model, maxPrice, minYear, notes);
            log.info("concessionaria: desejo {} registrado p/ conversa {} ({} {})",
                w.id(), conversationId, w.brand(), w.model());
            return Optional.of(w);
        } catch (RuntimeException e) {
            log.warn("concessionaria: falha ao registrar desejo p/ conversa {} ({}) — mensagem segue sem registro",
                conversationId, e.getMessage());
            return Optional.empty();
        }
    }
}
