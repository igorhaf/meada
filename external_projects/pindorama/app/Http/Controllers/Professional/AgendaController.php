<?php

namespace App\Http\Controllers\Professional;

use App\Http\Controllers\Controller;
use App\Models\Appointment;
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

        $date = $request->query('date')
            ? CarbonImmutable::parse($request->query('date'), $tz)->startOfDay()
            : CarbonImmutable::now($tz)->startOfDay();

        $appointments = Appointment::where('professional_id', $user->id)
            ->with('location')
            ->where('start_at', '>=', $date->utc())
            ->where('start_at', '<', $date->addDay()->utc())
            ->orderBy('start_at')
            ->get();

        return view('professional.agenda', [
            'appointments' => $appointments,
            'date' => $date,
            'tz' => $tz,
            'prev' => $date->subDay()->format('Y-m-d'),
            'next' => $date->addDay()->format('Y-m-d'),
        ]);
    }

    /** JSON feed (para um calendário futuro): agendamentos numa janela. */
    public function events(Request $request): JsonResponse
    {
        $user = $request->user();
        $tz = $user->timezone ?: config('pindorama.timezone');
        $from = CarbonImmutable::parse($request->query('from', 'today'), $tz)->startOfDay();
        $to = CarbonImmutable::parse($request->query('to', $from->addWeek()->format('Y-m-d')), $tz)->endOfDay();

        $events = Appointment::where('professional_id', $user->id)
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
            ]);

        return response()->json(['events' => $events]);
    }
}
