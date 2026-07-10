<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    // Especialidades (práticas) de um terapeuta — chips na landing + faceta no diretório.
    public function up(): void
    {
        Schema::create('professional_specialties', function (Blueprint $table) {
            $table->foreignId('professional_id')->constrained('users')->cascadeOnDelete();
            $table->foreignId('service_category_id')->constrained('service_categories')->cascadeOnDelete();
            $table->primary(['professional_id', 'service_category_id']);
        });
    }

    public function down(): void
    {
        Schema::dropIfExists('professional_specialties');
    }
};
