package com.meada.whatsapp.profiles.lingerie;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Categorias de produto do perfil lingerie (camada 8.21, moda íntima · varejo), MATERIALIZADAS —
 * espelho 1:1 de {@code frontend/profiles/lingerie/lingerie-categories.ts}. O
 * {@code LingerieCategoryParityTest} garante que os dois nunca divergem (mesmo padrão do
 * {@link com.meada.whatsapp.profiles.adega.AdegaCategory}). A CHECK constraint de
 * {@code lingerie_products.category} (migration 65) trava os mesmos ids no banco.
 *
 * <p>{@code id} é a string estável (persistida, ASCII sem acento) e {@code label} é o rótulo pt-BR
 * exibido.
 */
public enum LingerieCategory {
    SUTIAS("sutias", "Sutiãs"),
    CALCINHAS("calcinhas", "Calcinhas"),
    CONJUNTOS("conjuntos", "Conjuntos"),
    PIJAMAS("pijamas", "Pijamas"),
    MODELADORES("modeladores", "Modeladores"),
    MEIAS("meias", "Meias"),
    ACESSORIOS("acessorios", "Acessórios");

    private final String id;
    private final String label;

    LingerieCategory(String id, String label) {
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
    public static Optional<LingerieCategory> fromId(String id) {
        if (id == null) {
            return Optional.empty();
        }
        return Arrays.stream(values()).filter(c -> c.id.equals(id)).findFirst();
    }

    /** Todas as categorias, na ordem de declaração (ordem de exibição). */
    public static List<LingerieCategory> allActive() {
        return List.of(values());
    }
}
