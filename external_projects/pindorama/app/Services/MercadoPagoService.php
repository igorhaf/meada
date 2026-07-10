<?php

namespace App\Services;

use App\Models\Appointment;
use Illuminate\Support\Facades\Http;
use Illuminate\Support\Str;

/**
 * Thin wrapper around the Mercado Pago REST API (Checkout Transparente / Payment
 * Brick). Credentials live in config/services.php (fed by the MP_* env vars).
 * Single platform account — the platform collects; commission is an accounting
 * split recorded on the appointment (see CommissionService).
 */
class MercadoPagoService
{
    private const API = 'https://api.mercadopago.com';

    public function enabled(): bool
    {
        return (bool) config('services.mercadopago.enabled')
            && filled($this->token())
            && filled($this->publicKey());
    }

    public function token(): ?string
    {
        return config('services.mercadopago.access_token');
    }

    public function publicKey(): ?string
    {
        return config('services.mercadopago.public_key');
    }

    public function isSandbox(): bool
    {
        return Str::startsWith((string) $this->token(), 'TEST-');
    }

    public function environmentLabel(): string
    {
        return $this->isSandbox() ? 'Teste (sandbox)' : 'Produção';
    }

    private function baseUrl(): string
    {
        return rtrim((string) (config('services.mercadopago.base_url') ?: config('app.url')), '/');
    }

    /** notification_url só é aceita com URL pública (não localhost). */
    private function hasPublicBase(): bool
    {
        return ! Str::contains($this->baseUrl(), ['localhost', '127.0.0.1', '::1']);
    }

    /**
     * Checkout Transparente — create a payment from the Payment Brick formData.
     * Amount and external_reference are set server-side (never trusted from the
     * client). $data is the Brick payload (token, payment_method_id, payer…).
     *
     * @param  array<string,mixed>  $data
     * @return array<string,mixed>
     */
    public function createPayment(Appointment $appointment, array $data): array
    {
        $base = $this->baseUrl();

        $payload = array_merge($data, [
            'transaction_amount' => (float) $appointment->total,
            'description' => 'Agendamento ' . $appointment->reference,
            'external_reference' => $appointment->reference,
            'statement_descriptor' => 'PINDORAMA',
            'metadata' => ['appointment_id' => $appointment->id],
        ]);

        if ($this->hasPublicBase()) {
            $payload['notification_url'] = "{$base}/webhooks/mercadopago";
        }

        $response = Http::withToken($this->token())
            ->acceptJson()
            ->withHeaders(['X-Idempotency-Key' => $appointment->reference . '-' . Str::random(8)])
            ->post(self::API . '/v1/payments', $payload);

        $response->throw();

        return $response->json();
    }

    /**
     * Fetch a payment from MP by id. Source of truth for the appointment status —
     * we re-query MP instead of trusting the webhook body.
     *
     * @return array<string,mixed>|null
     */
    public function getPayment(string $paymentId): ?array
    {
        $response = Http::withToken($this->token())->acceptJson()
            ->get(self::API . "/v1/payments/{$paymentId}");

        return $response->successful() ? $response->json() : null;
    }
}
