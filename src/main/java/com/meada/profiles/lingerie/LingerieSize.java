package com.meada.profiles.lingerie;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Eixo de TAMANHO das variantes do perfil lingerie (camada 8.21), MATERIALIZADO — espelho 1:1 de
 * {@code frontend/profiles/lingerie/lingerie-size.ts} ({@code LingerieSizeParityTest}). Mesmo padrão
 * de enum hardcoded+parity do {@link LingerieCategory}.
 *
 * <p>É o eixo de tamanho de uma variante (a outra dimensão, {@code color}, é TEXTO LIVRE — não há
 * enum de cor). O size validado aqui é a defesa app-level ao cadastrar uma variante; a CHECK do
 * banco em {@code lingerie_variants.size} é apenas de comprimento (1..20), então a validação real do
 * conjunto de tamanhos é deste enum.
 */
public enum LingerieSize {
    PP("PP"),
    P("P"),
    M("M"),
    G("G"),
    GG("GG"),
    XGG("XGG");

    private final String id;

    LingerieSize(String id) {
        this.id = id;
    }

    /** O id é também o rótulo exibido (PP, P, M, ...). */
    public String id() {
        return id;
    }

    public String label() {
        return id;
    }

    /** Resolve um tamanho pelo id estável. Optional vazio se inválido/null. */
    public static Optional<LingerieSize> fromId(String id) {
        if (id == null) {
            return Optional.empty();
        }
        return Arrays.stream(values()).filter(s -> s.id.equals(id)).findFirst();
    }

    /** Todos os tamanhos, na ordem de declaração (ordem de exibição). */
    public static List<LingerieSize> allActive() {
        return List.of(values());
    }
}
