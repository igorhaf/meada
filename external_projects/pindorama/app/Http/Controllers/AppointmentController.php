<?php

namespace App\Http\Controllers;

use App\Models\Appointment;
use App\Services\BookingService;
use Illuminate\Http\RedirectResponse;
use Illuminate\Http\Request;
use Illuminate\View\View;

class AppointmentController extends Controller
{
    public function index(Request $request): View
    {
        $appointments = Appointment::where('customer_id', $request->user()->id)
            ->with('professional')
            ->orderByDesc('start_at')
            ->paginate(15);

        return view('appointments.index', compact('appointments'));
    }

    public function show(Request $request, Appointment $appointment): View
    {
        $this->authorizeView($request, $appointment);
        $appointment->load('professional', 'location');

        return view('appointments.show', compact('appointment'));
    }

    public function cancel(Request $request, Appointment $appointment, BookingService $booking): RedirectResponse
    {
        $this->authorizeView($request, $appointment);
        $booking->cancel($appointment, 'customer');

        return redirect()->route('appointments.show', $appointment)->with('status', 'Agendamento cancelado.');
    }

    private function authorizeView(Request $request, Appointment $appointment): void
    {
        abort_unless($appointment->customer_id === $request->user()->id || $request->user()->isRoot(), 403);
    }
}
