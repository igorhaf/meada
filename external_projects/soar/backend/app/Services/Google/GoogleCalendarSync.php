<?php

namespace App\Services\Google;

use App\Models\CalendarEvent;
use App\Models\Page;
use App\Models\User;
use Illuminate\Support\Facades\Http;
use Illuminate\Support\Facades\Log;
use Throwable;

/**
 * Espelha eventos do soar no Google Calendar (push soar → Google, best-effort).
 * Página compartilhada → calendários de TODOS os usuários com google_calendar_id
 * (Igor E Aline); página pessoal → só o calendário do dono.
 */
class GoogleCalendarSync
{
    private const API = 'https://www.googleapis.com/calendar/v3';

    public function __construct(private readonly GoogleServiceAccount $auth)
    {
    }

    public function syncEvent(CalendarEvent $event): void
    {
        if (! $this->auth->isConfigured()) {
            return;
        }

        try {
            $targets = $this->targetCalendars($event->page);
            $ids = $event->google_event_ids ?? [];

            foreach ($targets as $calendarId) {
                $body = $this->eventBody($event);
                if (isset($ids[$calendarId])) {
                    Http::withToken($this->auth->accessToken())
                        ->patch(self::API."/calendars/{$calendarId}/events/{$ids[$calendarId]}", $body);
                } else {
                    $response = Http::withToken($this->auth->accessToken())
                        ->post(self::API."/calendars/{$calendarId}/events", $body);
                    if ($response->successful()) {
                        $ids[$calendarId] = $response->json('id');
                    } else {
                        Log::warning('Google Calendar insert falhou', [
                            'calendar' => $calendarId, 'status' => $response->status(),
                        ]);
                    }
                }
            }

            $event->forceFill(['google_event_ids' => $ids])->saveQuietly();
        } catch (Throwable $e) {
            Log::warning('Sync Google Calendar falhou (best-effort)', ['error' => $e->getMessage()]);
        }
    }

    public function deleteEvent(CalendarEvent $event): void
    {
        if (! $this->auth->isConfigured() || empty($event->google_event_ids)) {
            return;
        }

        foreach ($event->google_event_ids as $calendarId => $eventId) {
            try {
                Http::withToken($this->auth->accessToken())
                    ->delete(self::API."/calendars/{$calendarId}/events/{$eventId}");
            } catch (Throwable $e) {
                Log::warning('Delete no Google Calendar falhou (best-effort)', ['error' => $e->getMessage()]);
            }
        }
    }

    /** @return array<int, string> */
    private function targetCalendars(Page $page): array
    {
        if ($page->scope === Page::SCOPE_PERSONAL) {
            $calendarId = $page->owner?->google_calendar_id;

            return $calendarId ? [$calendarId] : [];
        }

        return User::whereNotNull('google_calendar_id')->pluck('google_calendar_id')->all();
    }

    /** @return array<string, mixed> */
    private function eventBody(CalendarEvent $event): array
    {
        $tz = config('app.timezone');

        if ($event->all_day) {
            $start = ['date' => $event->starts_at->toDateString()];
            $end = ['date' => ($event->ends_at ?? $event->starts_at)->addDay()->toDateString()];
        } else {
            $start = ['dateTime' => $event->starts_at->toIso8601String(), 'timeZone' => $tz];
            $end = [
                'dateTime' => ($event->ends_at ?? $event->starts_at->copy()->addHour())->toIso8601String(),
                'timeZone' => $tz,
            ];
        }

        return [
            'summary' => $event->title,
            'description' => $event->notes ?? '',
            'start' => $start,
            'end' => $end,
        ];
    }
}
