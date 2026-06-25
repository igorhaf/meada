package com.meada.whatsapp.profiles.floricultura;

/**
 * Config do delivery floricultura (camada 8.4): taxa de entrega + pedido mínimo, em centavos. Clone de
 * {@link com.meada.whatsapp.profiles.sushi.SushiRestaurantConfig}. Quando o tenant não tem linha em
 * {@code floricultura_config}, usa-se {@link #ZERO} (taxa/mínimo 0).
 */
public record FloriculturaConfig(int deliveryFeeCents, int minOrderCents) {

    public static final FloriculturaConfig ZERO = new FloriculturaConfig(0, 0);
}
