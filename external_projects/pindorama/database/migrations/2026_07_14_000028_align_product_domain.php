<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    public function up(): void
    {
        DB::table('banners')->where('link_url','/seja-terapeuta')->update(['title'=>'Encontre seu terapeuta','subtitle'=>'Conheça profissionais e práticas disponíveis','cta_label'=>'Ver profissionais','link_url'=>'/terapeutas']);
        DB::table('pages')->updateOrInsert(['slug'=>'termos'],['title'=>'Termos de uso','body'=>'Ao usar o Pindorama, você concorda com as regras de cadastro, agendamento, pagamento, cancelamento e participação informadas no site. Os profissionais respondem pelo conteúdo técnico de seus atendimentos; a plataforma administra divulgação, agenda e pagamentos conforme o checkout.','created_at'=>now(),'updated_at'=>now()]);
        Schema::table('users', function (Blueprint $table) {
            $table->string('instagram_url')->nullable();
            $table->string('facebook_url')->nullable();
            $table->string('youtube_url')->nullable();
            $table->string('website_url')->nullable();
            $table->boolean('is_active')->default(true)->index();
            $table->timestamp('terms_accepted_at')->nullable();
            $table->timestamp('privacy_accepted_at')->nullable();
        });

        Schema::table('events', function (Blueprint $table) {
            $table->foreignId('created_by')->nullable()->constrained('users')->nullOnDelete();
            $table->foreignId('room_id')->nullable()->constrained('rooms')->nullOnDelete();
            $table->decimal('house_percentage', 5, 2)->default(0);
        });

        DB::table('events')->whereNull('created_by')->update([
            'created_by' => DB::raw('professional_id'),
        ]);

        Schema::create('event_professional', function (Blueprint $table) {
            $table->id();
            $table->foreignId('event_id')->constrained()->cascadeOnDelete();
            $table->foreignId('professional_id')->constrained('users')->cascadeOnDelete();
            $table->string('role')->default('instructor');
            $table->boolean('can_view_financials')->default(true);
            $table->boolean('can_manage_attendance')->default(true);
            $table->decimal('revenue_percentage', 5, 2)->default(0);
            $table->unsignedInteger('position')->default(0);
            $table->timestamps();
            $table->unique(['event_id', 'professional_id']);
        });

        foreach (DB::table('events')->get(['id', 'professional_id']) as $event) {
            DB::table('event_professional')->insertOrIgnore([
                'event_id' => $event->id, 'professional_id' => $event->professional_id,
                'role' => 'lead', 'can_view_financials' => true, 'can_manage_attendance' => true, 'revenue_percentage' => 100,
                'position' => 0, 'created_at' => now(), 'updated_at' => now(),
            ]);
        }

        Schema::create('event_sessions', function (Blueprint $table) {
            $table->id();
            $table->foreignId('event_id')->constrained()->cascadeOnDelete();
            $table->foreignId('room_id')->nullable()->constrained('rooms')->nullOnDelete();
            $table->string('title')->nullable();
            $table->timestampTz('starts_at');
            $table->timestampTz('ends_at');
            $table->string('timezone')->default('America/Sao_Paulo');
            $table->string('modality')->default('presencial');
            $table->string('location_label')->nullable();
            $table->string('meeting_link')->nullable();
            $table->string('status')->default('scheduled');
            $table->timestamps();
            $table->index(['starts_at', 'ends_at']);
            $table->index(['room_id', 'starts_at']);
        });

        foreach (DB::table('events')->get() as $event) {
            $start = \Carbon\CarbonImmutable::parse($event->starts_at);
            DB::table('event_sessions')->insert([
                'event_id' => $event->id, 'room_id' => $event->room_id, 'title' => $event->title,
                'starts_at' => $start, 'ends_at' => $event->ends_at ?: $start->addHour(),
                'timezone' => $event->timezone, 'modality' => $event->modality,
                'location_label' => $event->location_label, 'status' => 'scheduled',
                'created_at' => now(), 'updated_at' => now(),
            ]);
        }

        Schema::create('event_session_professional', function (Blueprint $table) {
            $table->foreignId('event_session_id')->constrained()->cascadeOnDelete();
            $table->foreignId('professional_id')->constrained('users')->cascadeOnDelete();
            $table->primary(['event_session_id', 'professional_id']);
        });

        foreach (DB::table('event_sessions')->get(['id', 'event_id']) as $session) {
            foreach (DB::table('event_professional')->where('event_id', $session->event_id)->pluck('professional_id') as $professionalId) {
                DB::table('event_session_professional')->insertOrIgnore([
                    'event_session_id' => $session->id, 'professional_id' => $professionalId,
                ]);
            }
        }

        Schema::table('event_registrations', function (Blueprint $table) {
            $table->decimal('house_amount', 10, 2)->nullable();
            $table->decimal('professional_amount', 10, 2)->nullable();
            $table->timestamp('cancelled_at')->nullable();
            $table->timestamp('checked_in_at')->nullable();
            $table->foreignId('checked_in_by')->nullable()->constrained('users')->nullOnDelete();
            $table->timestamp('consent_at')->nullable();
        });

        Schema::table('appointments', function (Blueprint $table) {
            $table->boolean('health_data_consent')->default(false);
            $table->timestamp('consent_at')->nullable();
        });

        Schema::create('transactions', function (Blueprint $table) {
            $table->id();
            $table->string('reference')->unique();
            $table->nullableMorphs('payable');
            $table->foreignId('customer_id')->nullable()->constrained('users')->nullOnDelete();
            $table->foreignId('professional_id')->nullable()->constrained('users')->nullOnDelete();
            $table->decimal('gross_amount', 10, 2);
            $table->decimal('discount_amount', 10, 2)->default(0);
            $table->decimal('house_amount', 10, 2)->default(0);
            $table->decimal('professional_amount', 10, 2)->default(0);
            $table->string('provider')->default('mercadopago');
            $table->string('provider_payment_id')->nullable()->index();
            $table->string('payment_method')->nullable();
            $table->string('status')->default('pending')->index();
            $table->string('idempotency_key')->unique();
            $table->timestamp('approved_at')->nullable();
            $table->timestamp('refunded_at')->nullable();
            $table->timestamp('settled_at')->nullable();
            $table->json('metadata')->nullable();
            $table->timestamps();
        });

        Schema::create('transaction_splits', function (Blueprint $table) {
            $table->id();
            $table->foreignId('transaction_id')->constrained()->cascadeOnDelete();
            $table->foreignId('professional_id')->constrained('users')->cascadeOnDelete();
            $table->decimal('percentage', 5, 2);
            $table->decimal('amount', 10, 2);
            $table->timestamps();
            $table->unique(['transaction_id','professional_id']);
        });

        Schema::create('payouts', function (Blueprint $table) {
            $table->id();
            $table->string('reference')->unique();
            $table->foreignId('professional_id')->constrained('users')->cascadeOnDelete();
            $table->decimal('amount', 10, 2);
            $table->string('status')->default('pending');
            $table->date('period_start');
            $table->date('period_end');
            $table->timestamp('paid_at')->nullable();
            $table->text('notes')->nullable();
            $table->foreignId('created_by')->nullable()->constrained('users')->nullOnDelete();
            $table->timestamps();
        });

        Schema::create('access_passes', function (Blueprint $table) {
            $table->id();
            $table->string('public_code', 32)->unique();
            $table->string('token_hash', 64)->unique();
            $table->morphs('passable');
            $table->foreignId('holder_id')->nullable()->constrained('users')->nullOnDelete();
            $table->string('holder_name');
            $table->string('status')->default('valid')->index();
            $table->timestamp('valid_from')->nullable();
            $table->timestamp('valid_until')->nullable();
            $table->timestamp('used_at')->nullable();
            $table->foreignId('checked_in_by')->nullable()->constrained('users')->nullOnDelete();
            $table->string('check_in_location')->nullable();
            $table->timestamps();
        });

        foreach (DB::table('appointments')->get() as $appointment) {
            DB::table('transactions')->insert([
                'reference' => 'TX-APPT-'.$appointment->id, 'payable_type' => App\Models\Appointment::class, 'payable_id' => $appointment->id,
                'customer_id' => $appointment->customer_id, 'professional_id' => $appointment->professional_id,
                'gross_amount' => $appointment->total, 'discount_amount' => 0,
                'house_amount' => $appointment->commission_amount ?? 0, 'professional_amount' => $appointment->professional_amount ?? $appointment->total,
                'provider' => $appointment->payment_method === 'simulado' ? 'simulado' : 'mercadopago', 'provider_payment_id' => $appointment->mp_payment_id,
                'payment_method' => $appointment->payment_method, 'status' => $appointment->payment_status,
                'idempotency_key' => hash('sha256', App\Models\Appointment::class.':'.$appointment->id),
                'approved_at' => $appointment->paid_at, 'created_at' => $appointment->created_at, 'updated_at' => now(),
            ]);
        }
        foreach (DB::table('event_registrations')->join('events','events.id','=','event_registrations.event_id')->select('event_registrations.*','events.professional_id','events.house_percentage')->get() as $registration) {
            $house = round((float) $registration->amount * (float) $registration->house_percentage / 100, 2);
            DB::table('transactions')->insert([
                'reference' => 'TX-EVENT-'.$registration->id, 'payable_type' => App\Models\EventRegistration::class, 'payable_id' => $registration->id,
                'customer_id' => $registration->customer_id, 'professional_id' => $registration->professional_id,
                'gross_amount' => $registration->amount, 'discount_amount' => $registration->discount_amount,
                'house_amount' => $house, 'professional_amount' => (float) $registration->amount - $house,
                'provider' => $registration->payment_method === 'simulado' ? 'simulado' : 'mercadopago', 'provider_payment_id' => $registration->mp_payment_id,
                'payment_method' => $registration->payment_method, 'status' => $registration->payment_status,
                'idempotency_key' => hash('sha256', App\Models\EventRegistration::class.':'.$registration->id),
                'approved_at' => $registration->paid_at, 'created_at' => $registration->created_at, 'updated_at' => now(),
            ]);
        }

        foreach (DB::table('transactions')->get() as $transaction) {
            $professionals = collect([$transaction->professional_id]);
            if ($transaction->payable_type === App\Models\EventRegistration::class) {
                $eventId = DB::table('event_registrations')->where('id',$transaction->payable_id)->value('event_id');
                $professionals = DB::table('event_professional')->where('event_id',$eventId)->pluck('professional_id');
            }
            $count=max(1,$professionals->count());$allocated=0;
            foreach ($professionals->values() as $index=>$professionalId) {
                $amount=$index===$count-1?(float)$transaction->professional_amount-$allocated:round((float)$transaction->professional_amount/$count,2);$allocated+=$amount;
                DB::table('transaction_splits')->insert(['transaction_id'=>$transaction->id,'professional_id'=>$professionalId,'percentage'=>round(100/$count,2),'amount'=>$amount,'created_at'=>now(),'updated_at'=>now()]);
            }
        }

        foreach (DB::table('appointments')->whereIn('payment_status',['approved','authorized'])->get() as $appointment) {
            $code = strtoupper(\Illuminate\Support\Str::random(12));
            DB::table('access_passes')->insert(['public_code'=>$code,'token_hash'=>hash_hmac('sha256',$code,(string)config('app.key')),'passable_type'=>App\Models\Appointment::class,'passable_id'=>$appointment->id,'holder_id'=>$appointment->customer_id,'holder_name'=>$appointment->patient_name,'status'=>'valid','valid_from'=>Carbon\CarbonImmutable::parse($appointment->start_at)->subHours(2),'valid_until'=>Carbon\CarbonImmutable::parse($appointment->end_at)->addDay(),'created_at'=>now(),'updated_at'=>now()]);
        }
        foreach (DB::table('event_registrations')->join('events','events.id','=','event_registrations.event_id')->whereIn('event_registrations.payment_status',['approved','authorized'])->select('event_registrations.*','events.starts_at','events.ends_at')->get() as $registration) {
            $code = strtoupper(\Illuminate\Support\Str::random(12));
            DB::table('access_passes')->insert(['public_code'=>$code,'token_hash'=>hash_hmac('sha256',$code,(string)config('app.key')),'passable_type'=>App\Models\EventRegistration::class,'passable_id'=>$registration->id,'holder_id'=>$registration->customer_id,'holder_name'=>$registration->participant_name,'status'=>'valid','valid_from'=>Carbon\CarbonImmutable::parse($registration->starts_at)->subHours(2),'valid_until'=>Carbon\CarbonImmutable::parse($registration->ends_at?:$registration->starts_at)->addDay(),'created_at'=>now(),'updated_at'=>now()]);
        }

        Schema::create('professional_memberships', function (Blueprint $table) {
            $table->id();
            $table->foreignId('professional_id')->constrained('users')->cascadeOnDelete();
            $table->foreignId('user_id')->constrained('users')->cascadeOnDelete();
            $table->string('role')->default('assistant');
            $table->boolean('can_manage_profile')->default(false);
            $table->boolean('can_manage_agenda')->default(true);
            $table->boolean('can_view_financials')->default(false);
            $table->timestamps();
            $table->unique(['professional_id', 'user_id']);
        });

        Schema::create('professional_invites', function (Blueprint $table) {
            $table->id();
            $table->foreignId('professional_id')->constrained('users')->cascadeOnDelete();
            $table->string('token_hash', 64)->unique();
            $table->timestamp('expires_at');
            $table->timestamp('accepted_at')->nullable();
            $table->foreignId('created_by')->nullable()->constrained('users')->nullOnDelete();
            $table->timestamps();
        });

        Schema::create('customer_invites', function (Blueprint $table) {
            $table->id();$table->foreignId('customer_id')->constrained('users')->cascadeOnDelete();$table->string('token_hash',64)->unique();$table->timestamp('expires_at');$table->timestamp('accepted_at')->nullable();$table->foreignId('created_by')->nullable()->constrained('users')->nullOnDelete();$table->timestamps();
        });

        Schema::create('audit_logs', function (Blueprint $table) {
            $table->id();
            $table->foreignId('actor_id')->nullable()->constrained('users')->nullOnDelete();
            $table->string('action');
            $table->nullableMorphs('subject');
            $table->json('before')->nullable();
            $table->json('after')->nullable();
            $table->string('reason')->nullable();
            $table->string('ip_address', 45)->nullable();
            $table->timestamps();
        });
    }

    public function down(): void
    {
        DB::table('pages')->where('slug','termos')->delete();
        Schema::dropIfExists('audit_logs');
        Schema::dropIfExists('customer_invites');
        Schema::dropIfExists('professional_invites');
        Schema::dropIfExists('professional_memberships');
        Schema::dropIfExists('access_passes');
        Schema::dropIfExists('payouts');
        Schema::dropIfExists('transaction_splits');
        Schema::dropIfExists('transactions');
        Schema::table('event_registrations', function (Blueprint $table) {
            $table->dropConstrainedForeignId('checked_in_by');
            $table->dropColumn(['house_amount', 'professional_amount', 'cancelled_at', 'checked_in_at','consent_at']);
        });
        Schema::table('appointments', fn (Blueprint $table) => $table->dropColumn(['health_data_consent','consent_at']));
        Schema::dropIfExists('event_session_professional');
        Schema::dropIfExists('event_sessions');
        Schema::dropIfExists('event_professional');
        Schema::table('events', function (Blueprint $table) {
            $table->dropConstrainedForeignId('created_by');
            $table->dropConstrainedForeignId('room_id');
            $table->dropColumn('house_percentage');
        });
        Schema::table('users', fn (Blueprint $table) => $table->dropColumn(['instagram_url', 'facebook_url', 'youtube_url', 'website_url', 'is_active','terms_accepted_at','privacy_accepted_at']));
    }
};
