<?php

namespace App\Mail;

use App\Models\EventRegistration;
use Illuminate\Bus\Queueable;
use Illuminate\Mail\Mailable;
use Illuminate\Mail\Mailables\Content;
use Illuminate\Mail\Mailables\Envelope;
use Illuminate\Queue\SerializesModels;

class EventReminderMail extends Mailable
{
    use Queueable, SerializesModels;

    public function __construct(public EventRegistration $registration) {}

    public function envelope(): Envelope
    {
        return new Envelope(subject: 'Lembrete: ' . $this->registration->event->title);
    }

    public function content(): Content
    {
        return new Content(view: 'emails.event-reminder', with: [
            'registration' => $this->registration,
            'event' => $this->registration->event,
        ]);
    }
}
