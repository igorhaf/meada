<?php

namespace App\Services;

use App\Models\Appointment;
use RuntimeException;

class AppointmentCancellationService
{
    public function __construct(private BookingService $booking, private MercadoPagoService $mp, private TransactionService $transactions, private AccessPassService $passes, private NotificationService $notifications) {}
    public function cancel(Appointment $appointment,string $by): bool
    {
        $manualRefund=false;
        if($appointment->isPaid()){
            if($appointment->mp_payment_id&&$this->mp->enabled()){
                try{$this->mp->refund($appointment->mp_payment_id,'refund-'.$appointment->reference);$this->transactions->apply($appointment,'refunded',$appointment->mp_payment_id,$appointment->payment_method);}catch(\Throwable $e){report($e);throw new RuntimeException('Não foi possível confirmar o estorno; o agendamento não foi cancelado.');}
            }elseif($appointment->payment_method==='simulado')$this->transactions->apply($appointment,'refunded',null,'simulado');
            else{$manualRefund=true;$transaction=$this->transactions->for($appointment);$transaction->update(['metadata'=>array_merge($transaction->metadata??[],['refund_required'=>true])]);}
        }
        $this->booking->cancel($appointment,$by);$this->passes->cancel($appointment);$this->notifications->appointmentChanged($appointment->fresh(),'Seu agendamento foi cancelado.');
        return $manualRefund;
    }
}
