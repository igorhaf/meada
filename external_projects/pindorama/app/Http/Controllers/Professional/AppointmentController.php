<?php

namespace App\Http\Controllers\Professional;

use App\Http\Controllers\Controller;
use App\Models\Appointment;
use App\Services\BookingService;
use Illuminate\Http\RedirectResponse;
use Illuminate\Http\Request;
use Illuminate\View\View;

class AppointmentController extends Controller
{
    public function __construct(private BookingService $booking) {}

    public function show(Request $request, Appointment $appointment): View
    {
        $this->authorizePro($request, $appointment);
        $appointment->load('location', 'customer', 'service');

        return view('professional.appointments.show', compact('appointment'));
    }

    public function confirm(Request $request, Appointment $appointment): RedirectResponse
    {
        $this->authorizePro($request, $appointment);
        $this->booking->confirm($appointment);

        return back()->with('status', 'Agendamento confirmado.');
    }

    public function complete(Request $request, Appointment $appointment): RedirectResponse
    {
        $this->authorizePro($request, $appointment);
        $this->booking->complete($appointment);

        return back()->with('status', 'Agendamento concluído.');
    }

    public function cancel(Request $request, Appointment $appointment): RedirectResponse
    {
        $this->authorizePro($request, $appointment);
        $this->booking->cancel($appointment, 'professional');

        return back()->with('status', 'Agendamento cancelado.');
    }

    private function authorizePro(Request $request, Appointment $appointment): void
    {
        abort_unless($appointment->professional_id === $request->user()->id || $request->user()->isRoot(), 403);
    }
}
