package com.meada.whatsapp.profiles.pizzaria;

import com.meada.whatsapp.profiles.pizzaria.orders.PizzaOrderStatus;
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
 * Paridade 1:1 entre {@link PizzaOrderStatus} (Java) e o type union {@code OrderStatus} de
 * {@code frontend/profiles/pizzaria/pizzaria-types.ts} (camada 8.6). Cobre os 6 status do gate de
 * aceite (ESCAPADA 1).
 *
 * <p>Diferente dos demais perfis (que exportam um array de {@code { id: '...' }}), o pizzaria
 * declara o status como TYPE UNION (<code>type OrderStatus = | 'aguardando' | 'em_preparo' | ...
 * </code>). O teste isola o bloco da union e casa cada literal {@code '...'} contra os ids do enum.
 */
class PizzaOrderStatusParityTest {

    private static final Path TS_FILE =
        Path.of("frontend", "profiles", "pizzaria", "pizzaria-types.ts");

    // Isola o corpo da declaração: "type OrderStatus =" até o próximo type/export/const em branco.
    private static final Pattern UNION_BLOCK = Pattern.compile(
        "type\\s+OrderStatus\\s*=\\s*(.*?)\\n\\s*\\n", Pattern.DOTALL);
    private static final Pattern LITERAL = Pattern.compile("'([a-z0-9_]+)'");

    @Test
    @DisplayName("ids do enum PizzaOrderStatus == literais da union OrderStatus em pizzaria-types.ts")
    void javaTsParity() throws IOException {
        assertThat(Files.exists(TS_FILE)).as("arquivo TS não encontrado em %s", TS_FILE).isTrue();
        String ts = Files.readString(TS_FILE);

        Matcher block = UNION_BLOCK.matcher(ts);
        assertThat(block.find())
            .as("não encontrei o bloco 'type OrderStatus = ...' em %s", TS_FILE)
            .isTrue();

        Set<String> tsIds = new LinkedHashSet<>();
        Matcher m = LITERAL.matcher(block.group(1));
        while (m.find()) {
            tsIds.add(m.group(1));
        }

        Set<String> javaIds = Arrays.stream(PizzaOrderStatus.values())
            .map(PizzaOrderStatus::id)
            .collect(Collectors.toCollection(LinkedHashSet::new));

        assertThat(tsIds).as("literais do TS não-vazios").isNotEmpty();
        Set<String> onlyInJava = new LinkedHashSet<>(javaIds);
        onlyInJava.removeAll(tsIds);
        Set<String> onlyInTs = new LinkedHashSet<>(tsIds);
        onlyInTs.removeAll(javaIds);
        assertThat(onlyInJava).as("ids no enum Java mas AUSENTES no TS: %s", onlyInJava).isEmpty();
        assertThat(onlyInTs).as("ids no TS mas AUSENTES no enum Java: %s", onlyInTs).isEmpty();
    }
}
