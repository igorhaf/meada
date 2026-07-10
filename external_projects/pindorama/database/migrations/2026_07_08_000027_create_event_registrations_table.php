<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    // Inscrições em eventos (com controle de vagas transacional + pagamento).
    public function up(): void
    {
        Schema::create('event_registrations', function (Blueprint $table) {
            $table->id();
            $table->string('reference')->unique();
            $table->foreignId('event_id')->constrained('events')->cascadeOnDelete();
            $table->foreignId('customer_id')->nullable()->constrained('users')->nullOnDelete();
            $table->string('participant_name');
            $table->string('participant_email')->nullable();
            $table->string('participant_phone')->nullable();

            $table->string('status')->default('registered');  // registered | confirmed | cancelled | attended
            $table->decimal('amount', 10, 2)->default(0);
            $table->decimal('discount_amount', 10, 2)->default(0);

            // Pagamento (reuso do padrão do agendamento)
            $table->string('payment_status')->default('pending');
            $table->string('payment_method')->nullable();
            $table->string('mp_payment_id')->nullable();
            $table->timestamp('paid_at')->nullable();

            $table->boolean('reminded')->default(false);
            $table->timestamps();

            $table->unique(['event_id', 'customer_id']);   // anti-dupla por conta
            $table->index(['event_id', 'status']);
        });
    }

    public function down(): void
    {
        Schema::dropIfExists('event_registrations');
    }
};
