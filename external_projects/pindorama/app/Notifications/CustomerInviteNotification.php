<?php
namespace App\Notifications;
use Illuminate\Bus\Queueable;
use Illuminate\Notifications\Messages\MailMessage;
use Illuminate\Notifications\Notification;
class CustomerInviteNotification extends Notification
{
    use Queueable;public function __construct(private string $url){}public function via(object $n):array{return ['mail'];}public function toMail(object $n):MailMessage{return(new MailMessage)->subject('Seu acesso ao Pindorama')->greeting('Olá, '.$n->name.'!')->line('A equipe cadastrou você para um atendimento ou evento no Pindorama.')->action('Definir minha senha',$this->url)->line('Este convite expira em 72 horas.');}
}
