<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    // Horário de trabalho semanal recorrente — POR LOCAL (o menu de slots vem daqui).
    public function up(): void
    {
        Schema::create('professional_availabilities', function (Blueprint $table) {
            $table->id();
            $table->foreignId('professional_id')->constrained('users')->cascadeOnDelete();
            $table->foreignId('attendance_location_id')->constrained('attendance_locations')->cascadeOnDelete();
            $table->unsignedTinyInteger('weekday');   // 0=domingo … 6=sábado (Carbon::dayOfWeek)
            $table->time('start_time');
            $table->time('end_time');
            $table->boolean('is_active')->default(true);
            $table->timestamps();

            $table->index(['professional_id', 'weekday']);
            $table->index(['attendance_location_id', 'weekday']);
        });
    }

    public function down(): void
    {
        Schema::dropIfExists('professional_availabilities');
    }
};
