<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    // Regras de comissão/aluguel da plataforma, resolvidas por precedência
    // (room > professional > service_category > service > default).
    public function up(): void
    {
        Schema::create('commission_rules', function (Blueprint $table) {
            $table->id();
            $table->string('scope_type');            // default | room | professional | service_category | service
            $table->unsignedBigInteger('scope_id')->nullable();  // null para default
            $table->string('rate_type')->default('percent');     // percent | fixed
            $table->decimal('rate_value', 10, 2)->default(0);
            $table->boolean('is_active')->default(true);
            $table->timestamps();

            $table->unique(['scope_type', 'scope_id']);
        });
    }

    public function down(): void
    {
        Schema::dropIfExists('commission_rules');
    }
};
