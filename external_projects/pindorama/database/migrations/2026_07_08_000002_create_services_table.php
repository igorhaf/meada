<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    // Serviços (consultas/sessões) ofertados por um terapeuta (tenant).
    public function up(): void
    {
        Schema::create('services', function (Blueprint $table) {
            $table->id();

            // Row-level multitenancy: cada serviço pertence a um terapeuta (tenant).
            $table->foreignId('professional_id')->constrained('users')->cascadeOnDelete();
            $table->foreignId('service_category_id')->constrained('service_categories')->restrictOnDelete();

            $table->string('title');
            $table->string('slug')->unique();
            $table->text('description')->nullable();

            // Modalidade e duração (a duração dimensiona os slots da agenda)
            $table->string('modality')->default('presencial');   // presencial | online | ambos
            $table->unsignedInteger('duration_minutes');
            $table->unsignedInteger('buffer_minutes')->default(0);  // intervalo após cada atendimento

            // Preço
            $table->decimal('price', 10, 2);
            $table->decimal('compare_at_price', 10, 2)->nullable();  // "de/por"
            $table->unsignedTinyInteger('max_installments')->default(1);

            // Pagamento antecipado exigido? true = confirma no Brick; false = aceite manual no local
            $table->boolean('requires_prepayment')->default(true);

            // Status / destaque
            $table->boolean('is_active')->default(true);
            $table->boolean('is_featured')->default(false);

            // Social proof (denormalizado para a vitrine)
            $table->decimal('rating', 2, 1)->default(0);
            $table->unsignedInteger('reviews_count')->default(0);
            $table->unsignedInteger('bookings_count')->default(0);
            $table->unsignedInteger('views')->default(0);

            // Denormalização do terapeuta (para card/facetas sem join — como no muda)
            $table->string('professional_name')->nullable();
            $table->string('professional_city')->nullable();
            $table->string('professional_state')->nullable();

            $table->string('cover_path')->nullable();

            $table->timestamps();

            $table->index(['service_category_id', 'is_active']);
            $table->index(['professional_id', 'is_active']);
            $table->index('is_featured');
            $table->index('price');
            $table->index(['professional_city', 'professional_state']);
        });
    }

    public function down(): void
    {
        Schema::dropIfExists('services');
    }
};
