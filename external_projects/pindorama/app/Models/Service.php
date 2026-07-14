<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Builder;
use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\Relations\BelongsTo;
use Illuminate\Database\Eloquent\Relations\BelongsToMany;
use Illuminate\Database\Eloquent\Relations\HasMany;

class Service extends Model
{
    protected $fillable = [
        'professional_id', 'service_category_id', 'title', 'slug', 'description',
        'modality', 'duration_minutes', 'buffer_minutes',
        'price', 'compare_at_price', 'max_installments', 'requires_prepayment',
        'is_active', 'is_featured',
        'rating', 'reviews_count', 'bookings_count', 'views',
        'professional_name', 'professional_city', 'professional_state',
        'cover_path',
    ];

    protected $casts = [
        'price' => 'decimal:2',
        'compare_at_price' => 'decimal:2',
        'rating' => 'decimal:1',
        'requires_prepayment' => 'boolean',
        'is_active' => 'boolean',
        'is_featured' => 'boolean',
    ];

    public const MODALITIES = [
        'presencial' => 'Presencial',
        'online' => 'Online',
        'ambos' => 'Presencial e Online',
    ];

    /* ---------------------------------------------------------------- Relations */

    public function category(): BelongsTo
    {
        return $this->belongsTo(ServiceCategory::class, 'service_category_id');
    }

    /** The therapist (tenant) who offers this service. */
    public function professional(): BelongsTo
    {
        return $this->belongsTo(User::class, 'professional_id');
    }

    public function images(): HasMany
    {
        return $this->hasMany(ServiceImage::class)->orderByDesc('is_primary')->orderBy('position');
    }

    /** Locations where this service is offered (online modality → the online pseudo-location). */
    public function locations(): BelongsToMany
    {
        return $this->belongsToMany(AttendanceLocation::class, 'attendance_location_service');
    }

    /* ------------------------------------------------------------------- Scopes */

    public function scopeActive(Builder $query): Builder
    {
        return $query->where('is_active', true)->whereHas('professional', fn (Builder $q) => $q->where('is_active', true));
    }

    public function scopeFeatured(Builder $query): Builder
    {
        return $query->where('is_featured', true);
    }

    /** Row-level tenant scope: only this professional's services. */
    public function scopeForProfessional(Builder $query, User $professional): Builder
    {
        return $query->where('professional_id', $professional->id);
    }

    public function scopeSearch(Builder $query, ?string $term): Builder
    {
        if (! $term) {
            return $query;
        }

        $like = '%' . str_replace(' ', '%', trim($term)) . '%';

        return $query->where(function (Builder $inner) use ($like) {
            $inner->where('title', 'ilike', $like)
                ->orWhere('description', 'ilike', $like)
                ->orWhere('professional_name', 'ilike', $like);
        });
    }

    /**
     * Apply storefront listing filters coming from the request query string.
     *
     * @param  array<string,mixed>  $filters
     */
    public function scopeFilter(Builder $query, array $filters): Builder
    {
        $query->search($filters['q'] ?? null);

        // Modalidade: um serviço "ambos" aparece tanto em presencial quanto em online.
        $query->when($filters['modality'] ?? null, function (Builder $q, $modalities) {
            $wanted = (array) $modalities;
            $expanded = $wanted;
            if (array_intersect(['presencial', 'online'], $wanted)) {
                $expanded[] = 'ambos';
            }
            $q->whereIn('modality', array_unique($expanded));
        });

        $query->when($filters['city'] ?? null, function (Builder $q, $cities) {
            $q->whereIn('professional_city', (array) $cities);
        });

        $query->when($filters['min'] ?? null, fn (Builder $q, $min) => $q->where('price', '>=', (float) $min));
        $query->when($filters['max'] ?? null, fn (Builder $q, $max) => $q->where('price', '<=', (float) $max));

        return match ($filters['sort'] ?? 'relevance') {
            'price_asc'   => $query->orderBy('price'),
            'price_desc'  => $query->orderByDesc('price'),
            'newest'      => $query->latest(),
            'most_booked' => $query->orderByDesc('bookings_count'),
            'rating'      => $query->orderByDesc('rating'),
            default       => $query->orderByDesc('is_featured')->orderByDesc('bookings_count'),
        };
    }

    /* ---------------------------------------------------------------- Accessors */

    public function getRouteKeyName(): string
    {
        return 'slug';
    }

    public function getUrlAttribute(): string
    {
        return route('services.show', $this->slug);
    }

    public function getModalityLabelAttribute(): string
    {
        return self::MODALITIES[$this->modality] ?? ucfirst($this->modality);
    }

    public function getCoverUrlAttribute(): string
    {
        if ($this->cover_path) {
            return $this->cover_path;
        }

        $image = $this->relationLoaded('images')
            ? $this->images->first()
            : $this->images()->first();

        return $image?->path ?? placeholder_image($this->slug, $this->title);
    }

    public function getDiscountPercentAttribute(): ?int
    {
        if (! $this->compare_at_price || $this->compare_at_price <= $this->price) {
            return null;
        }

        return (int) round((1 - $this->price / $this->compare_at_price) * 100);
    }

    public function getInstallmentValueAttribute(): float
    {
        $n = max(1, (int) $this->max_installments);

        return round($this->price / $n, 2);
    }

    public function getDurationLabelAttribute(): string
    {
        $min = (int) $this->duration_minutes;
        if ($min < 60) {
            return "{$min}min";
        }
        $h = intdiv($min, 60);
        $rest = $min % 60;

        return $rest ? "{$h}h{$rest}min" : "{$h}h";
    }
}
