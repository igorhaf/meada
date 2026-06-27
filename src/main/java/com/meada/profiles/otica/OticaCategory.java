package com.meada.profiles.otica;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Categorias do catálogo do perfil otica (camada 8.12, FLUXO B — encomenda de óculos),
 * MATERIALIZADAS — espelho 1:1 de {@code frontend/profiles/otica/otica-categories.ts}. Clone de
 * {@link com.meada.profiles.floricultura.FloriculturaCategory}. O
 * {@code OticaCategoryParityTest} garante que os dois nunca divergem. A CHECK constraint de
 * {@code otica_catalog_items.category} (migration 56) trava os mesmos ids no banco.
 *
 * <p>{@code id} é a string estável (persistida, ASCII sem acento) e {@code label} é o rótulo pt-BR
 * exibido.
 */
public enum OticaCategory {
    ARMACOES("armacoes", "Armações"),
    LENTES("lentes", "Lentes"),
    ACESSORIOS("acessorios", "Acessórios");

    private final String id;
    private final String label;

    OticaCategory(String id, String label) {
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
    public static Optional<OticaCategory> fromId(String id) {
        if (id == null) {
            return Optional.empty();
        }
        return Arrays.stream(values()).filter(c -> c.id.equals(id)).findFirst();
    }

    /** Todas as categorias, na ordem de declaração (ordem de exibição). */
    public static List<OticaCategory> allActive() {
        return List.of(values());
    }
}
