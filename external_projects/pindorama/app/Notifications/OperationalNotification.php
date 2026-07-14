<?php
namespace App\Notifications;
use Illuminate\Bus\Queueable;
use Illuminate\Notifications\Messages\MailMessage;
use Illuminate\Notifications\Notification;
class OperationalNotification extends Notification
{
    use Queueable;
    public function __construct(private string $subject,private array $lines,private ?string $actionLabel=null,private ?string $actionUrl=null){}
    public function via(object $notifiable): array{return ['mail'];}
    public function toMail(object $notifiable): MailMessage{$mail=(new MailMessage)->subject($this->subject)->greeting('Olá!');foreach($this->lines as $line)$mail->line($line);if($this->actionLabel&&$this->actionUrl)$mail->action($this->actionLabel,$this->actionUrl);return $mail->line('Equipe Pindorama');}
}
