<?php

return [

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
