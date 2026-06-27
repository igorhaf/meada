package com.meada.profiles.lingerie;

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
 * Paridade 1:1 entre {@link LingerieSize} (Java) e {@code frontend/profiles/lingerie/
 * lingerie-size.ts} (camada 8.21 / perfil lingerie). É o eixo de TAMANHO da grade de variantes
 * (a outra dimensão, cor, é texto livre — sem enum). Mesmo padrão do LingerieCategoryParityTest:
 * casa cada objeto {@code { id: '...' }} do array contra os ids do enum. Aqui os ids têm letras
 * MAIÚSCULAS (PP, GG, XGG), então o regex aceita {@code [A-Z]}.
 */
class LingerieSizeParityTest {

    private static final Path TS_FILE =
        Path.of("frontend", "profiles", "lingerie", "lingerie-size.ts");

    private static final Pattern ID_PATTERN = Pattern.compile("\\{\\s*id:\\s*'([A-Z0-9]+)'");

    @Test
    @DisplayName("ids do enum LingerieSize == ids do lingerie-size.ts")
    void javaTsParity() throws IOException {
        assertThat(Files.exists(TS_FILE)).as("arquivo TS de tamanhos não encontrado em %s", TS_FILE).isTrue();

        String ts = Files.readString(TS_FILE);
        Set<String> tsIds = new LinkedHashSet<>();
        Matcher m = ID_PATTERN.matcher(ts);
        while (m.find()) {
            tsIds.add(m.group(1));
        }

        Set<String> javaIds = Arrays.stream(LingerieSize.values())
            .map(LingerieSize::id)
            .collect(Collectors.toCollection(LinkedHashSet::new));

        assertThat(tsIds).as("ids do TS não-vazios — o regex deve ter casado o array LINGERIE_SIZES").isNotEmpty();

        Set<String> onlyInJava = new LinkedHashSet<>(javaIds);
        onlyInJava.removeAll(tsIds);
        Set<String> onlyInTs = new LinkedHashSet<>(tsIds);
        onlyInTs.removeAll(javaIds);

        assertThat(onlyInJava).as("ids no enum Java mas AUSENTES no TS: %s", onlyInJava).isEmpty();
        assertThat(onlyInTs).as("ids no TS mas AUSENTES no enum Java: %s", onlyInTs).isEmpty();
    }
}
