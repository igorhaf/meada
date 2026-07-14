<?php

namespace App\Http\Controllers\Professional;

use App\Http\Controllers\Controller;
use App\Models\Appointment;
use App\Models\AvailabilityBlock;
use App\Models\EventSession;
use Carbon\CarbonImmutable;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;
use Illuminate\View\View;

class AgendaController extends Controller
{
    public function index(Request $request): View
    {
        $user = $request->user();
        $tz = $user->timezone ?: config('pindorama.timezone');
        $view = in_array($request->query('view'), ['day', 'week', 'month'], true) ? $request->query('view') : 'week';
        $date = CarbonImmutable::parse($request->query('date', 'today'), $tz)->startOfDay();
        [$from, $to] = match ($view) {
            'day' => [$date, $date->endOfDay()],
            'month' => [$date->startOfMonth()->startOfWeek(CarbonImmutable::SUNDAY), $date->endOfMonth()->endOfWeek(CarbonImmutable::SATURDAY)],
            default => [$date->startOfWeek(CarbonImmutable::SUNDAY), $date->endOfWeek(CarbonImmutable::SATURDAY)],
        };

        $appointments = Appointment::where('professional_id', $user->id)
            ->with('location')
            ->where('start_at', '>=', $from->utc())
            ->where('start_at', '<=', $to->utc())
            ->orderBy('start_at')
            ->get();
        $sessions = EventSession::whereHas('professionals', fn ($q) => $q->whereKey($user->id))
            ->with('event', 'room')->where('starts_at', '>=', $from->utc())->where('starts_at', '<=', $to->utc())
            ->orderBy('starts_at')->get();
        $blocks = AvailabilityBlock::where('professional_id', $user->id)->with('location')
            ->overlapping($from->utc(), $to->addSecond()->utc())->orderBy('starts_at')->get();

        $items = $appointments->map(fn (Appointment $appointment) => [
            'type' => 'appointment', 'start' => $appointment->start_at, 'end' => $appointment->end_at,
            'title' => $appointment->service_title, 'subtitle' => $appointment->patient_name,
            'location' => $appointment->location?->name, 'url' => route('professional.appointments.show', $appointment),
        ])->concat($sessions->map(fn (EventSession $session) => [
            'type' => 'event', 'start' => $session->starts_at, 'end' => $session->ends_at,
            'title' => $session->event->title, 'subtitle' => $session->title ?: 'Encontro',
            'location' => $session->room?->name ?: $session->location_label, 'url' => route('events.show', $session->event),
        ]))->concat($blocks->map(fn (AvailabilityBlock $block) => [
            'type' => 'block', 'start' => $block->starts_at, 'end' => $block->ends_at,
            'title' => $block->all_day ? 'Bloqueio do dia' : 'Bloqueio', 'subtitle' => $block->reason,
            'location' => $block->location?->name, 'url' => route('professional.availability.edit'),
        ]))->sortBy('start')->groupBy(fn (array $item) => $item['start']->setTimezone($tz)->format('Y-m-d'));

        [$prev, $next] = match ($view) {
            'month' => [$date->subMonthNoOverflow(), $date->addMonthNoOverflow()],
            'week' => [$date->subWeek(), $date->addWeek()],
            default => [$date->subDay(), $date->addDay()],
        };

        return view('professional.agenda', [
            'appointments' => $appointments, 'sessions' => $sessions, 'blocks' => $blocks, 'items' => $items,
            'date' => $date, 'from' => $from, 'to' => $to, 'view' => $view, 'tz' => $tz,
            'prev' => $prev->format('Y-m-d'), 'next' => $next->format('Y-m-d'),
        ]);
    }

    /** JSON feed (para um calendário futuro): agendamentos numa janela. */
    public function events(Request $request): JsonResponse
    {
        $user = $request->user();
        $tz = $user->timezone ?: config('pindorama.timezone');
        $from = CarbonImmutable::parse($request->query('from', 'today'), $tz)->startOfDay();
        $to = CarbonImmutable::parse($request->query('to', $from->addWeek()->format('Y-m-d')), $tz)->endOfDay();

        $appointments = Appointment::where('professional_id', $user->id)
            ->with('location')
            ->where('start_at', '>=', $from->utc())
            ->where('start_at', '<=', $to->utc())
            ->get()
            ->map(fn ($a) => [
                'id' => $a->id,
                'title' => $a->service_title,
                'start' => $a->start_at->setTimezone($tz)->toIso8601String(),
                'end' => $a->end_at->setTimezone($tz)->toIso8601String(),
                'status' => $a->status,
                'location' => $a->location?->name,
                'patient' => $a->patient_name,
                'type' => 'appointment',
            ]);
        $sessions = EventSession::whereHas('professionals', fn ($q) => $q->whereKey($user->id))->with('event','room')->where('starts_at','>=',$from->utc())->where('starts_at','<=',$to->utc())->get()->map(fn($s)=>['id'=>'event-'.$s->id,'title'=>$s->event->title.' · '.($s->title?:'Encontro'),'start'=>$s->starts_at->setTimezone($tz)->toIso8601String(),'end'=>$s->ends_at->setTimezone($tz)->toIso8601String(),'status'=>$s->status,'location'=>$s->room?->name?:$s->location_label,'patient'=>null,'type'=>'event']);

        return response()->json(['events' => $appointments->concat($sessions)->sortBy('start')->values()]);
    }
}
