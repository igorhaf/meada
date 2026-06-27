package com.meada.profiles.adega;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Categorias de cardápio do perfil adega (camada 8.9, delivery de bebidas), MATERIALIZADAS —
 * espelho 1:1 de {@code frontend/profiles/adega/adega-categories.ts}. O {@code AdegaCategoryParityTest}
 * garante que os dois nunca divergem (mesmo padrão do ProfileType da SM-A). A CHECK constraint de
 * {@code adega_menu_items.category} (migration 53) trava os mesmos ids no banco.
 *
 * <p>{@code id} é a string estável (persistida, ASCII sem acento) e {@code label} é o rótulo pt-BR
 * exibido.
 */
public enum AdegaCategory {
    VINHOS("vinhos", "Vinhos"),
    ESPUMANTES("espumantes", "Espumantes"),
    CERVEJAS("cervejas", "Cervejas"),
    DESTILADOS("destilados", "Destilados"),
    SEM_ALCOOL("sem_alcool", "Sem Álcool"),
    ACESSORIOS("acessorios", "Acessórios");

    private final String id;
    private final String label;

    AdegaCategory(String id, String label) {
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
    public static Optional<AdegaCategory> fromId(String id) {
        if (id == null) {
            return Optional.empty();
        }
        return Arrays.stream(values()).filter(c -> c.id.equals(id)).findFirst();
    }

    /** Todas as categorias, na ordem de declaração (ordem de exibição). */
    public static List<AdegaCategory> allActive() {
        return List.of(values());
    }
}
