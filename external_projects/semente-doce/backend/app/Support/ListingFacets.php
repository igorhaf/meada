<?php

namespace App\Support;

use App\Models\Product;
use Illuminate\Database\Eloquent\Builder;

/**
 * Monta os filtros (facetas) da listagem: unidades de venda, disponibilidade
 * (pronta-entrega x sob encomenda) e faixa de preço — a partir de uma query base.
 */
class ListingFacets
{
    /**
     * @return array<string,mixed>
     */
    public static function build(Builder $base): array
    {
        $units = (clone $base)->whereNotNull('unit')
            ->distinct()->orderBy('unit')->pluck('unit')->all();

        $availability = [
            'ready' => (clone $base)->where('is_made_to_order', false)->count(),
            'order' => (clone $base)->where('is_made_to_order', true)->count(),
        ];

        $range = (clone $base)->selectRaw('min(price) as min_price, max(price) as max_price')->first();

        return [
            'units' => $units,
            'unitLabels' => Product::UNITS,
            'availability' => $availability,
            'priceMin' => (float) ($range->min_price ?? 0),
            'priceMax' => (float) ($range->max_price ?? 0),
        ];
    }
}
