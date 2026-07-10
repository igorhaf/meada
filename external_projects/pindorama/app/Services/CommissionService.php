<?php

namespace App\Services;

use App\Models\Appointment;
use App\Models\CommissionRule;

/**
 * Resolves the platform's cut ("aluguel"/comissão) on a paid appointment by
 * PRECEDENCE — most specific rule wins:
 *   room > professional > service > service_category > default.
 * The money all lands in the single platform MP account; commission_amount and
 * professional_amount are snapshots (for reporting / manual payout).
 */
class CommissionService
{
    /**
     * @return array{commission: float, professional: float, rule: ?CommissionRule}
     */
    public function compute(Appointment $appointment): array
    {
        $total = (float) $appointment->total;
        $rule = $this->resolveRule($appointment);

        if (! $rule) {
            return ['commission' => 0.0, 'professional' => $total, 'rule' => null];
        }

        $commission = $rule->rate_type === 'fixed'
            ? min((float) $rule->rate_value, $total)
            : round($total * ((float) $rule->rate_value) / 100, 2);

        $commission = max(0.0, min($commission, $total));

        return [
            'commission' => $commission,
            'professional' => round($total - $commission, 2),
            'rule' => $rule,
        ];
    }

    /** Compute + persist the split on the appointment. */
    public function apply(Appointment $appointment): void
    {
        $result = $this->compute($appointment);
        $appointment->forceFill([
            'commission_amount' => $result['commission'],
            'professional_amount' => $result['professional'],
        ])->save();
    }

    private function resolveRule(Appointment $appointment): ?CommissionRule
    {
        $appointment->loadMissing('location', 'service');

        $candidates = [
            ['room', $appointment->location?->room_id],
            ['professional', $appointment->professional_id],
            ['service', $appointment->service_id],
            ['service_category', $appointment->service?->service_category_id],
            ['default', null],
        ];

        foreach ($candidates as [$type, $id]) {
            if ($type !== 'default' && ! $id) {
                continue;
            }
            $rule = CommissionRule::where('is_active', true)
                ->where('scope_type', $type)
                ->when($type === 'default', fn ($q) => $q->whereNull('scope_id'), fn ($q) => $q->where('scope_id', $id))
                ->first();
            if ($rule) {
                return $rule;
            }
        }

        return null;
    }
}
