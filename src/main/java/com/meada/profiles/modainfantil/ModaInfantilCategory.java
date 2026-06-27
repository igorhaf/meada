package com.meada.profiles.modainfantil;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Categorias de produto do perfil moda_infantil (camada 8.22, roupa de criança · varejo),
 * MATERIALIZADAS — espelho 1:1 de {@code frontend/profiles/moda-infantil/moda-infantil-categories.ts}.
 * O {@code ModaInfantilCategoryParityTest} garante que os dois nunca divergem (mesmo padrão do
 * {@link com.meada.profiles.lingerie.LingerieCategory}). A CHECK constraint de
 * {@code moda_infantil_products.category} (migration 66) trava os mesmos ids no banco.
 *
 * <p>{@code id} é a string estável (persistida, ASCII sem acento) e {@code label} é o rótulo pt-BR
 * exibido.
 */
public enum ModaInfantilCategory {
    BEBE("bebe", "Bebê"),
    MENINO("menino", "Menino"),
    MENINA("menina", "Menina"),
    CALCADOS("calcados", "Calçados"),
    ACESSORIOS("acessorios", "Acessórios"),
    PIJAMAS("pijamas", "Pijamas"),
    KITS("kits", "Kits");

    private final String id;
    private final String label;

    ModaInfantilCategory(String id, String label) {
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
    public static Optional<ModaInfantilCategory> fromId(String id) {
        if (id == null) {
            return Optional.empty();
        }
        return Arrays.stream(values()).filter(c -> c.id.equals(id)).findFirst();
    }

    /** Todas as categorias, na ordem de declaração (ordem de exibição). */
    public static List<ModaInfantilCategory> allActive() {
        return List.of(values());
    }
}
