<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    // Quais locais oferecem cada serviço (online -> local is_online).
    public function up(): void
    {
        Schema::create('attendance_location_service', function (Blueprint $table) {
            $table->foreignId('service_id')->constrained('services')->cascadeOnDelete();
            $table->foreignId('attendance_location_id')->constrained('attendance_locations')->cascadeOnDelete();
            $table->primary(['service_id', 'attendance_location_id']);
        });
    }

    public function down(): void
    {
        Schema::dropIfExists('attendance_location_service');
    }
};
