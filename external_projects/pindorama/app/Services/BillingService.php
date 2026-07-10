<?php

namespace App\Services;

use App\Models\PlatformCharge;
use App\Models\User;
use Carbon\CarbonImmutable;
use Illuminate\Support\Str;

/**
 * Cobrança da plataforma ao terapeuta (Epic B). Aplica desconto e gratuidade
 * configurados por tenant. A mensalidade é única por mês (idempotente).
 */
class BillingService
{
    /**
     * Gera (ou retorna a existente) a mensalidade de um mês para o profissional.
     * Gratuidade → cobrança 'waived' com valor 0. Desconto → reduz o valor.
     */
    public function generateMonthly(User $professional, ?string $month = null): PlatformCharge
    {
        $month ??= CarbonImmutable::now($professional->timezone ?: config('pindorama.timezone'))->format('Y-m');

        $existing = PlatformCharge::where('professional_id', $professional->id)
            ->where('type', 'subscription')->where('reference_month', $month)->first();
        if ($existing) {
            return $existing;
        }

        $base = (float) $professional->billing_monthly_fee;

        return $this->create($professional, 'subscription', "Mensalidade {$month}", $base, [
            'reference_month' => $month,
            'due_date' => $this->dueDate($professional, $month),
        ]);
    }

    /** Cobrança avulsa (cadastro / destaque). */
    public function createCharge(User $professional, string $type, string $description, float $baseAmount): PlatformCharge
    {
        return $this->create($professional, $type, $description, $baseAmount, []);
    }

    /**
     * @param  array<string,mixed>  $extra
     */
    private function create(User $professional, string $type, string $description, float $base, array $extra): PlatformCharge
    {
        $free = (bool) $professional->billing_free;
        $discountPercent = (float) $professional->billing_discount_percent;

        $discount = $free ? $base : round($base * $discountPercent / 100, 2);
        $amount = max(0.0, round($base - $discount, 2));
        $status = ($free || $amount == 0.0) ? 'waived' : 'pending';

        return PlatformCharge::create(array_merge([
            'reference' => 'CHG-' . strtoupper(Str::random(8)),
            'professional_id' => $professional->id,
            'type' => $type,
            'description' => $description,
            'base_amount' => $base,
            'discount_amount' => $discount,
            'amount' => $amount,
            'status' => $status,
            'paid_at' => $status === 'waived' ? now() : null,
        ], $extra));
    }

    private function dueDate(User $professional, string $month): string
    {
        $day = max(1, min(28, (int) ($professional->billing_day ?: 5)));

        return CarbonImmutable::parse($month . '-01')->day($day)->format('Y-m-d');
    }
}
