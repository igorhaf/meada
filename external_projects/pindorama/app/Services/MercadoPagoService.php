<?php

namespace App\Services;

use App\Models\Appointment;
use App\Models\EventRegistration;
use Illuminate\Support\Facades\Http;
use Illuminate\Support\Str;
use Illuminate\Http\Request;

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

    public function validateWebhook(Request $request): bool
    {
        $secret = (string) config('services.mercadopago.webhook_secret');
        if ($secret === '') return ! $this->enabled();

        $parts = [];
        foreach (explode(',', (string) $request->header('x-signature')) as $part) {
            [$key, $value] = array_pad(explode('=', trim($part), 2), 2, null);
            if ($key && $value) $parts[$key] = $value;
        }
        $ts = $parts['ts'] ?? null; $signature = $parts['v1'] ?? null;
        if (! $ts || ! $signature) return false;

        $timestamp = (int) $ts;
        if ($timestamp > 9999999999) $timestamp = (int) floor($timestamp / 1000);
        if (abs(now()->timestamp - $timestamp) > (int) config('services.mercadopago.webhook_tolerance', 300)) return false;

        $dataId = $this->webhookDataId($request);
        if (is_string($dataId) && ! ctype_digit($dataId)) $dataId = strtolower($dataId);
        $manifest = '';
        if (filled($dataId)) $manifest .= 'id:'.$dataId.';';
        if ($requestId = $request->header('x-request-id')) $manifest .= 'request-id:'.$requestId.';';
        $manifest .= 'ts:'.$ts.';';

        return hash_equals(hash_hmac('sha256', $manifest, $secret), $signature);
    }

    public function webhookDataId(Request $request): ?string
    {
        $raw = (string) $request->server('QUERY_STRING');
        if (preg_match('/(?:^|&)data\.id=([^&]*)/', $raw, $match)) return urldecode($match[1]);
        return $request->query('data.id') ?: $request->query('data_id') ?: $request->input('data.id');
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
    public function createPayment(Appointment|EventRegistration $payable, array $data, string $idempotencyKey): array
    {
        $base = $this->baseUrl();
        $amount = $payable instanceof Appointment ? (float) $payable->total : (float) $payable->amount;
        $kind = $payable instanceof Appointment ? 'Agendamento' : 'Inscrição';
        $email = $payable instanceof Appointment ? $payable->patient_email : $payable->participant_email;

        $payload = array_merge($data, [
            'transaction_amount' => $amount,
            'description' => $kind . ' ' . $payable->reference,
            'external_reference' => $payable->reference,
            'statement_descriptor' => 'PINDORAMA',
            'metadata' => [$payable instanceof Appointment ? 'appointment_id' : 'event_registration_id' => $payable->id],
            'payer' => array_merge((array) ($data['payer'] ?? []), ['email' => $email]),
        ]);

        if ($this->hasPublicBase()) {
            $payload['notification_url'] = "{$base}/webhooks/mercadopago";
        }

        $response = Http::withToken($this->token())
            ->acceptJson()
            ->withHeaders(['X-Idempotency-Key' => $idempotencyKey])
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

    /** @return array<string,mixed> */
    public function refund(string $paymentId, string $idempotencyKey): array
    {
        $response = Http::withToken($this->token())->acceptJson()
            ->withHeaders(['X-Idempotency-Key' => $idempotencyKey])
            ->post(self::API . "/v1/payments/{$paymentId}/refunds");
        $response->throw();

        return $response->json();
    }
}
