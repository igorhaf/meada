package com.meada.whatsapp.profiles.papelaria;

/**
 * Config da papelaria (camada 8.15 / perfil papelaria): taxa de entrega + pedido mínimo +
 * {@code leadTimeDaysDefault} (antecedência default para itens sob encomenda), em centavos/dias. Clone
 * de {@link com.meada.whatsapp.profiles.padaria.PadariaConfig} (camada 8.8). Quando o tenant não tem
 * linha em {@code papelaria_config}, usa-se {@link #DEFAULT} (taxa/mínimo 0, lead default 5,
 * espelhando o {@code default 5} da migration 59).
 */
public record PapelariaConfig(int deliveryFeeCents, int minOrderCents, int leadTimeDaysDefault) {

    /** Default usado quando não há linha de config (taxa/mínimo 0, lead default 5 — igual à migration). */
    public static final PapelariaConfig DEFAULT = new PapelariaConfig(0, 0, 5);
}
