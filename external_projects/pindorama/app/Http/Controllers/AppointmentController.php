<?php

namespace App\Http\Controllers;

use App\Models\Appointment;
use App\Services\BookingService;
use App\Services\AppointmentCancellationService;
use Carbon\CarbonImmutable;
use App\Exceptions\OutsideHoursException;
use App\Exceptions\SlotUnavailableException;
use App\Services\NotificationService;
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
        $appointment->load('professional', 'location', 'service', 'accessPasses');

        return view('appointments.show', compact('appointment'));
    }

    public function cancel(Request $request, Appointment $appointment, AppointmentCancellationService $cancellation): RedirectResponse
    {
        $this->authorizeView($request, $appointment);
        try{$manual=$cancellation->cancel($appointment,'customer');}catch(\RuntimeException $e){return back()->with('error',$e->getMessage());}

        return redirect()->route('appointments.show', $appointment)->with('status', $manual?'Agendamento cancelado. O estorno manual ficou sinalizado ao financeiro.':'Agendamento cancelado.');
    }

    public function reschedule(Request $request,Appointment $appointment,BookingService $booking,NotificationService $notifications): RedirectResponse
    {
        $this->authorizeView($request,$appointment);$data=$request->validate(['date'=>['required','date_format:Y-m-d'],'time'=>['required','date_format:H:i']]);$tz=$appointment->timezone?:config('pindorama.timezone');
        try{$booking->reschedule($appointment,CarbonImmutable::parse($data['date'].' '.$data['time'],$tz));}catch(OutsideHoursException|SlotUnavailableException $e){return back()->with('error',$e->getMessage());}
        $notifications->appointmentChanged($appointment->fresh(),'Seu agendamento foi reagendado.');
        return back()->with('status','Agendamento reagendado.');
    }

    private function authorizeView(Request $request, Appointment $appointment): void
    {
        abort_unless($appointment->customer_id === $request->user()->id || $request->user()->isRoot(), 403);
    }
}
