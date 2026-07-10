<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    // ⭐ Kits montados pela doceria no painel (Kit Festa, Caixa de brigadeiros…).
    // O preço é arbitrado pela loja; a composição vive em kit_items.
    public function up(): void
    {
        Schema::create('kits', function (Blueprint $table) {
            $table->id();
            $table->string('name');
            $table->string('slug')->unique();
            $table->text('description')->nullable();

            $table->string('kit_type')->default('festa');  // festa | cafe | presente | corporativo
            $table->string('serves')->nullable();           // "Serve 20 pessoas"

            $table->decimal('price', 10, 2);                // preço final do kit (definido pela loja)
            $table->string('image_path')->nullable();

            $table->boolean('is_active')->default(true);
            $table->boolean('is_featured')->default(false);
            $table->boolean('is_made_to_order')->default(false); // kit por encomenda (agendado)
            $table->unsignedInteger('lead_time_days')->nullable();
            $table->unsignedInteger('position')->default(0);

            $table->unsignedInteger('sold_count')->default(0);

            $table->timestamps();

            $table->index(['is_active', 'is_featured']);
            $table->index('kit_type');
        });
    }

    public function down(): void
    {
        Schema::dropIfExists('kits');
    }
};
