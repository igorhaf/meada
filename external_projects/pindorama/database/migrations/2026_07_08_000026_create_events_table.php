<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    // Eventos do terapeuta: rodas de terapia, cursos presenciais, certificações.
    public function up(): void
    {
        Schema::create('events', function (Blueprint $table) {
            $table->id();
            $table->foreignId('professional_id')->constrained('users')->cascadeOnDelete();
            $table->string('title');
            $table->string('slug')->unique();
            $table->text('description')->nullable();
            $table->string('type')->default('roda');        // roda | curso | certificacao
            $table->string('modality')->default('presencial'); // presencial | online
            $table->string('location_label')->nullable();     // local livre (ou link online)
            $table->timestampTz('starts_at');
            $table->timestampTz('ends_at')->nullable();
            $table->string('timezone')->default('America/Sao_Paulo');
            $table->unsignedInteger('capacity')->default(0);  // 0 = ilimitado
            $table->decimal('price', 10, 2)->default(0);
            $table->boolean('is_free')->default(false);
            $table->boolean('allow_discount')->default(false);
            $table->decimal('discount_percent', 5, 2)->default(0);
            $table->string('cover_path')->nullable();
            $table->string('status')->default('draft');       // draft | published | cancelled
            $table->unsignedSmallInteger('reminder_hours')->default(24);
            $table->timestamp('reminded_at')->nullable();
            $table->timestamps();

            $table->index(['status', 'starts_at']);
            $table->index(['professional_id', 'starts_at']);
        });
    }

    public function down(): void
    {
        Schema::dropIfExists('events');
    }
};
