package com.meada.profiles.modainfantil;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Paridade 1:1 entre {@link KidsSize} (Java) e {@code frontend/profiles/moda-infantil/kids-size.ts}
 * (camada 8.22 / perfil moda_infantil). É o eixo de TAMANHO (⭐ FAIXA ETÁRIA) da grade de variantes
 * (a outra dimensão, cor, é texto livre — sem enum). Mesmo padrão do ModaInfantilCategoryParityTest:
 * casa cada objeto {@code { id: '...' }} do array contra os ids do enum. Aqui os ids misturam letras
 * (RN, A...) e faixas com hífen+dígito (0-3m, 9-12m), então o regex aceita {@code [A-Za-z0-9-]}.
 */
class KidsSizeParityTest {

    private static final Path TS_FILE =
        Path.of("frontend", "profiles", "moda-infantil", "kids-size.ts");

    private static final Pattern ID_PATTERN = Pattern.compile("\\{\\s*id:\\s*'([A-Za-z0-9-]+)'");

    @Test
    @DisplayName("ids do enum KidsSize == ids do kids-size.ts")
    void javaTsParity() throws IOException {
        assertThat(Files.exists(TS_FILE)).as("arquivo TS de tamanhos não encontrado em %s", TS_FILE).isTrue();

        String ts = Files.readString(TS_FILE);
        Set<String> tsIds = new LinkedHashSet<>();
        Matcher m = ID_PATTERN.matcher(ts);
        while (m.find()) {
            tsIds.add(m.group(1));
        }

        Set<String> javaIds = Arrays.stream(KidsSize.values())
            .map(KidsSize::id)
            .collect(Collectors.toCollection(LinkedHashSet::new));

        assertThat(tsIds).as("ids do TS não-vazios — o regex deve ter casado o array KIDS_SIZES").isNotEmpty();

        Set<String> onlyInJava = new LinkedHashSet<>(javaIds);
        onlyInJava.removeAll(tsIds);
        Set<String> onlyInTs = new LinkedHashSet<>(tsIds);
        onlyInTs.removeAll(javaIds);

        assertThat(onlyInJava).as("ids no enum Java mas AUSENTES no TS: %s", onlyInJava).isEmpty();
        assertThat(onlyInTs).as("ids no TS mas AUSENTES no enum Java: %s", onlyInTs).isEmpty();
    }
}
