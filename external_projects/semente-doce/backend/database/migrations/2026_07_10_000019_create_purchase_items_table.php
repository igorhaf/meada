<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    public function up(): void
    {
        Schema::create('purchase_items', function (Blueprint $table) {
            $table->id();
            $table->foreignId('purchase_id')->constrained()->cascadeOnDelete();
            // Insumo com compras não pode ser excluído (histórico de custo).
            $table->foreignId('ingredient_id')->constrained()->restrictOnDelete();

            $table->decimal('qty', 12, 3);                    // na unidade BASE do insumo
            $table->decimal('unit_cost', 12, 6);              // custo por unidade base
            $table->decimal('line_total', 10, 2);             // materializado em PHP

            $table->timestamps();

            $table->index(['purchase_id']);
            $table->index(['ingredient_id']);
        });
    }

    public function down(): void
    {
        Schema::dropIfExists('purchase_items');
    }
};
