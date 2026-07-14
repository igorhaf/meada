<?php

namespace App\Http\Controllers;

use App\Exceptions\OutsideHoursException;
use App\Exceptions\SlotUnavailableException;
use App\Models\AttendanceLocation;
use App\Models\Service;
use App\Models\User;
use App\Services\AuditService;
use App\Services\BookingService;
use App\Services\TransactionService;
use App\Services\NotificationService;
use Carbon\CarbonImmutable;
use Illuminate\Http\RedirectResponse;
use Illuminate\Http\Request;
use Illuminate\View\View;

class StaffBookingController extends Controller
{
    public function create(Request $request): View
    {
        $root=$request->user()->isRoot();
        $services=Service::active()->with('professional','locations')->when(!$root,fn($q)=>$q->where('professional_id',$request->user()->id))->orderBy('title')->get();
        $customers=User::where('role','customer')->orderBy('name')->get();
        return view('staff.booking-form',compact('services','customers','root'));
    }

    public function store(Request $request, BookingService $booking, TransactionService $transactions, AuditService $audit, NotificationService $notifications): RedirectResponse
    {
        $data=$request->validate(['service_id'=>['required','exists:services,id'],'attendance_location_id'=>['required','exists:attendance_locations,id'],'customer_id'=>['required','exists:users,id'],'start_at'=>['required','date'],'patient_phone'=>['nullable','string','max:40'],'notes'=>['nullable','string','max:2000'],'mark_paid'=>['nullable','boolean'],'health_data_consent'=>['accepted']]);
        $service=Service::with('professional')->findOrFail($data['service_id']); $professional=$service->professional; $location=AttendanceLocation::findOrFail($data['attendance_location_id']);
        abort_unless($request->user()->isRoot()||$professional->id===$request->user()->id,403);
        $customer=User::findOrFail($data['customer_id']);
        try{$appointment=$booking->book($professional,$service,$location,CarbonImmutable::parse($data['start_at'],$professional->timezone),['name'=>$customer->name,'email'=>$customer->email,'phone'=>$data['patient_phone']??null,'notes'=>$data['notes']??null,'consent'=>true],$customer);}catch(OutsideHoursException|SlotUnavailableException $e){return back()->withInput()->withErrors(['start_at'=>$e->getMessage()]);}
        $booking->confirm($appointment);
        $notifications->appointmentCreated($appointment);
        if($request->boolean('mark_paid'))$transactions->apply($appointment,'approved',null,'manual');
        $audit->record('appointment.created_by_staff',$appointment,[],['customer_id'=>$customer->id]);
        return $request->user()->isRoot()?redirect()->route('admin.calendar')->with('status','Agendamento criado.'):redirect()->route('professional.appointments.show',$appointment)->with('status','Agendamento criado.');
    }
}
