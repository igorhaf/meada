package com.meada.whatsapp.profiles.restaurant;

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
 * Paridade 1:1 entre {@link ReservationStatus} (Java) e {@code frontend/profiles/restaurant/
 * reservation-status.ts} (camada 7.3). Mesmo padrão do SushiCategoryParityTest: se os conjuntos
 * de ids divergirem, um status existe num lado e não no outro — e o produto fica inconsistente
 * (o Kanban/agenda usaria status que o backend não aceita, ou vice-versa). Falha cedo (build)
 * com mensagem acionável.
 */
class ReservationStatusParityTest {

    private static final Path TS_FILE =
        Path.of("frontend", "profiles", "restaurant", "reservation-status.ts");

    // Casa apenas os ids do array RESERVATION_STATUSES (linhas "{ id: '...', label: ... }").
    private static final Pattern ID_PATTERN = Pattern.compile("\\{\\s*id:\\s*'([a-z0-9_]+)'");

    @Test
    @DisplayName("ids do enum ReservationStatus == ids do reservation-status.ts")
    void javaTsParity() throws IOException {
        assertThat(Files.exists(TS_FILE))
            .as("arquivo TS de status não encontrado em %s (cwd=%s)",
                TS_FILE, Path.of("").toAbsolutePath())
            .isTrue();

        String ts = Files.readString(TS_FILE);
        Set<String> tsIds = new LinkedHashSet<>();
        Matcher m = ID_PATTERN.matcher(ts);
        while (m.find()) {
            tsIds.add(m.group(1));
        }

        Set<String> javaIds = Arrays.stream(ReservationStatus.values())
            .map(ReservationStatus::id)
            .collect(Collectors.toCollection(LinkedHashSet::new));

        assertThat(tsIds)
            .as("ids do TS não-vazios — o regex deve ter casado o array RESERVATION_STATUSES")
            .isNotEmpty();

        Set<String> onlyInJava = new LinkedHashSet<>(javaIds);
        onlyInJava.removeAll(tsIds);
        Set<String> onlyInTs = new LinkedHashSet<>(tsIds);
        onlyInTs.removeAll(javaIds);

        assertThat(onlyInJava)
            .as("ids no enum Java mas AUSENTES no TS (adicione em reservation-status.ts): %s", onlyInJava)
            .isEmpty();
        assertThat(onlyInTs)
            .as("ids no TS mas AUSENTES no enum Java (adicione em ReservationStatus.java): %s", onlyInTs)
            .isEmpty();
    }
}
