<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Model;

class CommissionRule extends Model
{
    protected $fillable = ['scope_type', 'scope_id', 'rate_type', 'rate_value', 'is_active'];

    protected $casts = [
        'scope_id' => 'integer',
        'rate_value' => 'decimal:2',
        'is_active' => 'boolean',
    ];

    public const SCOPE_TYPES = [
        'default' => 'Padrão da plataforma',
        'room' => 'Por sala',
        'professional' => 'Por profissional',
        'service_category' => 'Por prática',
        'service' => 'Por serviço',
    ];

    public const RATE_TYPES = [
        'percent' => 'Percentual (%)',
        'fixed' => 'Valor fixo (R$)',
    ];
}
