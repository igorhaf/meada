<?php

namespace App\Http\Controllers;

use App\Models\Appointment;
use App\Models\EventRegistration;
use App\Services\MercadoPagoService;
use App\Services\TransactionService;
use Illuminate\Contracts\View\View;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\RedirectResponse;
use Illuminate\Http\Request;

class PaymentController extends Controller
{
    public function __construct(private TransactionService $transactions) {}

    /** Página de pagamento (Checkout Transparente — Payment Brick do MP). */
    public function show(Appointment $appointment, MercadoPagoService $mp): View|RedirectResponse
    {
        $this->authorizeOwner($appointment);

        if ($appointment->isPaid()) {
            return redirect()->route('appointments.show', $appointment);
        }

        if (! $mp->enabled()) {
            $this->transactions->apply($appointment, 'approved', null, 'simulado');

            return redirect()->route('appointments.show', $appointment)->with('status', 'Pagamento simulado aprovado (MP desligado).');
        }

        $this->transactions->prepareAttempt($appointment);

        return view('payment.show', ['appointment' => $appointment, 'publicKey' => $mp->publicKey()]);
    }

    /** Recebe a formData do Brick e cria o pagamento via API de Pagamentos. */
    public function process(Request $request, Appointment $appointment, MercadoPagoService $mp): JsonResponse
    {
        $this->authorizeOwner($appointment);

        if ($appointment->isPaid()) {
            return response()->json(['status' => $appointment->payment_status, 'redirect' => route('appointments.show', $appointment)]);
        }

        try {
            $transaction = $this->transactions->for($appointment);
            $payment = $mp->createPayment($appointment, $request->all(), $transaction->idempotency_key);
        } catch (\Throwable $e) {
            report($e);

            return response()->json(['error' => 'Não foi possível processar o pagamento. Tente outro cartão.'], 422);
        }

        $status = (string) ($payment['status'] ?? 'rejected');
        $this->transactions->apply($appointment, $status, (string) ($payment['id'] ?? ''), data_get($payment, 'payment_method_id'));

        $pix = data_get($payment, 'point_of_interaction.transaction_data');

        return response()->json([
            'status' => $status,
            'detail' => $payment['status_detail'] ?? null,
            'redirect' => route('appointments.show', $appointment),
            'qr_code' => $pix['qr_code'] ?? null,
            'qr_code_base64' => $pix['qr_code_base64'] ?? null,
            'ticket_url' => $pix['ticket_url'] ?? data_get($payment, 'transaction_details.external_resource_url'),
        ]);
    }

    /** "Pagar novamente" para um agendamento pendente → volta ao Brick. */
    public function retry(Appointment $appointment, MercadoPagoService $mp): RedirectResponse
    {
        $this->authorizeOwner($appointment);

        if ($appointment->isPaid()) {
            return redirect()->route('appointments.show', $appointment);
        }

        if (! $mp->enabled()) {
            $this->transactions->apply($appointment, 'approved', null, 'simulado');

            return redirect()->route('appointments.show', $appointment)->with('status', 'Pagamento simulado aprovado (MP desligado).');
        }

        $this->transactions->prepareAttempt($appointment);
        return redirect()->route('payment.show', $appointment);
    }

    /**
     * Notificação servidor-a-servidor do Mercado Pago. Nunca confiamos no corpo:
     * buscamos o pagamento por id na API e atualizamos o agendamento a partir disso.
     */
    public function webhook(Request $request, MercadoPagoService $mp): JsonResponse
    {
        if (! $mp->validateWebhook($request)) return response()->json(['error' => 'invalid_signature'], 401);
        $type = $request->input('type', $request->query('topic'));
        $paymentId = $mp->webhookDataId($request) ?: $request->query('id');

        if ($type === 'payment' && $paymentId) {
            $payment = $mp->getPayment((string) $paymentId);

            if ($payment && ($reference = $payment['external_reference'] ?? null)) {
                $payable = Appointment::where('reference', $reference)->first()
                    ?: EventRegistration::where('reference', $reference)->first();
                if ($payable) {
                    $this->transactions->apply($payable, (string) $payment['status'], (string) $payment['id'], data_get($payment, 'payment_method_id'));
                }
            }
        }

        return response()->json(['received' => true]);
    }

    private function authorizeOwner(Appointment $appointment): void
    {
        abort_unless($appointment->customer_id === auth()->id() || auth()->user()->isRoot(), 403);
    }
}
