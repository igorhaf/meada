<?php

namespace App\Http\Controllers\Professional;

use App\Http\Controllers\Controller;
use App\Models\Appointment;
use App\Services\BookingService;
use App\Services\AppointmentCancellationService;
use App\Services\NotificationService;
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

    public function confirm(Request $request, Appointment $appointment, NotificationService $notifications): RedirectResponse
    {
        $this->authorizePro($request, $appointment);
        $this->booking->confirm($appointment);
        $notifications->appointmentChanged($appointment->fresh(),'Seu agendamento foi confirmado pelo profissional.');

        return back()->with('status', 'Agendamento confirmado.');
    }

    public function complete(Request $request, Appointment $appointment, NotificationService $notifications): RedirectResponse
    {
        $this->authorizePro($request, $appointment);
        $this->booking->complete($appointment);
        $notifications->appointmentChanged($appointment->fresh(),'Seu atendimento foi marcado como concluído.');

        return back()->with('status', 'Agendamento concluído.');
    }

    public function cancel(Request $request, Appointment $appointment, AppointmentCancellationService $cancellation): RedirectResponse
    {
        $this->authorizePro($request, $appointment);
        try{$manual=$cancellation->cancel($appointment,'professional');}catch(\RuntimeException $e){return back()->with('error',$e->getMessage());}

        return back()->with('status', $manual?'Agendamento cancelado; estorno manual sinalizado.':'Agendamento cancelado.');
    }

    public function noShow(Request $request,Appointment $appointment): RedirectResponse
    {
        $this->authorizePro($request,$appointment);$this->booking->noShow($appointment);return back()->with('status','Falta registrada.');
    }

    private function authorizePro(Request $request, Appointment $appointment): void
    {
        abort_unless($appointment->professional_id === $request->user()->id || $request->user()->isRoot(), 403);
    }
}
