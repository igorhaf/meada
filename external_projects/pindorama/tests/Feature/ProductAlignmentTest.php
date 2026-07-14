<?php

namespace Tests\Feature;

use App\Models\AttendanceLocation;
use App\Models\AvailabilityBlock;
use App\Models\Event;
use App\Models\ProfessionalAvailability;
use App\Models\Service;
use App\Models\ServiceCategory;
use App\Models\User;
use App\Services\BookingService;
use App\Services\EventService;
use App\Services\TransactionService;
use Carbon\CarbonImmutable;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Illuminate\Support\Facades\Notification;
use Illuminate\Support\Facades\Http;
use Tests\TestCase;

class ProductAlignmentTest extends TestCase
{
    use RefreshDatabase;

    protected function setUp(): void
    {
        parent::setUp();
        Notification::fake();
        CarbonImmutable::setTestNow(CarbonImmutable::parse('2026-08-03 08:00', 'America/Sao_Paulo'));
    }

    protected function tearDown(): void
    {
        CarbonImmutable::setTestNow();
        parent::tearDown();
    }

    public function test_public_registration_can_only_create_a_customer(): void
    {
        $this->post(route('register'), [
            'name' => 'Cliente', 'email' => 'cliente@example.com', 'password' => 'password123',
            'password_confirmation' => 'password123', 'become_professional' => 1,
            'professional_name' => 'Tentativa indevida',
            'accept_terms' => 1,
        ])->assertRedirect(route('home'));

        $this->assertDatabaseHas('users', ['email' => 'cliente@example.com', 'role' => 'customer', 'is_professional' => false]);
    }

    public function test_only_root_can_create_a_professional_and_an_invite_is_generated(): void
    {
        $root = User::factory()->create(['role' => 'root', 'is_professional' => true]);
        $response = $this->actingAs($root)->post(route('admin.professionals.store'), [
            'name' => 'Ana Souza', 'email' => 'ana@example.com', 'professional_name' => 'Ana Terapias',
            'timezone' => 'America/Sao_Paulo',
        ])->assertRedirect();

        $professional = User::where('email', 'ana@example.com')->firstOrFail();
        $this->assertSame('professional', $professional->role);
        $this->assertTrue($professional->is_professional);
        $this->assertDatabaseHas('professional_invites', ['professional_id' => $professional->id]);

        $token = basename(parse_url($response->getSession()->get('invite_url'), PHP_URL_PATH));
        $this->post(route('professional-invites.accept', $token), [
            'password' => 'password123', 'password_confirmation' => 'password123',
        ])->assertSessionHasErrors('accept_terms');
        $this->post(route('professional-invites.accept', $token), [
            'password' => 'password123', 'password_confirmation' => 'password123', 'accept_terms' => 1,
        ])->assertRedirect(route('professional.profile.edit'));
        $this->assertNotNull($professional->fresh()->terms_accepted_at);

        $customer = User::factory()->create();
        $this->actingAs($customer)->get(route('admin.professionals.create'))->assertForbidden();
    }

    public function test_event_supports_multiple_instructors_and_sessions_block_booking_slots(): void
    {
        [$professional, $service, $location] = $this->bookableProfessional('ana@example.com');
        $second = User::factory()->create(['role' => 'professional', 'is_professional' => true, 'professional_name' => 'Bruno', 'professional_slug' => 'bruno']);
        $event = $this->event($professional, 100);
        $event->instructors()->attach([
            $professional->id => ['role' => 'lead', 'revenue_percentage' => 50, 'can_view_financials' => true, 'can_manage_attendance' => true],
            $second->id => ['role' => 'instructor', 'revenue_percentage' => 50, 'can_view_financials' => true, 'can_manage_attendance' => true],
        ]);
        $session = $event->sessions()->create(['title' => 'Encontro 1', 'starts_at' => CarbonImmutable::parse('2026-08-03 10:00','America/Sao_Paulo'), 'ends_at' => CarbonImmutable::parse('2026-08-03 11:00','America/Sao_Paulo'), 'timezone' => 'America/Sao_Paulo', 'modality' => 'presencial']);
        $session->professionals()->sync([$professional->id, $second->id]);

        $slots = app(BookingService::class)->availableSlots($professional, $service, $location, CarbonImmutable::parse('2026-08-03','America/Sao_Paulo'));
        $this->assertSame(['09:00', '11:00'], $slots);
        $this->assertCount(2, $event->instructors);
        $this->assertCount(1, $event->sessions);
    }

    public function test_root_event_form_persists_multiple_instructors_and_course_meetings(): void
    {
        $root=User::factory()->create(['role'=>'root','is_professional'=>true]);$a=User::factory()->create(['role'=>'professional','is_professional'=>true,'professional_name'=>'Ana']);$b=User::factory()->create(['role'=>'professional','is_professional'=>true,'professional_name'=>'Bruno']);
        $response=$this->actingAs($root)->post(route('admin.events.store'),['title'=>'Formação em Reiki','description'=>'Curso','type'=>'curso','modality'=>'presencial','capacity'=>20,'price'=>300,'discount_percent'=>0,'house_percentage'=>20,'status'=>'published','reminder_hours'=>24,'instructors'=>[$a->id,$b->id],'sessions'=>[['title'=>'Módulo 1','starts_at'=>'2026-08-10T10:00','ends_at'=>'2026-08-10T12:00','room_id'=>'','location_label'=>'Sala 1','meeting_link'=>''],['title'=>'Módulo 2','starts_at'=>'2026-08-17T10:00','ends_at'=>'2026-08-17T12:00','room_id'=>'','location_label'=>'Sala 1','meeting_link'=>'']]]);
        $response->assertRedirect()->assertSessionHasNoErrors();$event=Event::where('title','Formação em Reiki')->firstOrFail();$this->assertCount(2,$event->instructors);$this->assertCount(2,$event->sessions);$this->assertSame($root->id,$event->created_by);
    }

    public function test_paid_event_creates_transaction_splits_and_passport(): void
    {
        $lead = User::factory()->create(['role'=>'professional','is_professional'=>true,'professional_name'=>'Ana','professional_slug'=>'ana']);
        $co = User::factory()->create(['role'=>'professional','is_professional'=>true,'professional_name'=>'Bruno','professional_slug'=>'bruno']);
        $customer = User::factory()->create();
        $event = $this->event($lead, 200);
        $event->instructors()->attach([$lead->id=>['role'=>'lead','revenue_percentage'=>50,'can_view_financials'=>true,'can_manage_attendance'=>true],$co->id=>['role'=>'instructor','revenue_percentage'=>50,'can_view_financials'=>true,'can_manage_attendance'=>true]]);
        $session=$event->sessions()->create(['starts_at'=>$event->starts_at,'ends_at'=>$event->ends_at,'timezone'=>$event->timezone,'modality'=>'presencial']);$session->professionals()->sync([$lead->id,$co->id]);
        $registration=app(EventService::class)->register($event,['name'=>$customer->name,'email'=>$customer->email],$customer);
        $transaction=app(TransactionService::class)->apply($registration,'approved',null,'simulado');

        $this->assertTrue($registration->fresh()->isPaid());
        $this->assertCount(2,$transaction->splits);
        $this->assertEquals(100.0,(float)$transaction->splits->first()->amount);
        $this->assertDatabaseHas('access_passes',['passable_id'=>$registration->id,'status'=>'valid']);
        $root=User::factory()->create(['role'=>'root','is_professional'=>true]);
        $this->actingAs($root)->get(route('admin.payments',['professional_id'=>$lead->id]))
            ->assertOk()->assertViewHas('totals',fn(array $totals)=>(float)$totals['professional']===100.0);
        $this->actingAs($root)->get(route('admin.payments.pdf',['professional_id'=>$lead->id]))
            ->assertOk()->assertHeader('content-type','application/pdf');
    }

    public function test_root_can_check_in_a_valid_passport(): void
    {
        $lead=User::factory()->create(['role'=>'professional','is_professional'=>true,'professional_name'=>'Ana']);$customer=User::factory()->create();$root=User::factory()->create(['role'=>'root','is_professional'=>true]);
        $event=$this->event($lead,0,true);$event->instructors()->attach($lead->id,['role'=>'lead','revenue_percentage'=>100,'can_view_financials'=>true,'can_manage_attendance'=>true]);
        $registration=app(EventService::class)->register($event,['name'=>$customer->name,'email'=>$customer->email],$customer);$pass=$registration->accessPasses()->firstOrFail();$pass->update(['valid_from'=>now()->subMinute()]);

        $this->actingAs($root)->post(route('passes.check-in',$pass))->assertRedirect();
        $this->assertDatabaseHas('access_passes',['id'=>$pass->id,'status'=>'used','checked_in_by'=>$root->id]);
        $this->assertDatabaseHas('event_registrations',['id'=>$registration->id,'status'=>'attended']);
    }

    public function test_new_root_and_professional_operational_pages_render(): void
    {
        $root=User::factory()->create(['role'=>'root','is_professional'=>true]);
        $professional=User::factory()->create(['role'=>'professional','is_professional'=>true,'professional_name'=>'Ana','professional_slug'=>'ana','timezone'=>'America/Sao_Paulo']);
        AvailabilityBlock::create(['professional_id'=>$professional->id,'starts_at'=>CarbonImmutable::parse('2026-08-03 13:00','America/Sao_Paulo'),'ends_at'=>CarbonImmutable::parse('2026-08-03 14:00','America/Sao_Paulo'),'reason'=>'Compromisso pessoal']);
        foreach ([route('admin.professionals.show',$professional),route('admin.events.index'),route('admin.events.create'),route('admin.calendar'),route('admin.payments'),route('admin.bookings.create'),route('admin.passes.lookup.form')] as $url) {
            $this->actingAs($root)->get($url)->assertOk();
        }
        $this->actingAs($root)->get(route('admin.payments.pdf'))->assertOk()->assertHeader('content-type','application/pdf');
        foreach ([route('professional.finance.index'),route('professional.events.index'),route('professional.agenda'),route('professional.profile.edit'),route('professional.passes.lookup.form')] as $url) {
            $this->actingAs($professional)->get($url)->assertOk();
        }
        $this->actingAs($professional)->get(route('professional.agenda',['date'=>'2026-08-03','view'=>'month']))->assertOk()->assertSee('Compromisso pessoal');
    }

    public function test_mercado_pago_webhook_requires_and_accepts_the_official_hmac_manifest(): void
    {
        [$professional,$service,$location]=$this->bookableProfessional('webhook@example.com');$customer=User::factory()->create();
        $appointment=app(BookingService::class)->book($professional,$service,$location,CarbonImmutable::parse('2026-08-03 09:00','America/Sao_Paulo'),['name'=>$customer->name,'email'=>$customer->email],$customer);
        config(['services.mercadopago.enabled'=>true,'services.mercadopago.access_token'=>'TEST-token','services.mercadopago.public_key'=>'TEST-key','services.mercadopago.webhook_secret'=>'top-secret']);
        $this->postJson(route('mp.webhook').'?data.id=123',['type'=>'payment'])->assertUnauthorized();

        Http::fake(['api.mercadopago.com/v1/payments/123'=>Http::response(['id'=>'123','status'=>'approved','external_reference'=>$appointment->reference,'payment_method_id'=>'pix'])]);
        $ts=(string)now()->timestamp;$requestId='request-123';$manifest="id:123;request-id:{$requestId};ts:{$ts};";$signature=hash_hmac('sha256',$manifest,'top-secret');
        $this->withHeaders(['x-request-id'=>$requestId,'x-signature'=>"ts={$ts},v1={$signature}"])->postJson(route('mp.webhook').'?data.id=123',['type'=>'payment'])->assertOk();
        $this->assertSame('approved',$appointment->fresh()->payment_status);
        $this->assertDatabaseHas('access_passes',['passable_id'=>$appointment->id,'status'=>'valid']);
    }

    private function bookableProfessional(string $email): array
    {
        $professional=User::factory()->create(['email'=>$email,'role'=>'professional','is_professional'=>true,'professional_name'=>'Ana','professional_slug'=>'ana','timezone'=>'America/Sao_Paulo']);
        $category=ServiceCategory::create(['name'=>'Acupuntura','slug'=>'acupuntura']);
        $location=$professional->attendanceLocations()->create(['name'=>'Consultório','is_active'=>true]);
        ProfessionalAvailability::create(['professional_id'=>$professional->id,'attendance_location_id'=>$location->id,'weekday'=>1,'start_time'=>'09:00','end_time'=>'12:00','is_active'=>true]);
        $service=Service::create(['professional_id'=>$professional->id,'service_category_id'=>$category->id,'title'=>'Sessão','slug'=>'sessao','modality'=>'presencial','duration_minutes'=>60,'buffer_minutes'=>0,'price'=>100,'max_installments'=>1,'is_active'=>true]);$service->locations()->attach($location);
        return [$professional,$service,$location];
    }

    private function event(User $professional,float $price,bool $free=false): Event
    {
        return Event::create(['professional_id'=>$professional->id,'created_by'=>$professional->id,'title'=>'Curso terapêutico','slug'=>'curso-'.uniqid(),'type'=>'curso','modality'=>'presencial','starts_at'=>CarbonImmutable::parse('2026-08-10 10:00','America/Sao_Paulo'),'ends_at'=>CarbonImmutable::parse('2026-08-10 12:00','America/Sao_Paulo'),'timezone'=>'America/Sao_Paulo','capacity'=>20,'price'=>$price,'is_free'=>$free,'status'=>'published','house_percentage'=>0]);
    }
}
