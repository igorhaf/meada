<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    // ⭐ Compras de insumos — alimentam o estoque e o custo médio ponderado.
    public function up(): void
    {
        Schema::create('purchases', function (Blueprint $table) {
            $table->id();
            $table->string('supplier');                      // fornecedor/mercado
            $table->date('purchased_at');
            $table->decimal('total', 10, 2)->default(0);      // materializado em PHP (Σ itens)
            $table->text('notes')->nullable();
            $table->timestamps();

            $table->index('purchased_at');
        });
    }

    public function down(): void
    {
        Schema::dropIfExists('purchases');
    }
};
