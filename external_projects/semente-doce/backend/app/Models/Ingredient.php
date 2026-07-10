<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Builder;
use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\Relations\HasMany;

class Ingredient extends Model
{
    protected $fillable = [
        'name', 'unit', 'stock_qty', 'avg_cost', 'min_stock', 'is_active', 'notes',
    ];

    protected $casts = [
        'stock_qty' => 'decimal:3',
        'avg_cost' => 'decimal:6',
        'min_stock' => 'decimal:3',
        'is_active' => 'boolean',
    ];

    /** Unidade BASE do insumo — estoque, custo médio e fichas técnicas usam ela. */
    public const UNITS = [
        'g' => 'gramas (g)',
        'ml' => 'mililitros (ml)',
        'un' => 'unidades (un)',
    ];

    public function purchaseItems(): HasMany
    {
        return $this->hasMany(PurchaseItem::class);
    }

    public function recipeItems(): HasMany
    {
        return $this->hasMany(ProductRecipeItem::class);
    }

    public function scopeActive(Builder $query): Builder
    {
        return $query->where('is_active', true);
    }

    /* -------------------------------------------------- Custo médio ponderado */

    /**
     * Aplica uma compra ao insumo: soma estoque e recalcula o custo médio
     * PONDERADO — (estoque×custo + qtd×custo_novo) / (estoque + qtd).
     */
    public function applyPurchase(float $qty, float $unitCost): void
    {
        $currentQty = max(0, (float) $this->stock_qty);
        $newQty = $currentQty + $qty;

        $this->avg_cost = $newQty > 0
            ? (($currentQty * (float) $this->avg_cost) + ($qty * $unitCost)) / $newQty
            : $unitCost;
        $this->stock_qty = $newQty;
        $this->save();
    }

    /** Estorno simples de estoque (exclusão de compra) — o custo médio NÃO rebobina. */
    public function reverseStock(float $qty): void
    {
        $this->stock_qty = max(0, (float) $this->stock_qty - $qty);
        $this->save();
    }

    /* ------------------------------------------------------------- Acessores */

    public function getUnitLabelAttribute(): string
    {
        return self::UNITS[$this->unit] ?? $this->unit;
    }

    /** Rótulo da unidade "grande" p/ exibição: kg, L ou un. */
    public function getBigUnitAttribute(): string
    {
        return match ($this->unit) {
            'g' => 'kg',
            'ml' => 'L',
            default => 'un',
        };
    }

    /** Custo médio na unidade grande (R$/kg, R$/L ou R$/un). */
    public function getAvgCostBigAttribute(): float
    {
        $factor = in_array($this->unit, ['g', 'ml'], true) ? 1000 : 1;

        return (float) $this->avg_cost * $factor;
    }

    public function getLowStockAttribute(): bool
    {
        return $this->min_stock !== null && (float) $this->stock_qty < (float) $this->min_stock;
    }
}
