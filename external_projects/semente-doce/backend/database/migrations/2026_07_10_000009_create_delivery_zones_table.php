<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    // Entrega local por bairro (estilo iFood). Bairro fora da tabela → taxa padrão.
    public function up(): void
    {
        Schema::create('delivery_zones', function (Blueprint $table) {
            $table->id();
            $table->string('neighborhood');                 // bairro
            $table->decimal('fee', 10, 2)->default(0);
            $table->unsignedInteger('eta_min')->nullable(); // minutos
            $table->unsignedInteger('eta_max')->nullable();
            $table->boolean('is_active')->default(true);
            $table->unsignedInteger('position')->default(0);
            $table->timestamps();

            $table->index(['is_active', 'position']);
        });
    }

    public function down(): void
    {
        Schema::dropIfExists('delivery_zones');
    }
};
