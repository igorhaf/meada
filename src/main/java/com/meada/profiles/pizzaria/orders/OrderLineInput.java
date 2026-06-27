package com.meada.profiles.pizzaria.orders;

import java.util.List;
import java.util.UUID;

/**
 * Linha de pedido na ENTRADA (o que a IA pediu) no perfil pizzaria (camada 8.6).
 *
 * <p>Dois modos, conforme a ESCAPADA meio-a-meio:
 * <ul>
 *   <li>Item NÃO-pizza (bebida, sobremesa, borda avulsa): {@code menuItemId} é o item;
 *       {@code flavorItemIds} vazio. Preço = base do item + Σ deltas (como no comida).</li>
 *   <li>Item PIZZA: {@code flavorItemIds} são os sabores das frações (1 = inteira, 2 =
 *       meio-a-meio, N = N frações). {@code menuItemId} pode ser null/ignorado nesse modo. O
 *       preço da pizza = MAX(preço dos sabores no tamanho) + Σ deltas (regra do maior valor),
 *       recalculado no backend.</li>
 * </ul>
 * {@code optionIds} = modifiers (Tamanho, Borda) — somam delta nos dois modos. Clone do comida +
 * {@code flavorItemIds} (a escapada).
 */
public record OrderLineInput(UUID menuItemId, int qtd, List<UUID> optionIds, List<UUID> flavorItemIds) {

    /** True se a linha é uma pizza com sabores (meio-a-meio ou inteira por sabor). */
    public boolean hasFlavors() {
        return flavorItemIds != null && !flavorItemIds.isEmpty();
    }
}
