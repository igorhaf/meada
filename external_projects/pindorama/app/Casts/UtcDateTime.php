<?php

namespace App\Casts;

use Carbon\CarbonImmutable;
use Illuminate\Contracts\Database\Eloquent\CastsAttributes;

/**
 * Cast for timestamptz columns: always PERSISTS in UTC (with offset, so Postgres
 * records the correct instant regardless of the incoming Carbon's timezone) and
 * READS back as a UTC CarbonImmutable. Display code converts to the local tz.
 *
 * Laravel otherwise formats Carbons wall-clock (dropping the offset), which would
 * shift a São Paulo time into UTC and corrupt the stored instant.
 */
class UtcDateTime implements CastsAttributes
{
    public function get($model, string $key, $value, array $attributes): ?CarbonImmutable
    {
        return $value === null ? null : CarbonImmutable::parse($value)->utc();
    }

    public function set($model, string $key, $value, array $attributes): ?string
    {
        return $value === null ? null : CarbonImmutable::parse($value)->utc()->format('Y-m-d H:i:sP');
    }
}
