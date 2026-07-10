<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    // ⭐ Componentes de um kit. product_id pode ser nulo (item avulso digitado à mão),
    // e label/unit_price são SNAPSHOTS do momento da montagem — o kit não muda de cara
    // se o produto for editado depois.
    public function up(): void
    {
        Schema::create('kit_items', function (Blueprint $table) {
            $table->id();
            $table->foreignId('kit_id')->constrained()->cascadeOnDelete();
            $table->foreignId('product_id')->nullable()->constrained()->nullOnDelete();

            $table->string('label');                         // "Brigadeiro gourmet"
            $table->unsignedInteger('qty')->default(1);
            $table->decimal('unit_price', 10, 2)->default(0); // snapshot do preço unitário do componente
            $table->unsignedInteger('position')->default(0);

            $table->timestamps();

            $table->index(['kit_id', 'position']);
        });
    }

    public function down(): void
    {
        Schema::dropIfExists('kit_items');
    }
};
