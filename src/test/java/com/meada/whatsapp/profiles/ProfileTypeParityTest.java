package com.meada.whatsapp.profiles;

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
 * Paridade 1:1 entre o enum Java {@link ProfileType} e o catálogo TS
 * {@code frontend/lib/profiles/profile-type.ts} (camada 7.0). O cliente final vê o produto
 * via frontend; o backend decide tom/validação via enum — se os dois divergirem, um perfil
 * existe num lado e não no outro, e o produto fica inconsistente. Este teste falha cedo (no
 * build) com mensagem clara apontando QUAL lado tem id sobrando.
 *
 * <p>Extrai os ids do TS via regex sobre o array PROFILES. O backend roda com cwd = raiz do
 * módulo Maven (a raiz do repo aqui), então o caminho relativo a frontend/ resolve.
 */
class ProfileTypeParityTest {

    private static final Path TS_FILE =
        Path.of("frontend", "lib", "profiles", "profile-type.ts");

    // Captura o id de cada entrada do array PROFILES: { id: 'xxx', productName: ... }
    private static final Pattern ID_PATTERN = Pattern.compile("\\{\\s*id:\\s*'([a-z0-9-]+)'");

    @Test
    @DisplayName("o conjunto de ids do enum Java == conjunto de ids do TS")
    void javaTsParity() throws IOException {
        assertThat(Files.exists(TS_FILE))
            .as("arquivo TS de perfis não encontrado em %s (cwd=%s)",
                TS_FILE, Path.of("").toAbsolutePath())
            .isTrue();

        String ts = Files.readString(TS_FILE);
        Set<String> tsIds = new LinkedHashSet<>();
        Matcher m = ID_PATTERN.matcher(ts);
        while (m.find()) {
            tsIds.add(m.group(1));
        }

        Set<String> javaIds = Arrays.stream(ProfileType.values())
            .map(ProfileType::id)
            .collect(Collectors.toCollection(LinkedHashSet::new));

        assertThat(tsIds)
            .as("ids do TS não-vazios — o regex deve ter casado o array PROFILES")
            .isNotEmpty();

        // Diferenças nos dois sentidos, com mensagem acionável.
        Set<String> onlyInJava = new LinkedHashSet<>(javaIds);
        onlyInJava.removeAll(tsIds);
        Set<String> onlyInTs = new LinkedHashSet<>(tsIds);
        onlyInTs.removeAll(javaIds);

        assertThat(onlyInJava)
            .as("ids no enum Java mas AUSENTES no TS (adicione-os em "
                + "frontend/lib/profiles/profile-type.ts): %s", onlyInJava)
            .isEmpty();
        assertThat(onlyInTs)
            .as("ids no TS mas AUSENTES no enum Java (adicione-os em "
                + "ProfileType.java): %s", onlyInTs)
            .isEmpty();
    }
}
