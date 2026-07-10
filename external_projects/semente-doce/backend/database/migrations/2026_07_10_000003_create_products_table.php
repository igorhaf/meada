<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    public function up(): void
    {
        Schema::create('products', function (Blueprint $table) {
            $table->id();
            $table->foreignId('category_id')->constrained()->cascadeOnDelete();

            $table->string('title');
            $table->string('slug')->unique();
            $table->text('description')->nullable();

            // Atributos de doceria/salgaderia
            $table->string('unit')->default('unidade');   // unidade | cento | duzia | kg | caixa | fatia | copo
            $table->string('flavor')->nullable();          // "Chocolate belga", "Coco queimado"…
            $table->string('serves')->nullable();          // "Serve 8 a 10 fatias"
            $table->string('contains_allergens')->nullable(); // "glúten, leite, ovo"
            $table->unsignedInteger('min_qty')->default(1);   // pedido mínimo do item (ex.: salgado por 50)

            // Sob encomenda x pronta-entrega (estilo iFood: preparo rápido vs agendado)
            $table->boolean('is_made_to_order')->default(false); // exige agendamento/encomenda
            $table->unsignedInteger('lead_time_days')->nullable(); // prazo mínimo (sob encomenda)
            $table->unsignedInteger('prep_minutes')->nullable();   // tempo de preparo (pronta-entrega)

            // Preço
            $table->decimal('price', 10, 2);
            $table->decimal('compare_at_price', 10, 2)->nullable(); // "de/por"

            // Status / vitrine
            $table->string('sku')->nullable();
            $table->boolean('is_active')->default(true);
            $table->boolean('is_featured')->default(false);
            $table->unsignedInteger('position')->default(0);

            // Prova social (denormalizada para a vitrine)
            $table->decimal('rating', 2, 1)->default(0);
            $table->unsignedInteger('reviews_count')->default(0);
            $table->unsignedInteger('sold_count')->default(0);

            $table->timestamps();

            $table->index(['category_id', 'is_active']);
            $table->index('is_featured');
            $table->index('is_made_to_order');
            $table->index('price');
        });
    }

    public function down(): void
    {
        Schema::dropIfExists('products');
    }
};
