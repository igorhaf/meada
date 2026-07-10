<?php

namespace App\Http\Controllers\Professional;

use App\Http\Controllers\Controller;
use App\Models\Appointment;
use Carbon\CarbonImmutable;
use Illuminate\Http\Request;
use Illuminate\View\View;

class DashboardController extends Controller
{
    public function index(Request $request): View
    {
        $user = $request->user();
        $tz = $user->timezone ?: config('pindorama.timezone');
        $today = CarbonImmutable::now($tz);

        $todays = Appointment::where('professional_id', $user->id)
            ->whereIn('status', ['pending', 'confirmed'])
            ->where('start_at', '>=', $today->startOfDay()->utc())
            ->where('start_at', '<=', $today->endOfDay()->utc())
            ->orderBy('start_at')
            ->get();

        $stats = [
            'pending' => Appointment::where('professional_id', $user->id)->where('status', 'pending')->count(),
            'upcoming' => Appointment::where('professional_id', $user->id)->where('status', 'confirmed')
                ->where('start_at', '>=', $today->utc())->count(),
            'services' => $user->services()->count(),
            'earnings' => (float) Appointment::where('professional_id', $user->id)
                ->whereNotNull('professional_amount')->sum('professional_amount'),
        ];

        return view('professional.dashboard', compact('todays', 'stats', 'tz'));
    }
}
