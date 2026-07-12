<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    public function up(): void
    {
        Schema::table('pages', function (Blueprint $table) {
            // note | vault | calendar | tasks | registro | meds | diet | gastos
            $table->string('kind', 20)->default('note');
            // metadados por tipo: template do registro, perfil da dieta, sheet de gastos etc.
            $table->jsonb('meta')->nullable();
        });

        Schema::table('users', function (Blueprint $table) {
            $table->bigInteger('telegram_chat_id')->nullable()->unique();
            $table->string('telegram_link_code', 12)->nullable();
            // ID do Google Calendar do usuário (gmail) — usado pelo sync via service account.
            $table->string('google_calendar_id')->nullable();
        });

        Schema::create('vault_entries', function (Blueprint $table) {
            $table->id();
            $table->foreignId('page_id')->constrained('pages')->cascadeOnDelete();
            $table->string('title');
            $table->string('username')->nullable();
            $table->text('secret'); // cifrado em repouso (cast encrypted)
            $table->string('url')->nullable();
            $table->text('notes')->nullable();
            $table->unsignedInteger('position')->default(0);
            $table->timestamps();
        });

        Schema::create('calendar_events', function (Blueprint $table) {
            $table->id();
            $table->foreignId('page_id')->constrained('pages')->cascadeOnDelete();
            $table->string('title');
            $table->timestampTz('starts_at');
            $table->timestampTz('ends_at')->nullable();
            $table->boolean('all_day')->default(false);
            $table->string('recurrence', 20)->default('none'); // none|daily|weekly|monthly|yearly
            $table->text('notes')->nullable();
            // mapa calendar_id_google => event_id do evento espelhado no Google Calendar
            $table->jsonb('google_event_ids')->nullable();
            $table->timestamps();

            $table->index(['page_id', 'starts_at']);
        });

        Schema::create('expense_entries', function (Blueprint $table) {
            $table->id();
            $table->foreignId('page_id')->constrained('pages')->cascadeOnDelete();
            $table->date('date');
            $table->string('description');
            $table->string('category')->nullable();
            $table->integer('amount_cents');
            $table->string('paid_by')->nullable(); // Igor | Aline | …
            $table->string('card')->nullable();    // cartão usado (texto livre)
            $table->boolean('synced_to_sheet')->default(false);
            $table->timestamps();

            $table->index(['page_id', 'date']);
        });

        Schema::create('task_items', function (Blueprint $table) {
            $table->id();
            $table->foreignId('page_id')->constrained('pages')->cascadeOnDelete();
            $table->string('content');
            $table->foreignId('assigned_user_id')->nullable()->constrained('users')->nullOnDelete();
            $table->date('due_date')->nullable();
            $table->boolean('done')->default(false);
            $table->unsignedInteger('position')->default(0);
            $table->timestamps();

            $table->index(['page_id', 'done', 'position']);
        });

        Schema::create('registro_entries', function (Blueprint $table) {
            $table->id();
            $table->foreignId('page_id')->constrained('pages')->cascadeOnDelete();
            $table->jsonb('data'); // valores conforme o template (pages.meta->template)
            $table->unsignedInteger('position')->default(0);
            $table->timestamps();
        });

        Schema::create('medications', function (Blueprint $table) {
            $table->id();
            $table->foreignId('page_id')->constrained('pages')->cascadeOnDelete();
            $table->string('person'); // Igor, Aline, nome do filho, cachorro…
            $table->string('name');
            $table->string('dose')->nullable();
            $table->jsonb('schedule_times'); // ["08:00","20:00"]
            $table->boolean('controlled')->default(false);
            $table->date('prescription_until')->nullable();
            $table->integer('stock')->nullable();
            $table->integer('low_stock_threshold')->nullable();
            $table->text('notes')->nullable();
            $table->boolean('active')->default(true);
            $table->timestamps();

            $table->index(['page_id', 'active']);
        });

        Schema::create('medication_logs', function (Blueprint $table) {
            $table->id();
            $table->foreignId('medication_id')->constrained('medications')->cascadeOnDelete();
            $table->timestampTz('taken_at');
            $table->foreignId('taken_by')->nullable()->constrained('users')->nullOnDelete();
            $table->timestamps();

            $table->index(['medication_id', 'taken_at']);
        });
    }

    public function down(): void
    {
        Schema::dropIfExists('medication_logs');
        Schema::dropIfExists('medications');
        Schema::dropIfExists('expense_entries');
        Schema::dropIfExists('registro_entries');
        Schema::dropIfExists('task_items');
        Schema::dropIfExists('calendar_events');
        Schema::dropIfExists('vault_entries');
        Schema::table('users', function (Blueprint $table) {
            $table->dropColumn(['telegram_chat_id', 'telegram_link_code']);
        });
        Schema::table('pages', function (Blueprint $table) {
            $table->dropColumn(['kind', 'meta']);
        });
    }
};
