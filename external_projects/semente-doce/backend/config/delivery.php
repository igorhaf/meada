<?php

return [

    /*
    |--------------------------------------------------------------------------
    | Entrega local (estilo iFood) — retirada na loja + entrega por bairro
    |--------------------------------------------------------------------------
    |
    | A Semente Doce é uma doceria de bairro: nada de frete nacional. O cliente
    | escolhe RETIRAR na loja (grátis) ou receber por ENTREGA, cuja taxa vem da
    | tabela de zonas (bairros) cadastrada no painel — com fallback para a taxa
    | padrão abaixo quando o bairro não está mapeado.
    |
    */

    // Permite a opção "retirar na loja" no checkout.
    'pickup_enabled' => (bool) env('DELIVERY_PICKUP_ENABLED', true),

    // Pedido mínimo (subtotal) para fechar — null desativa.
    'min_order' => env('DELIVERY_MIN_ORDER', 30),

    // Entrega grátis acima deste subtotal — null desativa o benefício.
    'free_above' => env('DELIVERY_FREE_ABOVE', 150),

    // Taxa usada quando o bairro não está na tabela de zonas.
    'default_fee' => (float) env('DELIVERY_DEFAULT_FEE', 12.90),

    // Endereço de origem (de onde os doces saem quentinhos).
    'origin' => env('DELIVERY_ORIGIN', 'Rua das Sementes, 100 — Centro'),

    // Janela de tempo estimada (minutos) exibida na vitrine.
    'eta_min' => (int) env('DELIVERY_ETA_MIN', 40),
    'eta_max' => (int) env('DELIVERY_ETA_MAX', 70),

];
