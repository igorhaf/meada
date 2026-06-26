package com.meada.whatsapp.profiles.papelaria;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Categorias de catálogo do perfil papelaria (camada 8.15 / perfil papelaria), MATERIALIZADAS —
 * espelho 1:1 de {@code frontend/profiles/papelaria/papelaria-categories.ts}. Clone de
 * {@link com.meada.whatsapp.profiles.padaria.PadariaCategory} (camada 8.8). O
 * {@code PapelariaCategoryParityTest} garante que os dois nunca divergem (mesmo padrão do ProfileType
 * da SM-A). A CHECK constraint de {@code papelaria_catalog_items.category} (migration 59) trava os
 * mesmos ids no banco.
 *
 * <p>{@code id} é a string estável (persistida, ASCII sem acento) e {@code label} é o rótulo pt-BR
 * exibido. {@code convites} costuma reunir os itens sob encomenda (made_to_order).
 */
public enum PapelariaCategory {
    CONVITES("convites", "Convites"),
    SAVE_THE_DATE("save_the_date", "Save the Date"),
    CARTOES("cartoes", "Cartões"),
    PAPELARIA("papelaria", "Papelaria"),
    ADESIVOS("adesivos", "Adesivos"),
    EMBALAGENS("embalagens", "Embalagens");

    private final String id;
    private final String label;

    PapelariaCategory(String id, String label) {
        this.id = id;
        this.label = label;
    }

    public String id() {
        return id;
    }

    public String label() {
        return label;
    }

    /** Resolve uma categoria pelo id estável. Optional vazio se inválido/null. */
    public static Optional<PapelariaCategory> fromId(String id) {
        if (id == null) {
            return Optional.empty();
        }
        return Arrays.stream(values()).filter(c -> c.id.equals(id)).findFirst();
    }

    /** Todas as categorias, na ordem de declaração (ordem de exibição). */
    public static List<PapelariaCategory> allActive() {
        return List.of(values());
    }
}
