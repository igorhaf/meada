<?php

namespace App\Http\Controllers\Professional;

use App\Http\Controllers\Controller;
use App\Models\Event;
use Illuminate\Http\Request;
use Illuminate\View\View;
use App\Models\User;
use App\Services\EventService;
use Illuminate\Http\RedirectResponse;

class EventController extends Controller
{
    public function index(Request $request): View
    {
        $events = $request->user()->instructedEvents()->withCount('activeRegistrations as taken')->latest('starts_at')->get();
        return view('professional.events.index', compact('events'));
    }

    public function registrations(Request $request, Event $event): View
    {
        $membership = $event->instructors()->whereKey($request->user()->id)->first()?->pivot;
        abort_unless($request->user()->isRoot() || $membership?->can_view_financials || $membership?->can_manage_attendance, 403);
        $registrations = $event->registrations()->with('accessPasses')->latest()->get();
        $canViewFinancials = $request->user()->isRoot() || (bool) $membership?->can_view_financials;
        $canManageAttendance = $request->user()->isRoot() || (bool) $membership?->can_manage_attendance;
        $customers=User::where('role','customer')->where('is_active',true)->orderBy('name')->get(['id','name','email']);
        return view('professional.events.registrations', compact('event', 'registrations', 'canViewFinancials', 'canManageAttendance','customers'));
    }

    public function addRegistration(Request $request,Event $event,EventService $events): RedirectResponse
    {
        $membership=$event->instructors()->whereKey($request->user()->id)->first()?->pivot;abort_unless($membership?->can_manage_attendance,403);
        $data=$request->validate(['customer_id'=>['required','exists:users,id'],'privacy_consent'=>['accepted']]);$customer=User::findOrFail($data['customer_id']);
        $events->register($event,['name'=>$customer->name,'email'=>$customer->email,'phone'=>$customer->phone,'consent'=>true],$customer);
        return back()->with('status','Aluno matriculado. O pagamento pendente aparecerá para o cliente.');
    }
}
