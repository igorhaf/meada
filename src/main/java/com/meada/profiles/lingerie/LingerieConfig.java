package com.meada.profiles.lingerie;

/**
 * Config do varejo lingerie (camada 8.21): taxa de entrega + pedido mínimo, em centavos. Clone de
 * {@link com.meada.profiles.adega.AdegaConfig}. Quando o tenant não tem linha em
 * {@code lingerie_config}, usa-se {@link #ZERO} (taxa/mínimo 0).
 */
public record LingerieConfig(int deliveryFeeCents, int minOrderCents) {

    public static final LingerieConfig ZERO = new LingerieConfig(0, 0);
}
