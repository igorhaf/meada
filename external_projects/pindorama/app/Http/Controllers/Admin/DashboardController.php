<?php

namespace App\Http\Controllers\Admin;

use App\Http\Controllers\Controller;
use App\Models\Appointment;
use App\Models\Service;
use App\Models\User;
use Illuminate\Contracts\View\View;

class DashboardController extends Controller
{
    public function index(): View
    {
        $paid = ['approved', 'authorized'];

        $stats = [
            'professionals' => User::where('is_professional', true)->where('role', '!=', 'root')->count(),
            'services' => Service::count(),
            'customers' => User::where('role', 'customer')->count(),
            'appointments' => Appointment::count(),
            'revenue' => (float) Appointment::whereIn('payment_status', $paid)->sum('total'),
            'commission' => (float) Appointment::whereIn('payment_status', $paid)->sum('commission_amount'),
        ];

        $recent = Appointment::with('professional')->latest()->take(8)->get();

        $topProfessionals = User::where('is_professional', true)->where('role', '!=', 'root')
            ->withCount('services')
            ->withSum('appointmentsAsProfessional as revenue', 'total')
            ->orderByDesc('revenue')
            ->take(5)
            ->get();

        return view('admin.dashboard', compact('stats', 'recent', 'topProfessionals'));
    }
}
