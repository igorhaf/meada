<?php

namespace App\Http\Controllers\Admin;

use App\Http\Controllers\Controller;
use App\Models\Event;
use App\Models\EventRegistration;
use App\Models\Room;
use App\Models\User;
use App\Services\AuditService;
use App\Services\CalendarConflictService;
use App\Services\EventService;
use App\Services\EventCancellationService;
use Carbon\CarbonImmutable;
use Illuminate\Http\RedirectResponse;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\Storage;
use Illuminate\Support\Str;
use Illuminate\Validation\ValidationException;
use Illuminate\View\View;

class EventController extends Controller
{
    public function index(): View
    {
        $events = Event::with('instructors')->withCount('activeRegistrations as taken')->latest('starts_at')->paginate(20);
        return view('admin.events.index', compact('events'));
    }

    public function create(): View { return $this->form(new Event(['status'=>'draft','type'=>'roda','modality'=>'presencial','reminder_hours'=>24,'house_percentage'=>0])); }
    public function edit(Event $event): View { $event->load('instructors','sessions'); return $this->form($event); }

    public function store(Request $request, CalendarConflictService $calendar, AuditService $audit): RedirectResponse
    {
        [$eventData, $sessions, $instructors] = $this->validated($request);
        $this->ensureNoConflicts($request, $calendar, $sessions, $instructors);
        $event = DB::transaction(function () use ($request, $eventData, $sessions, $instructors) {
            $event = Event::create($eventData + ['professional_id'=>$instructors[0], 'created_by'=>$request->user()->id, 'slug'=>$this->uniqueSlug($eventData['title'])]);
            $this->syncEvent($event, $sessions, $instructors, $request);
            return $event;
        });
        $audit->record('event.created', $event, [], $event->toArray(), $request->input('override_reason'));
        return redirect()->route('admin.events.edit',$event)->with('status','Evento criado.');
    }

    public function update(Request $request, Event $event, CalendarConflictService $calendar, AuditService $audit, EventCancellationService $cancellation): RedirectResponse
    {
        [$eventData, $sessions, $instructors] = $this->validated($request);
        $this->ensureNoConflicts($request, $calendar, $sessions, $instructors, $event->id);
        $before=$event->toArray();
        DB::transaction(function () use ($request,$event,$eventData,$sessions,$instructors) {
            $event->update($eventData+['professional_id'=>$instructors[0]]);
            $event->sessions()->delete();
            $this->syncEvent($event,$sessions,$instructors,$request);
        });
        $manual=[];if($eventData['status']==='cancelled')$manual=$cancellation->cancel($event);
        $audit->record('event.updated',$event,$before,$event->fresh()->toArray(),$request->input('override_reason'));
        return back()->with('status',$manual?'Evento cancelado. Estornos manuais pendentes: '.implode(', ',$manual):'Evento atualizado.');
    }

    public function destroy(Event $event, AuditService $audit): RedirectResponse
    {
        abort_if($event->registrations()->exists(),422,'Eventos com inscrições não podem ser excluídos; cancele o evento.');
        $audit->record('event.deleted',$event,$event->toArray()); $event->delete();
        return redirect()->route('admin.events.index')->with('status','Evento removido.');
    }

    public function registrations(Event $event): View
    {
        $registrations=$event->registrations()->with('customer','accessPasses')->latest()->paginate(30);
        $customers=User::where('role','customer')->orderBy('name')->get(['id','name','email']);
        return view('admin.events.registrations',compact('event','registrations','customers'));
    }

    public function addRegistration(Request $request, Event $event, EventService $events): RedirectResponse
    {
        $data=$request->validate(['customer_id'=>['required','exists:users,id'],'participant_name'=>['required','string','max:255'],'participant_phone'=>['nullable','string','max:40'],'mark_paid'=>['nullable','boolean'],'privacy_consent'=>['accepted']]);
        $customer=User::findOrFail($data['customer_id']);
        $registration=$events->register($event,['name'=>$data['participant_name'],'email'=>$customer->email,'phone'=>$data['participant_phone']??null,'consent'=>true],$customer);
        if($request->boolean('mark_paid')&&!$registration->isPaid()) app(\App\Services\TransactionService::class)->apply($registration,'approved',null,'manual');
        return back()->with('status','Participante matriculado.');
    }

    private function form(Event $event): View
    {
        return view('admin.events.form',['event'=>$event,'professionals'=>User::where('is_professional',true)->where('role','!=','root')->where('is_active',true)->orderBy('professional_name')->get(),'rooms'=>Room::active()->orderBy('position')->get()]);
    }

    /** @return array{0:array,1:array,2:array} */
    private function validated(Request $request): array
    {
        $data=$request->validate([
            'title'=>['required','string','max:255'],'description'=>['nullable','string'],'type'=>['required','in:roda,curso,certificacao'],'modality'=>['required','in:presencial,online'],'location_label'=>['nullable','string','max:255'],'room_id'=>['nullable','exists:rooms,id'],'capacity'=>['nullable','integer','min:0'],'price'=>['nullable','numeric','min:0'],'is_free'=>['nullable','boolean'],'allow_discount'=>['nullable','boolean'],'discount_percent'=>['nullable','numeric','min:0','max:100'],'house_percentage'=>['nullable','numeric','min:0','max:100'],'status'=>['required','in:draft,published,cancelled'],'reminder_hours'=>['nullable','integer','min:0','max:168'],'cover'=>['nullable','image','max:8192'],'instructors'=>['required','array','min:1'],'instructors.*'=>['integer','exists:users,id'],'sessions'=>['required','array','min:1'],'sessions.*.title'=>['nullable','string','max:255'],'sessions.*.starts_at'=>['required','date'],'sessions.*.ends_at'=>['required','date','after:sessions.*.starts_at'],'sessions.*.room_id'=>['nullable','exists:rooms,id'],'sessions.*.location_label'=>['nullable','string','max:255'],'sessions.*.meeting_link'=>['nullable','url','max:500'],'override_reason'=>['nullable','string','max:1000'],
        ]);
        $tz=config('pindorama.timezone'); $sessions=[];
        foreach($data['sessions'] as $session) {$sessions[]=array_merge($session,['room_id'=>$session['room_id']??$data['room_id']??null,'location_label'=>$session['location_label']??$data['location_label']??null,'starts_at'=>CarbonImmutable::parse($session['starts_at'],$tz)->utc(),'ends_at'=>CarbonImmutable::parse($session['ends_at'],$tz)->utc(),'timezone'=>$tz,'modality'=>$data['modality'],'status'=>'scheduled']);}
        usort($sessions,fn($a,$b)=>$a['starts_at']<=>$b['starts_at']); $instructors=array_values(array_unique(array_map('intval',$data['instructors'])));
        unset($data['sessions'],$data['instructors'],$data['cover'],$data['override_reason']);
        $data['is_free']=$request->boolean('is_free');$data['allow_discount']=$request->boolean('allow_discount');$data['price']=$data['is_free']?0:(float)($data['price']??0);$data['capacity']=(int)($data['capacity']??0);$data['discount_percent']=(float)($data['discount_percent']??0);$data['house_percentage']=(float)($data['house_percentage']??0);$data['reminder_hours']=(int)($data['reminder_hours']??24);$data['starts_at']=$sessions[0]['starts_at'];$data['ends_at']=$sessions[array_key_last($sessions)]['ends_at'];$data['timezone']=$tz;
        return [$data,$sessions,$instructors];
    }

    private function ensureNoConflicts(Request $request, CalendarConflictService $calendar,array $sessions,array $instructors,?int $ignore=null): void
    {
        $conflicts=[];foreach($sessions as $session)$conflicts=array_merge($conflicts,$calendar->conflicts($instructors,$session['room_id']??null,$session['starts_at'],$session['ends_at'],$ignore));
        if($conflicts&&!($request->boolean('force_conflict')&&filled($request->input('override_reason'))))throw ValidationException::withMessages(['sessions'=>array_values(array_unique($conflicts))]);
    }

    private function syncEvent(Event $event,array $sessions,array $instructors,Request $request): void
    {
        $pivot=[];$count=count($instructors);foreach($instructors as $i=>$id)$pivot[$id]=['role'=>$i===0?'lead':'instructor','can_view_financials'=>true,'can_manage_attendance'=>true,'revenue_percentage'=>round(100/$count,2),'position'=>$i];$event->instructors()->sync($pivot);
        foreach($sessions as $session){$model=$event->sessions()->create($session);$model->professionals()->sync($instructors);}
        if($request->hasFile('cover')){$old=$event->cover_path;if($old&&str_starts_with($old,'/storage/'))Storage::disk('public')->delete(str_replace('/storage/','',$old));$event->update(['cover_path'=>'/storage/'.$request->file('cover')->store('events/covers','public')]);}
    }

    private function uniqueSlug(string $title): string {$base=Str::slug($title)?:'evento';$slug=$base;$i=2;while(Event::where('slug',$slug)->exists())$slug=$base.'-'.$i++;return $slug;}
}
