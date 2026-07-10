<?php

namespace Tests\Feature;

use App\Exceptions\OutsideHoursException;
use App\Exceptions\SlotUnavailableException;
use App\Models\AttendanceLocation;
use App\Models\ProfessionalAvailability;
use App\Models\Service;
use App\Models\ServiceCategory;
use App\Models\User;
use App\Services\BookingService;
use Carbon\CarbonImmutable;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Tests\TestCase;

class BookingEngineTest extends TestCase
{
    use RefreshDatabase;

    private User $pro;
    private ServiceCategory $category;
    private BookingService $booking;
    private string $tz = 'America/Sao_Paulo';

    protected function setUp(): void
    {
        parent::setUp();
        // Segunda-feira 08:00 (America/Sao_Paulo)
        CarbonImmutable::setTestNow(CarbonImmutable::parse('2026-08-03 08:00:00', $this->tz));

        $this->booking = app(BookingService::class);
        $this->category = ServiceCategory::create(['name' => 'Acupuntura', 'slug' => 'acupuntura']);
        $this->pro = User::create([
            'name' => 'Ana', 'email' => 'ana@t.com', 'password' => 'x',
            'role' => 'professional', 'is_professional' => true, 'timezone' => $this->tz,
        ]);
    }

    protected function tearDown(): void
    {
        CarbonImmutable::setTestNow();
        parent::tearDown();
    }

    private function location(string $name = 'Consultório'): AttendanceLocation
    {
        return $this->pro->attendanceLocations()->create(['name' => $name, 'is_active' => true]);
    }

    private function service(AttendanceLocation $loc, int $duration = 60, int $buffer = 0): Service
    {
        $s = Service::create([
            'professional_id' => $this->pro->id, 'service_category_id' => $this->category->id,
            'title' => 'Sessão', 'slug' => 'sessao-' . uniqid(), 'modality' => 'presencial',
            'duration_minutes' => $duration, 'buffer_minutes' => $buffer, 'price' => 100,
            'max_installments' => 1, 'is_active' => true,
        ]);
        $s->locations()->attach($loc->id);

        return $s;
    }

    /** Mon–Fri window on the given location. */
    private function availability(AttendanceLocation $loc, string $start = '09:00', string $end = '12:00', int $weekday = 1): void
    {
        ProfessionalAvailability::create([
            'professional_id' => $this->pro->id, 'attendance_location_id' => $loc->id,
            'weekday' => $weekday, 'start_time' => $start, 'end_time' => $end, 'is_active' => true,
        ]);
    }

    private function at(string $hm): CarbonImmutable
    {
        return CarbonImmutable::parse("2026-08-03 {$hm}", $this->tz); // a Monday
    }

    public function test_end_at_is_materialized_from_duration(): void
    {
        $loc = $this->location();
        $this->availability($loc);
        $svc = $this->service($loc, 60);

        $appt = $this->booking->book($this->pro, $svc, $loc, $this->at('10:00'), ['name' => 'João']);

        $this->assertEquals($this->at('11:00')->toDateTimeString(), $appt->end_at->setTimezone($this->tz)->toDateTimeString());
        $this->assertSame('pending', $appt->status);
    }

    public function test_overlapping_booking_throws_conflict(): void
    {
        $loc = $this->location();
        $this->availability($loc);
        $svc = $this->service($loc, 60);

        $this->booking->book($this->pro, $svc, $loc, $this->at('10:00'), ['name' => 'A']);

        $this->expectException(SlotUnavailableException::class);
        $this->booking->book($this->pro, $svc, $loc, $this->at('10:30'), ['name' => 'B']);
    }

    public function test_back_to_back_is_allowed(): void
    {
        $loc = $this->location();
        $this->availability($loc);
        $svc = $this->service($loc, 60);

        $this->booking->book($this->pro, $svc, $loc, $this->at('10:00'), ['name' => 'A']); // 10–11
        $second = $this->booking->book($this->pro, $svc, $loc, $this->at('11:00'), ['name' => 'B']); // 11–12

        $this->assertDatabaseCount('appointments', 2);
        $this->assertSame('pending', $second->status);
    }

    public function test_same_time_at_a_different_location_still_conflicts(): void
    {
        $locA = $this->location('A');
        $locB = $this->location('B');
        $this->availability($locA);
        $this->availability($locB);
        $svcA = $this->service($locA, 60);
        $svcB = $this->service($locB, 60);

        $this->booking->book($this->pro, $svcA, $locA, $this->at('10:00'), ['name' => 'A']);

        // Mesmo horário, OUTRO local, MESMO profissional → conflito (agenda única).
        $this->expectException(SlotUnavailableException::class);
        $this->booking->book($this->pro, $svcB, $locB, $this->at('10:00'), ['name' => 'B']);
    }

    public function test_slot_generation_excludes_busy_and_respects_window(): void
    {
        $loc = $this->location();
        $this->availability($loc, '09:00', '12:00'); // 09–12, duração 60
        $svc = $this->service($loc, 60);

        $this->booking->book($this->pro, $svc, $loc, $this->at('10:00'), ['name' => 'A']); // ocupa 10–11

        $slots = $this->booking->availableSlots($this->pro, $svc, $loc, $this->at('00:00'));

        // 09:00 (09–10) e 11:00 (11–12); 10:00 excluído (ocupado). 12:00 não cabe.
        $this->assertSame(['09:00', '11:00'], $slots);
    }

    public function test_outside_hours_throws(): void
    {
        $loc = $this->location();
        $this->availability($loc, '09:00', '12:00');
        $svc = $this->service($loc, 60);

        $this->expectException(OutsideHoursException::class);
        $this->booking->book($this->pro, $svc, $loc, $this->at('14:00'), ['name' => 'X']); // fora da janela
    }

    public function test_aceite_gate_pending_then_confirm(): void
    {
        $loc = $this->location();
        $this->availability($loc);
        $svc = $this->service($loc, 60);

        $appt = $this->booking->book($this->pro, $svc, $loc, $this->at('10:00'), ['name' => 'A']);
        $this->assertSame('pending', $appt->status);
        $this->assertNull($appt->confirmed_at);

        $this->booking->confirm($appt);
        $appt->refresh();
        $this->assertSame('confirmed', $appt->status);
        $this->assertNotNull($appt->confirmed_at);
    }

    public function test_block_removes_slots(): void
    {
        $loc = $this->location();
        $this->availability($loc, '09:00', '12:00');
        $svc = $this->service($loc, 60);

        // Bloqueio 10–11 (folga)
        $this->pro->availabilityBlocks()->create([
            'starts_at' => $this->at('10:00'), 'ends_at' => $this->at('11:00'),
            'all_day' => false, 'reason' => 'Folga',
        ]);

        $slots = $this->booking->availableSlots($this->pro, $svc, $loc, $this->at('00:00'));
        $this->assertSame(['09:00', '11:00'], $slots);
    }
}
