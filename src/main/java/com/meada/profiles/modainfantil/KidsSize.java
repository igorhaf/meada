package com.meada.profiles.modainfantil;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Eixo de TAMANHO das variantes do perfil moda_infantil (camada 8.22), MATERIALIZADO — espelho 1:1 de
 * {@code frontend/profiles/moda-infantil/kids-size.ts} ({@code KidsSizeParityTest}). Mesmo padrão de
 * enum hardcoded+parity do {@link com.meada.profiles.lingerie.LingerieSize}.
 *
 * <p>⭐ ESCAPADA da camada (vs lingerie, cujos tamanhos eram PP..XGG): aqui o eixo de tamanho são
 * FAIXAS ETÁRIAS (age bands: RN, 0-3m, 3-6m, ..., 10a, 12a). A outra dimensão da variante,
 * {@code color}, continua TEXTO LIVRE — não há enum de cor. O size validado aqui é a defesa app-level
 * ao cadastrar uma variante; a CHECK do banco em {@code moda_infantil_variants.size} é apenas de
 * comprimento (1..20), então a validação real do conjunto de tamanhos é deste enum.
 *
 * <p>⭐ {@link #suggestForAgeMonths(int)}: dado a idade da criança em meses, sugere a melhor faixa
 * etária — a IA usa pra sugerir um tamanho a partir da idade informada pela cliente. Cada banda tem
 * um teto exclusivo em meses; a banda escolhida é a primeira cujo teto > idade (a última, 12a, é o
 * topo e acolhe qualquer idade maior).
 */
public enum KidsSize {
    RN("RN", 1),        // recém-nascido: < 1 mês
    M0_3("0-3m", 3),    // 0 a 3 meses: < 3 meses
    M3_6("3-6m", 6),    // 3 a 6 meses: < 6 meses
    M6_9("6-9m", 9),    // 6 a 9 meses: < 9 meses
    M9_12("9-12m", 12), // 9 a 12 meses: < 12 meses
    A1("1a", 24),       // 1 ano: < 24 meses
    A2("2a", 36),       // 2 anos: < 36 meses
    A3("3a", 48),       // 3 anos: < 48 meses
    A4("4a", 72),       // 4 anos: < 72 meses (até ~5 anos e meio)
    A6("6a", 96),       // 6 anos: < 96 meses
    A8("8a", 120),      // 8 anos: < 120 meses
    A10("10a", 144),    // 10 anos: < 144 meses
    A12("12a", Integer.MAX_VALUE);  // 12 anos: topo (qualquer idade maior)

    private final String id;
    private final int upperBoundMonthsExclusive;

    KidsSize(String id, int upperBoundMonthsExclusive) {
        this.id = id;
        this.upperBoundMonthsExclusive = upperBoundMonthsExclusive;
    }

    /** O id é também o rótulo curto exibido (RN, 0-3m, 1a, ...). */
    public String id() {
        return id;
    }

    public String label() {
        return id;
    }

    /** Resolve um tamanho/faixa etária pelo id estável. Optional vazio se inválido/null. */
    public static Optional<KidsSize> fromId(String id) {
        if (id == null) {
            return Optional.empty();
        }
        return Arrays.stream(values()).filter(s -> s.id.equals(id)).findFirst();
    }

    /** Todos os tamanhos/faixas, na ordem de declaração (ordem etária crescente = ordem de exibição). */
    public static List<KidsSize> allActive() {
        return List.of(values());
    }

    /**
     * ⭐ Sugere a melhor faixa etária para uma criança de {@code months} meses. Idade negativa é
     * tratada como 0 (RN). Percorre as bandas na ordem etária e devolve a primeira cujo teto
     * (exclusivo) seja maior que a idade — ex.: 0→RN, 2→0-3m, 5→3-6m, 18→1a, 24→2a, 120→10a. Acima da
     * última banda definida, devolve A12 (12a), o topo.
     */
    public static KidsSize suggestForAgeMonths(int months) {
        int m = Math.max(months, 0);
        for (KidsSize size : values()) {
            if (m < size.upperBoundMonthsExclusive) {
                return size;
            }
        }
        return A12;   // inalcançável (A12 tem teto MAX_VALUE), mas mantém o compilador/contrato felizes.
    }
}
