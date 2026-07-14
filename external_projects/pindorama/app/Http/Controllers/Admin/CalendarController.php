<?php

namespace App\Http\Controllers\Admin;

use App\Http\Controllers\Controller;
use App\Models\Appointment;
use App\Models\AvailabilityBlock;
use App\Models\EventSession;
use App\Models\Room;
use App\Models\User;
use Carbon\CarbonImmutable;
use Illuminate\Http\Request;
use Illuminate\View\View;

class CalendarController extends Controller
{
    public function index(Request $request): View
    {
        $tz=config('pindorama.timezone'); $anchor=CarbonImmutable::parse($request->query('date','today'),$tz); $view=$request->query('view','week');
        $from=$view==='month'?$anchor->startOfMonth()->startOfWeek():$anchor->startOfWeek(); $to=$view==='month'?$anchor->endOfMonth()->endOfWeek():$from->endOfWeek();
        $professionalId=$request->integer('professional_id')?:null; $roomId=$request->integer('room_id')?:null; $type=$request->query('type'); $items=collect();
        if(!$type||$type==='appointment') Appointment::with('professional','location.room')->when($professionalId,fn($q)=>$q->where('professional_id',$professionalId))->when($roomId,fn($q)=>$q->whereHas('location',fn($l)=>$l->where('room_id',$roomId)))->where('start_at','<',$to->endOfDay()->utc())->where('end_at','>',$from->startOfDay()->utc())->get()->each(fn($a)=>$items->push(['type'=>'appointment','start'=>$a->start_at,'end'=>$a->end_at,'title'=>$a->service_title,'subtitle'=>$a->professional?->display_name.' · '.$a->patient_name,'location'=>$a->location?->room?->name?:$a->location_label,'status'=>$a->status]));
        if(!$type||$type==='event') EventSession::with('event','professionals','room')->when($professionalId,fn($q)=>$q->whereHas('professionals',fn($p)=>$p->whereKey($professionalId)))->when($roomId,fn($q)=>$q->where('room_id',$roomId))->where('starts_at','<',$to->endOfDay()->utc())->where('ends_at','>',$from->startOfDay()->utc())->get()->each(fn($s)=>$items->push(['type'=>'event','start'=>$s->starts_at,'end'=>$s->ends_at,'title'=>$s->event->title,'subtitle'=>$s->professionals->pluck('display_name')->join(', '),'location'=>$s->room?->name?:$s->location_label,'status'=>$s->status]));
        if(!$type||$type==='block') AvailabilityBlock::with('professional','location')->when($professionalId,fn($q)=>$q->where('professional_id',$professionalId))->when($roomId,fn($q)=>$q->whereHas('location',fn($l)=>$l->where('room_id',$roomId)))->where('starts_at','<',$to->endOfDay()->utc())->where('ends_at','>',$from->startOfDay()->utc())->get()->each(fn($b)=>$items->push(['type'=>'block','start'=>$b->starts_at,'end'=>$b->ends_at,'title'=>'Bloqueio','subtitle'=>$b->professional?->display_name,'location'=>$b->location?->name,'status'=>'blocked']));
        $items=$items->sortBy('start')->groupBy(fn($item)=>$item['start']->setTimezone($tz)->format('Y-m-d'));
        return view('admin.calendar',compact('items','from','to','anchor','view','tz')+['professionals'=>User::where('is_professional',true)->where('role','!=','root')->orderBy('professional_name')->get(),'rooms'=>Room::active()->orderBy('position')->get()]);
    }
}
