<?php

namespace App\Http\Controllers;

use App\Exceptions\EventFullException;
use App\Models\Event;
use App\Models\EventRegistration;
use App\Services\EventService;
use Illuminate\Http\RedirectResponse;
use Illuminate\Http\Request;
use Illuminate\View\View;

class EventController extends Controller
{
    public function index(): View
    {
        $events = Event::published()->upcoming()->with('professional')
            ->withCount(['activeRegistrations as taken'])
            ->orderBy('starts_at')
            ->paginate(12);

        return view('events.index', compact('events'));
    }

    public function show(Event $event): View
    {
        abort_unless($event->status === 'published' || auth()->user()?->isRoot() || $event->professional_id === auth()->id(), 404);
        $event->load('professional');

        return view('events.show', compact('event'));
    }

    public function register(Request $request, Event $event, EventService $events): RedirectResponse
    {
        $data = $request->validate([
            'participant_name' => ['required', 'string', 'max:255'],
            'participant_phone' => ['nullable', 'string', 'max:40'],
        ]);

        try {
            $registration = $events->register($event, [
                'name' => $data['participant_name'],
                'phone' => $data['participant_phone'] ?? null,
            ], $request->user());
        } catch (EventFullException $e) {
            return back()->with('error', $e->getMessage());
        } catch (\RuntimeException $e) {
            return back()->with('error', $e->getMessage());
        }

        return redirect()->route('events.registration', $registration)
            ->with('status', $registration->isPaid() ? 'Inscrição confirmada!' : 'Inscrição realizada — conclua o pagamento.');
    }

    public function registration(Request $request, EventRegistration $registration): View
    {
        $this->authorizeOwner($request, $registration);
        $registration->load('event');

        return view('events.registration', compact('registration'));
    }

    public function pay(Request $request, EventRegistration $registration): RedirectResponse
    {
        $this->authorizeOwner($request, $registration);

        if (! $registration->isPaid()) {
            $registration->update([
                'status' => 'confirmed',
                'payment_status' => 'approved',
                'payment_method' => config('services.mercadopago.enabled') ? 'mercadopago' : 'simulado',
                'paid_at' => now(),
            ]);
        }

        return redirect()->route('events.registration', $registration)->with('status', 'Pagamento confirmado!');
    }

    private function authorizeOwner(Request $request, EventRegistration $registration): void
    {
        abort_unless($registration->customer_id === $request->user()->id || $request->user()->isRoot(), 403);
    }
}
