package com.meada.whatsapp.profiles.adega;

/**
 * Config do delivery adega (camada 8.4): taxa de entrega + pedido mínimo, em centavos. Clone de
 * {@link com.meada.whatsapp.profiles.sushi.SushiRestaurantConfig}. Quando o tenant não tem linha em
 * {@code adega_config}, usa-se {@link #ZERO} (taxa/mínimo 0).
 */
public record AdegaConfig(int deliveryFeeCents, int minOrderCents) {

    public static final AdegaConfig ZERO = new AdegaConfig(0, 0);
}
