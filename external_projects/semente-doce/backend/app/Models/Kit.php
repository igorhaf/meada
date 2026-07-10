<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Builder;
use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\Relations\HasMany;

class Kit extends Model
{
    protected $fillable = [
        'name', 'slug', 'description', 'kit_type', 'serves',
        'price', 'image_path', 'is_active', 'is_featured',
        'is_made_to_order', 'lead_time_days', 'position', 'sold_count',
    ];

    protected $casts = [
        'price' => 'decimal:2',
        'is_active' => 'boolean',
        'is_featured' => 'boolean',
        'is_made_to_order' => 'boolean',
    ];

    public const TYPES = [
        'festa' => 'Festa',
        'cafe' => 'Café da manhã',
        'presente' => 'Presente',
        'corporativo' => 'Corporativo',
    ];

    public function items(): HasMany
    {
        return $this->hasMany(KitItem::class)->orderBy('position');
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

    /* ---------------------------------------------------------------- Accessors */

    public function getRouteKeyName(): string
    {
        return 'slug';
    }

    public function getUrlAttribute(): string
    {
        return route('kits.show', $this->slug);
    }

    public function getTypeLabelAttribute(): string
    {
        return self::TYPES[$this->kit_type] ?? ucfirst((string) $this->kit_type);
    }

    public function getImageUrlAttribute(): string
    {
        return $this->image_path ?: placeholder_image($this->slug, $this->name);
    }

    /** Soma dos componentes (preço "cheio") — base para calcular a economia do kit. */
    public function getComponentsTotalAttribute(): float
    {
        $items = $this->relationLoaded('items') ? $this->items : $this->items()->get();

        return (float) $items->sum(fn (KitItem $item) => $item->qty * (float) $item->unit_price);
    }

    /** Quanto o cliente economiza comprando o kit em vez dos itens soltos. */
    public function getSavingsAttribute(): float
    {
        return max(0, round($this->components_total - (float) $this->price, 2));
    }

    /**
     * @return array<string,mixed>
     */
    public function toCartPayload(): array
    {
        return [
            'id' => $this->id,
            'type' => 'kit',
            'title' => $this->name,
            'slug' => $this->slug,
            'price' => (float) $this->price,
            'image' => $this->image_url,
            'url' => $this->url,
            'unit' => 'kit',
            'minQty' => 1,
            'madeToOrder' => (bool) $this->is_made_to_order,
            'needsChoice' => (bool) $this->is_made_to_order,
        ];
    }
}
