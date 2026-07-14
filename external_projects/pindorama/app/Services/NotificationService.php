<?php
namespace App\Services;
use App\Models\Appointment;
use App\Models\EventRegistration;
use App\Notifications\OperationalNotification;
use Illuminate\Support\Facades\Notification;
class NotificationService
{
    public function appointmentCreated(Appointment $a): void{$a->loadMissing('professional');$when=$a->start_at->setTimezone($a->timezone)->format('d/m/Y H:i');$this->mail($a->patient_email,'Agendamento recebido',["{$a->service_title} em {$when}.",'Acompanhe a confirmação e o pagamento no site.'],'Ver agendamento',route('appointments.show',$a));$this->mail($a->professional?->email,'Novo agendamento',["{$a->patient_name} solicitou {$a->service_title} para {$when}."],'Abrir agenda',route('professional.appointments.show',$a));}
    public function appointmentChanged(Appointment $a,string $message): void{$this->mail($a->patient_email,'Atualização do agendamento',[$message,$a->service_title.' · '.$a->start_at->setTimezone($a->timezone)->format('d/m/Y H:i')],'Ver agendamento',route('appointments.show',$a));}
    public function appointmentReminder(Appointment $a): void{$this->mail($a->patient_email,'Lembrete de atendimento',[$a->service_title.' será em '.$a->start_at->setTimezone($a->timezone)->format('d/m/Y H:i').'.',$a->location_label],'Ver agendamento',route('appointments.show',$a));}
    public function eventRegistered(EventRegistration $r): void{$r->loadMissing('event');$this->mail($r->participant_email,'Inscrição recebida',[$r->event->title.' · '.$r->event->starts_at->setTimezone($r->event->timezone)->format('d/m/Y H:i'),$r->isPaid()?'Sua inscrição está confirmada.':'Conclua o pagamento para confirmar a vaga.'],'Ver inscrição',route('events.registration',$r));}
    public function paymentApproved(Appointment|EventRegistration $source): void{$email=$source instanceof Appointment?$source->patient_email:$source->participant_email;$url=$source instanceof Appointment?route('appointments.show',$source):route('events.registration',$source);$this->mail($email,'Pagamento aprovado',['Seu pagamento foi aprovado e seu passaporte já está disponível.'],'Abrir comprovante e passaporte',$url);}
    private function mail(?string $email,string $subject,array $lines,?string $label=null,?string $url=null): void{if(!$email)return;try{Notification::route('mail',$email)->notify(new OperationalNotification($subject,$lines,$label,$url));}catch(\Throwable $e){report($e);}}
}
