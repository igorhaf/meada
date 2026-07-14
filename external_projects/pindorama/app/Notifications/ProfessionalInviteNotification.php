<?php

namespace App\Notifications;

use Illuminate\Bus\Queueable;
use Illuminate\Notifications\Messages\MailMessage;
use Illuminate\Notifications\Notification;

class ProfessionalInviteNotification extends Notification
{
    use Queueable;
    public function __construct(private string $url) {}
    public function via(object $notifiable): array { return ['mail']; }
    public function toMail(object $notifiable): MailMessage
    {
        return (new MailMessage)->subject('Seu acesso profissional ao Pindorama')
            ->greeting('Olá, '.$notifiable->name.'!')
            ->line('Seu perfil profissional foi criado pela equipe Pindorama.')
            ->action('Definir minha senha', $this->url)
            ->line('Este convite expira em 72 horas.');
    }
}
