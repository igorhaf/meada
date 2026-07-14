<?php

namespace App\Services;

use App\Models\Event;

class EventCancellationService
{
    public function __construct(private MercadoPagoService $mp,private TransactionService $transactions,private AccessPassService $passes){}
    /** @return array<int,string> */
    public function cancel(Event $event): array
    {
        $manual=[];$event->load('registrations');
        foreach($event->registrations->whereNotIn('status',['cancelled']) as $registration){
            if($registration->isPaid()){
                if($registration->mp_payment_id&&$this->mp->enabled())try{$this->mp->refund($registration->mp_payment_id,'refund-'.$registration->reference);$this->transactions->apply($registration,'refunded',$registration->mp_payment_id,$registration->payment_method);}catch(\Throwable $e){report($e);$manual[]=$registration->reference;}
                elseif($registration->payment_method==='simulado')$this->transactions->apply($registration,'refunded',null,'simulado');
                else{$manual[]=$registration->reference;$tx=$this->transactions->for($registration);$tx->update(['metadata'=>array_merge($tx->metadata??[],['refund_required'=>true])]);}
            }
            $registration->update(['status'=>'cancelled','cancelled_at'=>now()]);$this->passes->cancel($registration);
        }
        $event->update(['status'=>'cancelled']);$event->sessions()->update(['status'=>'cancelled']);return $manual;
    }
}
