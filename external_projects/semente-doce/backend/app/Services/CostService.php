<?php

namespace App\Services;

use App\Models\Kit;
use App\Models\Product;

/**
 * ⭐ Cálculo inteligente de custo e lucro.
 *
 * O custo REAL de um produto sai da ficha técnica (product_recipe_items) valorada
 * pelo CUSTO MÉDIO PONDERADO dos insumos (recalculado a cada compra) e dividida
 * pelo rendimento (products.yield_qty). Daí derivam lucro, margem e preço sugerido.
 *
 * Convenções:
 *  - margem  = (preço − custo) / preço × 100  (sobre a VENDA)
 *  - markup  = (preço − custo) / custo × 100  (sobre o CUSTO)
 *  - preço sugerido p/ margem-alvo M = custo / (1 − M/100)
 */
class CostService
{
    /** Custo TOTAL da receita (todos os insumos da ficha), em R$. */
    public function recipeCost(Product $product): ?float
    {
        $items = $product->relationLoaded('recipeItems')
            ? $product->recipeItems
            : $product->recipeItems()->with('ingredient')->get();

        if ($items->isEmpty()) {
            return null; // sem ficha técnica → custo desconhecido
        }

        return round($items->sum(
            fn ($item) => (float) $item->qty * (float) ($item->ingredient?->avg_cost ?? 0)
        ), 4);
    }

    /** Custo por UNIDADE VENDIDA (receita ÷ rendimento). */
    public function unitCost(Product $product): ?float
    {
        $recipe = $this->recipeCost($product);

        if ($recipe === null) {
            return null;
        }

        return round($recipe / max(1, (int) $product->yield_qty), 4);
    }

    /** Lucro em R$ por unidade vendida (preço − custo). */
    public function unitProfit(Product $product): ?float
    {
        $cost = $this->unitCost($product);

        return $cost === null ? null : round((float) $product->price - $cost, 2);
    }

    /** Margem % sobre a venda. */
    public function margin(Product $product): ?float
    {
        $cost = $this->unitCost($product);
        $price = (float) $product->price;

        if ($cost === null || $price <= 0) {
            return null;
        }

        return round(($price - $cost) / $price * 100, 1);
    }

    /** Markup % sobre o custo. */
    public function markup(Product $product): ?float
    {
        $cost = $this->unitCost($product);

        if ($cost === null || $cost <= 0) {
            return null;
        }

        return round(((float) $product->price - $cost) / $cost * 100, 1);
    }

    /**
     * Preço sugerido para atingir a margem-alvo (target_margin do produto, ou a
     * informada). Margem-alvo ≥ 100% é inválida → null.
     */
    public function suggestedPrice(Product $product, ?float $targetMargin = null): ?float
    {
        $cost = $this->unitCost($product);
        $target = $targetMargin ?? ($product->target_margin !== null ? (float) $product->target_margin : null);

        if ($cost === null || $target === null || $target >= 100 || $target < 0) {
            return null;
        }

        return round($cost / (1 - $target / 100), 2);
    }

    /**
     * Custo de um KIT = Σ (custo unitário do produto componente × qtd).
     * Componentes avulsos (sem product_id) ou sem ficha deixam o custo INCOMPLETO.
     *
     * @return array{cost: float, complete: bool, missing: array<int,string>}
     */
    public function kitCost(Kit $kit): array
    {
        $items = $kit->relationLoaded('items')
            ? $kit->items
            : $kit->items()->with('product.recipeItems.ingredient')->get();

        $cost = 0.0;
        $complete = true;
        $missing = [];

        foreach ($items as $item) {
            $unitCost = $item->product ? $this->unitCost($item->product) : null;

            if ($unitCost === null) {
                $complete = false;
                $missing[] = $item->label;
                continue;
            }

            $cost += $unitCost * (int) $item->qty;
        }

        return ['cost' => round($cost, 2), 'complete' => $complete, 'missing' => $missing];
    }
}
