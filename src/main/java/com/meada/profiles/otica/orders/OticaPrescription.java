package com.meada.profiles.otica.orders;

import java.math.BigDecimal;

/**
 * Dados de RECEITA de uma encomenda de óculos (camada 8.12, FLUXO B). TODOS os campos são ADMINISTRATIVOS
 * e NULLABLE — a IA REGISTRA o grau que o cliente forneceu, NÃO calcula/valida/interpreta. Espelha as
 * colunas rx_* de {@code otica_orders}: esférico/cilíndrico/eixo de OD (olho direito) e OE (olho
 * esquerdo) + DP (distância pupilar). {@code pending} = o cliente vai "trazer a receita" depois (a loja
 * confirma no painel antes de montar).
 */
public record OticaPrescription(
    BigDecimal odSpherical,
    BigDecimal odCylindrical,
    Integer odAxis,
    BigDecimal oeSpherical,
    BigDecimal oeCylindrical,
    Integer oeAxis,
    BigDecimal pd,
    boolean pending) {

    /** Receita vazia + pendente (cliente vai trazer a receita; nenhum grau informado). */
    public static OticaPrescription pendingEmpty() {
        return new OticaPrescription(null, null, null, null, null, null, null, true);
    }
}
