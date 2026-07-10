<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    // Cobranças da plataforma ao terapeuta: mensalidade, cadastro, destaque.
    public function up(): void
    {
        Schema::create('platform_charges', function (Blueprint $table) {
            $table->id();
            $table->string('reference')->unique();
            $table->foreignId('professional_id')->constrained('users')->cascadeOnDelete();
            $table->string('type');                        // subscription | registration | featured
            $table->string('description');
            $table->decimal('base_amount', 10, 2);          // valor cheio
            $table->decimal('discount_amount', 10, 2)->default(0);
            $table->decimal('amount', 10, 2);               // valor final cobrado
            $table->string('reference_month')->nullable();  // 'YYYY-MM' (mensalidade — única por mês)
            $table->string('status')->default('pending');   // pending | paid | waived
            $table->date('due_date')->nullable();
            $table->timestamp('paid_at')->nullable();
            $table->string('payment_method')->nullable();
            $table->string('mp_payment_id')->nullable();
            $table->timestamps();

            $table->unique(['professional_id', 'type', 'reference_month']);
            $table->index(['professional_id', 'status']);
        });
    }

    public function down(): void
    {
        Schema::dropIfExists('platform_charges');
    }
};
