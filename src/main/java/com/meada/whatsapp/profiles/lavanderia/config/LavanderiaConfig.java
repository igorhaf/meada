package com.meada.whatsapp.profiles.lavanderia.config;

/**
 * Config do delivery lavanderia (camada 8.10): taxa de entrega + pedido mínimo + turnaround default (em
 * dias), em centavos para os dois primeiros. Clone de
 * {@link com.meada.whatsapp.profiles.floricultura.FloriculturaConfig} + {@code turnaroundDaysDefault}.
 * Quando o tenant não tem linha em {@code lavanderia_config}, usa-se {@link #DEFAULT} (0/0/1).
 */
public record LavanderiaConfig(int deliveryFeeCents, int minOrderCents, int turnaroundDaysDefault) {

    /** Default quando não há linha de config: sem taxa, sem mínimo, turnaround 1 dia. */
    public static final LavanderiaConfig DEFAULT = new LavanderiaConfig(0, 0, 1);
}
