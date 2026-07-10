<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Builder;
use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\Relations\BelongsTo;
use Illuminate\Database\Eloquent\Relations\HasMany;

class Product extends Model
{
    protected $fillable = [
        'category_id', 'title', 'slug', 'description',
        'unit', 'flavor', 'serves', 'contains_allergens', 'min_qty',
        'is_made_to_order', 'lead_time_days', 'prep_minutes',
        'price', 'compare_at_price', 'sku', 'is_active', 'is_featured', 'position',
        'rating', 'reviews_count', 'sold_count',
        'yield_qty', 'target_margin',
    ];

    protected $casts = [
        'price' => 'decimal:2',
        'compare_at_price' => 'decimal:2',
        'rating' => 'decimal:1',
        'is_active' => 'boolean',
        'is_featured' => 'boolean',
        'is_made_to_order' => 'boolean',
        'target_margin' => 'decimal:2',
    ];

    /** Unidades de venda de uma doceria/salgaderia. */
    public const UNITS = [
        'unidade' => 'unidade',
        'cento' => 'cento',
        'duzia' => 'dúzia',
        'caixa' => 'caixa',
        'fatia' => 'fatia',
        'kg' => 'kg',
        'copo' => 'copo',
    ];

    /* ---------------------------------------------------------------- Relations */

    public function category(): BelongsTo
    {
        return $this->belongsTo(Category::class);
    }

    public function images(): HasMany
    {
        return $this->hasMany(ProductImage::class)->orderByDesc('is_primary')->orderBy('position');
    }

    /** Grupos de opção (recheio, cobertura, tamanho…), estilo iFood. */
    public function optionGroups(): HasMany
    {
        return $this->hasMany(ProductOptionGroup::class)->orderBy('position');
    }

    /** ⭐ Ficha técnica (insumos × quantidades) — base do custo real (CostService). */
    public function recipeItems(): HasMany
    {
        return $this->hasMany(ProductRecipeItem::class);
    }

    /* ------------------------------------------------------------------- Scopes */

    public function scopeActive(Builder $query): Builder
    {
        return $query->where('is_active', true);
    }

    public function scopeFeatured(Builder $query): Builder
    {
        return $query->where('is_featured', true);
    }

    public function scopeReadyToEat(Builder $query): Builder
    {
        return $query->where('is_made_to_order', false);
    }

    public function scopeMadeToOrder(Builder $query): Builder
    {
        return $query->where('is_made_to_order', true);
    }

    public function scopeSearch(Builder $query, ?string $term): Builder
    {
        if (! $term) {
            return $query;
        }

        $like = '%' . str_replace(' ', '%', trim($term)) . '%';

        return $query->where(function (Builder $inner) use ($like) {
            $inner->where('title', 'ilike', $like)
                ->orWhere('flavor', 'ilike', $like)
                ->orWhere('description', 'ilike', $like);
        });
    }

    /**
     * Filtros de listagem da vitrine vindos da query string.
     *
     * @param  array<string,mixed>  $filters
     */
    public function scopeFilter(Builder $query, array $filters): Builder
    {
        $query->search($filters['q'] ?? null);

        $query->when(($filters['availability'] ?? null) === 'ready', fn (Builder $q) => $q->readyToEat());
        $query->when(($filters['availability'] ?? null) === 'order', fn (Builder $q) => $q->madeToOrder());

        $query->when($filters['unit'] ?? null, function (Builder $q, $units) {
            $q->whereIn('unit', (array) $units);
        });

        $query->when($filters['min'] ?? null, fn (Builder $q, $min) => $q->where('price', '>=', (float) $min));
        $query->when($filters['max'] ?? null, fn (Builder $q, $max) => $q->where('price', '<=', (float) $max));

        return match ($filters['sort'] ?? 'relevance') {
            'price_asc'    => $query->orderBy('price'),
            'price_desc'   => $query->orderByDesc('price'),
            'newest'       => $query->latest(),
            'best_selling' => $query->orderByDesc('sold_count'),
            'rating'       => $query->orderByDesc('rating'),
            default        => $query->orderByDesc('is_featured')->orderByDesc('sold_count'),
        };
    }

    /* ---------------------------------------------------------------- Accessors */

    public function getRouteKeyName(): string
    {
        return 'slug';
    }

    public function getUrlAttribute(): string
    {
        return route('products.show', $this->slug);
    }

    public function getUnitLabelAttribute(): string
    {
        return self::UNITS[$this->unit] ?? $this->unit;
    }

    /** Sufixo mostrado ao lado do preço: "/cento", "/kg", "cada"… */
    public function getPriceSuffixAttribute(): string
    {
        return match ($this->unit) {
            'cento' => '/cento',
            'duzia' => '/dúzia',
            'kg' => '/kg',
            'caixa' => '/caixa',
            'fatia' => '/fatia',
            'copo' => '/copo',
            default => 'cada',
        };
    }

    public function getPrimaryImageUrlAttribute(): string
    {
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

    public function getHasOptionsAttribute(): bool
    {
        return $this->relationLoaded('optionGroups')
            ? $this->optionGroups->isNotEmpty()
            : $this->optionGroups()->exists();
    }

    /**
     * Dados do card para as ilhas Vue (add-to-cart). Produtos com opções ou sob
     * encomenda não vão direto ao carrinho — o card manda escolher na página.
     *
     * @return array<string,mixed>
     */
    public function toCartPayload(): array
    {
        return [
            'id' => $this->id,
            'type' => 'product',
            'title' => $this->title,
            'slug' => $this->slug,
            'price' => (float) $this->price,
            'image' => $this->primary_image_url,
            'url' => $this->url,
            'unit' => $this->unit_label,
            'minQty' => (int) $this->min_qty,
            'madeToOrder' => (bool) $this->is_made_to_order,
            'needsChoice' => $this->has_options || $this->is_made_to_order,
        ];
    }
}
