<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    public function up(): void
    {
        Schema::create('order_items', function (Blueprint $table) {
            $table->id();
            $table->foreignId('order_id')->constrained()->cascadeOnDelete();
            // Um item é OU um produto OU um kit (snapshots preservam o pedido intacto).
            $table->foreignId('product_id')->nullable()->constrained()->nullOnDelete();
            $table->foreignId('kit_id')->nullable()->constrained()->nullOnDelete();

            $table->string('title');
            $table->string('image_path')->nullable();
            $table->string('options_summary')->nullable(); // "Recheio: Brigadeiro · Tamanho: G"
            $table->decimal('price', 10, 2);               // unitário já com deltas de opção
            $table->unsignedInteger('qty')->default(1);
            $table->decimal('line_total', 10, 2);

            $table->timestamps();

            $table->index('order_id');
        });
    }

    public function down(): void
    {
        Schema::dropIfExists('order_items');
    }
};
