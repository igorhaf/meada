package com.meada.profiles.modainfantil;

/**
 * Config do varejo moda_infantil (camada 8.22): taxa de entrega + pedido mínimo, em centavos. Clone de
 * {@link com.meada.profiles.lingerie.LingerieConfig}. Quando o tenant não tem linha em
 * {@code moda_infantil_config}, usa-se {@link #ZERO} (taxa/mínimo 0).
 */
public record ModaInfantilConfig(int deliveryFeeCents, int minOrderCents) {

    public static final ModaInfantilConfig ZERO = new ModaInfantilConfig(0, 0);
}
