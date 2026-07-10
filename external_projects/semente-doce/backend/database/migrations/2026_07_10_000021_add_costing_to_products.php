<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    // Rendimento da ficha técnica + margem-alvo para o preço sugerido.
    public function up(): void
    {
        Schema::table('products', function (Blueprint $table) {
            $table->unsignedInteger('yield_qty')->default(1)->after('position');   // a receita rende N unidades de venda
            $table->decimal('target_margin', 5, 2)->nullable()->after('yield_qty'); // % desejada (ex.: 65.00)
        });
    }

    public function down(): void
    {
        Schema::table('products', function (Blueprint $table) {
            $table->dropColumn(['yield_qty', 'target_margin']);
        });
    }
};
