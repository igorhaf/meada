<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    // ⭐ Gastos da doceria: salários, recorrentes (aluguel/energia/…) e avulsos.
    // Recorrente = lançado 1x e contado TODO MÊS enquanto is_active (dia opcional).
    public function up(): void
    {
        Schema::create('expenses', function (Blueprint $table) {
            $table->id();
            $table->string('description');
            $table->string('category')->default('outros');    // ver Expense::CATEGORIES
            $table->string('person')->nullable();              // quem recebe (salários)
            $table->decimal('amount', 10, 2);
            $table->date('expense_date');                      // avulso: data do gasto; recorrente: início
            $table->boolean('is_recurring')->default(false);
            $table->unsignedTinyInteger('recurrence_day')->nullable(); // dia do mês (1–28)
            $table->boolean('is_active')->default(true);       // encerra a recorrência sem apagar histórico
            $table->text('notes')->nullable();
            $table->timestamps();

            $table->index('category');
            $table->index('expense_date');
            $table->index(['is_recurring', 'is_active']);
        });
    }

    public function down(): void
    {
        Schema::dropIfExists('expenses');
    }
};
