package com.meada.profiles.modainfantil;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testa o helper ⭐ {@link KidsSize#suggestForAgeMonths(int)} (camada 8.22 / perfil moda_infantil): a
 * sugestão idade(meses)→faixa-etária que a IA usa pra recomendar um tamanho a partir da idade da
 * criança. Unitário puro (sem contexto Spring). Cobre as fronteiras de cada banda + idade negativa.
 */
class KidsSizeTest {

    @ParameterizedTest(name = "{0} meses → {1}")
    @CsvSource({
        "0, RN",
        "2, M0_3",
        "3, M3_6",
        "5, M3_6",
        "6, M6_9",
        "9, M9_12",
        "11, M9_12",
        "12, A1",
        "18, A1",
        "23, A1",
        "24, A2",
        "35, A2",
        "36, A3",
        "47, A3",
        "48, A4",
        "71, A4",
        "72, A6",
        "95, A6",
        "96, A8",
        "119, A8",
        "120, A10",
        "143, A10",
        "144, A12",
        "240, A12"
    })
    @DisplayName("suggestForAgeMonths devolve a faixa etária correta para idades de amostra")
    void suggestForAgeMonths(int months, KidsSize expected) {
        assertThat(KidsSize.suggestForAgeMonths(months)).isEqualTo(expected);
    }

    @Test
    @DisplayName("idade negativa é tratada como 0 → RN")
    void negativeAge_treatedAsRn() {
        assertThat(KidsSize.suggestForAgeMonths(-5)).isEqualTo(KidsSize.RN);
    }

    @Test
    @DisplayName("fromId resolve ids válidos e rejeita inválidos/null")
    void fromId() {
        assertThat(KidsSize.fromId("1a")).contains(KidsSize.A1);
        assertThat(KidsSize.fromId("0-3m")).contains(KidsSize.M0_3);
        assertThat(KidsSize.fromId("RN")).contains(KidsSize.RN);
        assertThat(KidsSize.fromId("XGG")).isEmpty();   // tamanho de lingerie, não de moda infantil.
        assertThat(KidsSize.fromId(null)).isEmpty();
    }
}
