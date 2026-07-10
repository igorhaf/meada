<?php

return [

    /*
    |--------------------------------------------------------------------------
    | Marketplace / cadastro de terapeutas
    |--------------------------------------------------------------------------
    |
    | Liga/desliga os pontos de entrada de "Seja um terapeuta" (links no header,
    | footer e cadastro) e o acesso ao painel do profissional / onboarding.
    | A funcionalidade continua no código — apenas fica oculta ao público.
    | O usuário root sempre mantém acesso, para seguir construindo o recurso.
    |
    */

    'professionals_enabled' => (bool) env('PINDORAMA_PROFESSIONALS_ENABLED', false),

    /*
    |--------------------------------------------------------------------------
    | Agenda / agendamento
    |--------------------------------------------------------------------------
    |
    | Fuso padrão para geração de slots e janela mínima de antecedência (em
    | minutos) para reservar um horário no mesmo dia.
    |
    */

    'timezone' => env('PINDORAMA_TIMEZONE', 'America/Sao_Paulo'),

    'min_lead_minutes' => (int) env('PINDORAMA_MIN_LEAD_MINUTES', 60),

];
