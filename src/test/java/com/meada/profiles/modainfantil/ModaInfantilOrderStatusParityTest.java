package com.meada.profiles.modainfantil;

import com.meada.profiles.modainfantil.orders.ModaInfantilOrderStatus;
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
 * Paridade 1:1 entre {@link ModaInfantilOrderStatus} (Java) e {@code frontend/profiles/moda-infantil/
 * moda-infantil-order-status.ts} (camada 8.22 / perfil moda_infantil). Mesmo padrão de todos os
 * *OrderStatusParityTest dos demais perfis: casa cada objeto {@code { id: '...' }} do array exportado
 * (MODA_INFANTIL_ORDER_STATUSES) contra os ids do enum. Cobre os 6 status do gate de aceite.
 */
class ModaInfantilOrderStatusParityTest {

    private static final Path TS_FILE =
        Path.of("frontend", "profiles", "moda-infantil", "moda-infantil-order-status.ts");

    private static final Pattern ID_PATTERN = Pattern.compile("\\{\\s*id:\\s*'([a-z0-9_]+)'");

    @Test
    @DisplayName("ids do enum ModaInfantilOrderStatus == ids do moda-infantil-order-status.ts")
    void javaTsParity() throws IOException {
        assertThat(Files.exists(TS_FILE)).as("arquivo TS não encontrado em %s", TS_FILE).isTrue();
        String ts = Files.readString(TS_FILE);
        Set<String> tsIds = new LinkedHashSet<>();
        Matcher m = ID_PATTERN.matcher(ts);
        while (m.find()) {
            tsIds.add(m.group(1));
        }
        Set<String> javaIds = Arrays.stream(ModaInfantilOrderStatus.values())
            .map(ModaInfantilOrderStatus::id)
            .collect(Collectors.toCollection(LinkedHashSet::new));

        assertThat(tsIds).as("ids do TS não-vazios").isNotEmpty();
        Set<String> onlyInJava = new LinkedHashSet<>(javaIds);
        onlyInJava.removeAll(tsIds);
        Set<String> onlyInTs = new LinkedHashSet<>(tsIds);
        onlyInTs.removeAll(javaIds);
        assertThat(onlyInJava).as("ids no enum Java mas AUSENTES no TS: %s", onlyInJava).isEmpty();
        assertThat(onlyInTs).as("ids no TS mas AUSENTES no enum Java: %s", onlyInTs).isEmpty();
    }
}
