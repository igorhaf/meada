<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    public function up(): void
    {
        // Um local do terapeuta PODE ser uma sala do espaço Pindorama.
        Schema::table('attendance_locations', function (Blueprint $table) {
            $table->foreignId('room_id')->nullable()->after('professional_id')
                ->constrained('rooms')->nullOnDelete();
        });

        // Split da plataforma congelado no pagamento do agendamento.
        Schema::table('appointments', function (Blueprint $table) {
            $table->decimal('commission_amount', 10, 2)->nullable()->after('total');
            $table->decimal('professional_amount', 10, 2)->nullable()->after('commission_amount');
        });
    }

    public function down(): void
    {
        Schema::table('attendance_locations', function (Blueprint $table) {
            $table->dropConstrainedForeignId('room_id');
        });
        Schema::table('appointments', function (Blueprint $table) {
            $table->dropColumn(['commission_amount', 'professional_amount']);
        });
    }
};
