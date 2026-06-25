package com.meada.whatsapp.profiles.pizzaria;

/**
 * Config do delivery pizzaria (camada 8.4): taxa de entrega + pedido mínimo, em centavos. Clone de
 * {@link com.meada.whatsapp.profiles.sushi.SushiRestaurantConfig}. Quando o tenant não tem linha em
 * {@code pizzaria_config}, usa-se {@link #ZERO} (taxa/mínimo 0).
 */
public record PizzariaConfig(int deliveryFeeCents, int minOrderCents) {

    public static final PizzariaConfig ZERO = new PizzariaConfig(0, 0);
}
