package com.meada.whatsapp.profiles.pizzaria;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Categorias de cardápio do perfil pizzaria (camada 8.4, delivery estilo iFood), MATERIALIZADAS —
 * espelho 1:1 de {@code frontend/profiles/pizzaria/pizzaria-categories.ts}. Clone de
 * {@link com.meada.whatsapp.profiles.sushi.SushiCategory}. O {@code PizzaCategoryParityTest}
 * garante que os dois nunca divergem (mesmo padrão do ProfileType da SM-A). A CHECK constraint de
 * {@code pizzaria_menu_items.category} (migration 47) trava os mesmos ids no banco.
 *
 * <p>{@code id} é a string estável (persistida, ASCII sem acento) e {@code label} é o rótulo pt-BR
 * exibido.
 */
public enum PizzaCategory {
    PIZZAS_SALGADAS("pizzas_salgadas", "Pizzas Salgadas"),
    PIZZAS_DOCES("pizzas_doces", "Pizzas Doces"),
    BORDAS("bordas", "Bordas"),
    BEBIDAS("bebidas", "Bebidas"),
    SOBREMESAS("sobremesas", "Sobremesas"),
    COMBOS("combos", "Combos");

    private final String id;
    private final String label;

    PizzaCategory(String id, String label) {
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
    public static Optional<PizzaCategory> fromId(String id) {
        if (id == null) {
            return Optional.empty();
        }
        return Arrays.stream(values()).filter(c -> c.id.equals(id)).findFirst();
    }

    /** Todas as categorias, na ordem de declaração (ordem de exibição). */
    public static List<PizzaCategory> allActive() {
        return List.of(values());
    }
}
