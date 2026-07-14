<?php

namespace App\Http\Controllers;

use App\Exceptions\EventFullException;
use App\Models\Event;
use App\Models\EventRegistration;
use App\Services\EventService;
use App\Services\MercadoPagoService;
use App\Services\TransactionService;
use App\Services\AccessPassService;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\RedirectResponse;
use Illuminate\Http\Request;
use Illuminate\View\View;

class EventController extends Controller
{
    public function index(): View
    {
        $events = Event::published()->upcoming()->with('instructors')
            ->withCount(['activeRegistrations as taken'])
            ->orderBy('starts_at')
            ->paginate(12);

        return view('events.index', compact('events'));
    }

    public function show(Event $event): View
    {
        abort_unless($event->status === 'published' || auth()->user()?->isRoot() || $event->professional_id === auth()->id(), 404);
        $event->load('instructors', 'sessions.room');

        return view('events.show', compact('event'));
    }

    public function register(Request $request, Event $event, EventService $events): RedirectResponse
    {
        $data = $request->validate([
            'participant_name' => ['required', 'string', 'max:255'],
            'participant_phone' => ['nullable', 'string', 'max:40'],
            'privacy_consent' => ['accepted'],
        ]);

        try {
            $registration = $events->register($event, [
                'name' => $data['participant_name'],
                'phone' => $data['participant_phone'] ?? null,
                'consent' => true,
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

    public function myRegistrations(Request $request): View
    {
        $registrations = $request->user()->eventRegistrations()->with('event.instructors')->latest()->paginate(15);

        return view('events.my-registrations', compact('registrations'));
    }

    public function pay(Request $request, EventRegistration $registration, MercadoPagoService $mp, TransactionService $transactions): View|RedirectResponse
    {
        $this->authorizeOwner($request, $registration);

        if ($registration->isPaid()) return redirect()->route('events.registration', $registration);
        if (! $mp->enabled()) {
            $transactions->apply($registration, 'approved', null, 'simulado');
            return redirect()->route('events.registration', $registration)->with('status', 'Pagamento simulado aprovado (MP desligado).');
        }

        $transactions->prepareAttempt($registration);
        $registration->load('event');
        return view('events.payment', ['registration' => $registration, 'publicKey' => $mp->publicKey()]);
    }

    public function processPayment(Request $request, EventRegistration $registration, MercadoPagoService $mp, TransactionService $transactions): JsonResponse
    {
        $this->authorizeOwner($request, $registration);
        if ($registration->isPaid()) return response()->json(['status' => $registration->payment_status, 'redirect' => route('events.registration', $registration)]);

        try {
            $transaction = $transactions->for($registration);
            $payment = $mp->createPayment($registration, $request->all(), $transaction->idempotency_key);
        } catch (\Throwable $e) {
            report($e);
            return response()->json(['error' => 'Não foi possível processar o pagamento. Confira os dados e tente novamente.'], 422);
        }

        $status = (string) ($payment['status'] ?? 'rejected');
        $transactions->apply($registration, $status, (string) ($payment['id'] ?? ''), data_get($payment, 'payment_method_id'));
        $pix = data_get($payment, 'point_of_interaction.transaction_data');

        return response()->json([
            'status' => $status, 'detail' => $payment['status_detail'] ?? null,
            'redirect' => route('events.registration', $registration),
            'qr_code' => $pix['qr_code'] ?? null, 'qr_code_base64' => $pix['qr_code_base64'] ?? null,
            'ticket_url' => $pix['ticket_url'] ?? data_get($payment, 'transaction_details.external_resource_url'),
        ]);
    }

    public function cancel(Request $request, EventRegistration $registration, MercadoPagoService $mp, TransactionService $transactions, AccessPassService $passes): RedirectResponse
    {
        $this->authorizeOwner($request, $registration);
        abort_if($registration->status === 'attended', 422, 'Uma presença já registrada não pode ser cancelada.');

        if ($registration->isPaid() && $registration->mp_payment_id && $mp->enabled()) {
            try {
                $mp->refund($registration->mp_payment_id, 'refund-' . $registration->reference);
                $transactions->apply($registration, 'refunded', $registration->mp_payment_id, $registration->payment_method);
            } catch (\Throwable $e) {
                report($e);
                return back()->with('error', 'Não foi possível confirmar o estorno. A inscrição não foi cancelada; contate o atendimento.');
            }
        }

        $registration->update(['status' => 'cancelled', 'cancelled_at' => now()]);
        $passes->cancel($registration);

        return back()->with('status', 'Inscrição cancelada' . ($registration->payment_status === 'refunded' ? ' e pagamento estornado.' : '.'));
    }

    private function authorizeOwner(Request $request, EventRegistration $registration): void
    {
        abort_unless($registration->customer_id === $request->user()->id || $request->user()->isRoot(), 403);
    }
}
