<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    // Locais de atendimento de um terapeuta (consultórios físicos e/ou "Online").
    // Todos compartilham UMA agenda por profissional (o conflito é por professional_id).
    public function up(): void
    {
        Schema::create('attendance_locations', function (Blueprint $table) {
            $table->id();
            $table->foreignId('professional_id')->constrained('users')->cascadeOnDelete();
            $table->string('name');                                   // "Consultório Vila Mariana"
            $table->boolean('is_online')->default(false);             // pseudo-local de teleconsulta
            $table->string('address')->nullable();
            $table->string('neighborhood')->nullable();
            $table->string('city')->nullable();
            $table->string('state')->nullable();
            $table->string('zip')->nullable();
            $table->string('complement')->nullable();
            $table->string('map_url')->nullable();
            $table->boolean('is_active')->default(true);
            $table->unsignedInteger('position')->default(0);
            $table->timestamps();

            $table->index(['professional_id', 'is_active']);
            $table->index(['city', 'state']);
        });
    }

    public function down(): void
    {
        Schema::dropIfExists('attendance_locations');
    }
};
