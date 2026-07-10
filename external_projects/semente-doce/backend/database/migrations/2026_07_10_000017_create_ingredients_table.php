<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    // ⭐ Insumos (matéria-prima): farinha, açúcar, leite condensado, embalagens…
    // Estoque e custo vivem na UNIDADE BASE (g | ml | un); o custo médio é PONDERADO
    // e recalculado a cada compra (Ingredient::applyPurchase).
    public function up(): void
    {
        Schema::create('ingredients', function (Blueprint $table) {
            $table->id();
            $table->string('name');
            $table->string('unit')->default('g');            // g | ml | un (unidade base)
            $table->decimal('stock_qty', 12, 3)->default(0);  // estoque na unidade base
            $table->decimal('avg_cost', 12, 6)->default(0);   // custo médio POR unidade base
            $table->decimal('min_stock', 12, 3)->nullable();  // alerta de estoque baixo
            $table->boolean('is_active')->default(true);
            $table->string('notes')->nullable();
            $table->timestamps();

            $table->index('is_active');
        });
    }

    public function down(): void
    {
        Schema::dropIfExists('ingredients');
    }
};
