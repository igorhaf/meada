<?php

namespace App\Support;

use App\Models\Service;
use Illuminate\Database\Eloquent\Builder;

/**
 * Builds the filter facets (modality counts, cities, price range) shown in the
 * storefront listing sidebar, derived from a base service query (pre-filter, so
 * the counts don't collapse as the user selects filters).
 */
class ListingFacets
{
    /**
     * @return array<string,mixed>
     */
    public static function build(Builder $base): array
    {
        $modalities = (clone $base)
            ->selectRaw('modality, count(*) as total')
            ->groupBy('modality')
            ->pluck('total', 'modality')
            ->all();

        $cities = (clone $base)->whereNotNull('professional_city')
            ->distinct()->orderBy('professional_city')->pluck('professional_city')->all();

        $range = (clone $base)->selectRaw('min(price) as min_price, max(price) as max_price')->first();

        return [
            'modalities' => $modalities,
            'modalityLabels' => Service::MODALITIES,
            'cities' => $cities,
            'priceMin' => (float) ($range->min_price ?? 0),
            'priceMax' => (float) ($range->max_price ?? 0),
        ];
    }
}
