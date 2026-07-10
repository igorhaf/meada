<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    // Agendamentos (1 serviço = 1 agendamento). Núcleo do chassi de agenda.
    public function up(): void
    {
        Schema::create('appointments', function (Blueprint $table) {
            $table->id();
            $table->string('reference')->unique();

            $table->foreignId('professional_id')->constrained('users')->cascadeOnDelete();
            $table->foreignId('service_id')->nullable()->constrained('services')->nullOnDelete();
            $table->foreignId('attendance_location_id')->nullable()->constrained('attendance_locations')->nullOnDelete();
            $table->foreignId('customer_id')->nullable()->constrained('users')->nullOnDelete();

            // Snapshot do paciente
            $table->string('patient_name');
            $table->string('patient_email')->nullable();
            $table->string('patient_phone')->nullable();

            // Snapshots (chassi-A): congelados no INSERT
            $table->string('service_title');
            $table->decimal('service_price', 10, 2);
            $table->unsignedInteger('duration_minutes');
            $table->string('modality')->default('presencial');
            $table->string('location_label')->nullable();   // nome + endereço do local congelados

            // Agendamento — end_at é MATERIALIZADO em PHP (start_at + duration); nunca GENERATED.
            $table->timestampTz('start_at');
            $table->timestampTz('end_at');
            $table->string('timezone')->default('America/Sao_Paulo');

            $table->string('status')->default('pending');   // pending|confirmed|completed|cancelled|no_show
            $table->string('meeting_link')->nullable();       // online
            $table->text('notes')->nullable();                // motivo do paciente

            // Pagamento (reuso do muda — denormalizado no agendamento)
            $table->string('payment_status')->default('pending');
            $table->string('payment_method')->nullable();
            $table->string('mp_payment_id')->nullable();
            $table->timestamp('paid_at')->nullable();
            $table->decimal('total', 10, 2);

            // Auditoria
            $table->timestamp('confirmed_at')->nullable();
            $table->timestamp('completed_at')->nullable();
            $table->timestamp('cancelled_at')->nullable();
            $table->string('cancelled_by')->nullable();       // customer|professional|system

            $table->timestamps();

            $table->index(['professional_id', 'start_at']);
            $table->index('customer_id');
            $table->index('status');
            $table->index('payment_status');
        });

        // Índice parcial de conflito (Postgres) — por profissional, só status bloqueantes.
        if (DB::getDriverName() === 'pgsql') {
            DB::statement("CREATE INDEX appointments_conflict_idx ON appointments (professional_id, start_at, end_at) WHERE status IN ('pending','confirmed')");
        }
    }

    public function down(): void
    {
        Schema::dropIfExists('appointments');
    }
};
