<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    public function up(): void
    {
        Schema::create('orders', function (Blueprint $table) {
            $table->id();
            $table->string('reference')->unique();               // código amigável do pedido
            $table->foreignId('user_id')->nullable()->constrained()->nullOnDelete();

            $table->string('buyer_name');
            $table->string('buyer_email');
            $table->string('buyer_phone')->nullable();

            // Entrega local: retirar na loja x entregar no bairro
            $table->string('fulfillment_type')->default('delivery'); // delivery | pickup
            $table->text('delivery_address')->nullable();
            $table->string('delivery_neighborhood')->nullable();
            $table->date('scheduled_for')->nullable();            // agendamento (opcional)
            $table->text('notes')->nullable();                    // recado do cliente

            // Status de atendimento (Kanban da cozinha)
            $table->string('status')->default('pending');
            // pending | paid | preparing | out_for_delivery | ready | delivered | cancelled

            // Pagamento (Mercado Pago — igual ao muda)
            $table->string('payment_status')->default('pending'); // pending|approved|in_process|rejected|refunded|cancelled
            $table->string('payment_method')->nullable();          // mercadopago | simulado
            $table->string('mp_preference_id')->nullable();
            $table->string('mp_payment_id')->nullable();
            $table->timestamp('paid_at')->nullable();

            $table->decimal('subtotal', 10, 2)->default(0);
            $table->decimal('delivery_fee', 10, 2)->default(0);
            $table->decimal('total', 10, 2)->default(0);

            $table->timestamps();

            $table->index('status');
            $table->index('payment_status');
        });
    }

    public function down(): void
    {
        Schema::dropIfExists('orders');
    }
};
