package com.meada.profiles.lavanderia;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Categorias de serviço do perfil lavanderia (camada 8.10), MATERIALIZADAS — espelho 1:1 de
 * {@code frontend/profiles/lavanderia/lavanderia-categories.ts}. Clone de
 * {@link com.meada.profiles.floricultura.FloriculturaCategory}. O
 * {@code LavanderiaServiceCategoryParityTest} garante que os dois nunca divergem. A CHECK constraint de
 * {@code lavanderia_services.category} (migration 54) trava os mesmos ids no banco.
 *
 * <p>{@code id} é a string estável (persistida, ASCII sem acento) e {@code label} é o rótulo pt-BR.
 */
public enum LavanderiaServiceCategory {
    LAVAR("lavar", "Lavar"),
    LAVAR_PASSAR("lavar_passar", "Lavar e passar"),
    LAVAGEM_SECO("lavagem_seco", "Lavagem a seco"),
    PASSAR("passar", "Passar"),
    EDREDOM_PESADOS("edredom_pesados", "Edredom e pesados");

    private final String id;
    private final String label;

    LavanderiaServiceCategory(String id, String label) {
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
    public static Optional<LavanderiaServiceCategory> fromId(String id) {
        if (id == null) {
            return Optional.empty();
        }
        return Arrays.stream(values()).filter(c -> c.id.equals(id)).findFirst();
    }

    /** Todas as categorias, na ordem de declaração (ordem de exibição). */
    public static List<LavanderiaServiceCategory> allActive() {
        return List.of(values());
    }
}
