<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Builder;
use Illuminate\Database\Eloquent\Model;

class DeliveryZone extends Model
{
    protected $fillable = [
        'neighborhood', 'fee', 'eta_min', 'eta_max', 'is_active', 'position',
    ];

    protected $casts = [
        'fee' => 'decimal:2',
        'is_active' => 'boolean',
    ];

    public function scopeActive(Builder $query): Builder
    {
        return $query->where('is_active', true)->orderBy('position');
    }

    /** Busca a zona por bairro, ignorando caixa e acentuação frouxamente. */
    public static function matchNeighborhood(?string $neighborhood): ?self
    {
        if (! $neighborhood) {
            return null;
        }

        $needle = mb_strtolower(trim($neighborhood));

        return static::active()->get()
            ->first(fn (self $zone) => mb_strtolower(trim($zone->neighborhood)) === $needle);
    }
}
