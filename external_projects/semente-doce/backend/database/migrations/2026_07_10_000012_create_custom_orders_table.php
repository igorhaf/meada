<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    // ⭐ Encomendas: o cliente descreve o que quer (bolo de aniversário, mesa de
    // salgados) e agenda a data. Nasce 'requested' → a loja orça → confirma → produz.
    public function up(): void
    {
        Schema::create('custom_orders', function (Blueprint $table) {
            $table->id();
            $table->string('reference')->unique();
            $table->foreignId('user_id')->nullable()->constrained()->nullOnDelete();

            // Ponto de partida opcional (encomenda a partir de um produto/kit da vitrine)
            $table->foreignId('product_id')->nullable()->constrained()->nullOnDelete();
            $table->foreignId('kit_id')->nullable()->constrained()->nullOnDelete();

            $table->string('customer_name');
            $table->string('customer_phone');
            $table->string('customer_email')->nullable();

            $table->string('title');                       // "Bolo de aniversário 2 andares"
            $table->text('description');                    // o que o cliente deseja
            $table->unsignedInteger('quantity')->nullable();
            $table->string('flavor')->nullable();
            $table->string('message_on_item')->nullable();  // "Parabéns, João!"
            $table->string('reference_photo_url')->nullable();

            // Entrega/retirada + data do evento
            $table->string('fulfillment_type')->default('pickup'); // pickup | delivery
            $table->text('delivery_address')->nullable();
            $table->date('event_date');                    // data desejada

            // Fluxo de orçamento (gate humano — a loja define o preço)
            $table->string('status')->default('requested');
            // requested | quoted | confirmed | producing | ready | delivered | declined | cancelled
            $table->decimal('quoted_price', 10, 2)->nullable();
            $table->text('admin_notes')->nullable();
            $table->timestamp('quoted_at')->nullable();
            $table->timestamp('confirmed_at')->nullable();

            $table->timestamps();

            $table->index('status');
            $table->index('event_date');
        });
    }

    public function down(): void
    {
        Schema::dropIfExists('custom_orders');
    }
};
