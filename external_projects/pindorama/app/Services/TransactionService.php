<?php

namespace App\Services;

use App\Models\Appointment;
use App\Models\EventRegistration;
use App\Models\Transaction;
use Illuminate\Support\Str;

class TransactionService
{
    public function __construct(private CommissionService $commission, private AccessPassService $passes, private NotificationService $notifications) {}

    public function for(Appointment|EventRegistration $payable): Transaction
    {
        $transaction = $payable->transactions()->first();
        if ($transaction) return $transaction;

        if ($payable instanceof Appointment) {
            $professionalId = $payable->professional_id;
            $gross = (float) $payable->total;
            $house = (float) ($payable->commission_amount ?? 0);
            $professional = (float) ($payable->professional_amount ?? $gross);
            $discount = 0;
        } else {
            $payable->loadMissing('event.instructors');
            $professionalId = $payable->event->instructors->first()?->id ?: $payable->event->professional_id;
            $gross = (float) $payable->amount;
            $discount = (float) $payable->discount_amount;
            $house = round($gross * (float) $payable->event->house_percentage / 100, 2);
            $professional = round($gross - $house, 2);
            $payable->update(['house_amount' => $house, 'professional_amount' => $professional]);
        }

        $transaction = $payable->transactions()->create([
            'reference' => 'TX-' . strtoupper(Str::random(12)),
            'customer_id' => $payable->customer_id,
            'professional_id' => $professionalId,
            'gross_amount' => $gross,
            'discount_amount' => $discount,
            'house_amount' => $house,
            'professional_amount' => $professional,
            'provider' => 'mercadopago',
            'status' => $payable->payment_status,
            'idempotency_key' => hash('sha256', $payable->getMorphClass() . ':' . $payable->getKey()),
        ]);
        $professionals = $payable instanceof Appointment
            ? collect([(object) ['id' => $payable->professional_id, 'percentage' => 100]])
            : $payable->event->instructors->map(fn ($instructor) => (object) ['id' => $instructor->id, 'percentage' => (float) ($instructor->pivot->revenue_percentage ?: 0)]);
        if ($professionals->sum('percentage') <= 0) {
            $professionals = $professionals->map(fn ($professional) => (object) ['id' => $professional->id, 'percentage' => round(100 / max(1, $professionals->count()), 2)]);
        }
        $allocated = 0; $count = $professionals->count();
        foreach ($professionals->values() as $index => $split) {
            $amount = $index === $count - 1 ? round($professional - $allocated, 2) : round($professional * $split->percentage / 100, 2);
            $allocated += $amount;
            $transaction->splits()->create(['professional_id' => $split->id, 'percentage' => $split->percentage, 'amount' => $amount]);
        }

        return $transaction;
    }

    public function apply(Appointment|EventRegistration $payable, string $status, ?string $paymentId = null, ?string $method = null): Transaction
    {
        $wasPaid = $payable->isPaid();
        if ($payable instanceof Appointment) {
            $payable->applyPaymentStatus($status, $paymentId);
            if ($method && ! $payable->payment_method) $payable->update(['payment_method' => $method]);
            if ($payable->isPaid() && $payable->commission_amount === null) $this->commission->apply($payable);
        } else {
            $payable->applyPaymentStatus($status, $paymentId, $method ?: 'mercadopago');
        }

        $transaction = $this->for($payable);
        if ($payable instanceof Appointment && $payable->commission_amount !== null) {
            $transaction->update(['house_amount' => $payable->commission_amount, 'professional_amount' => $payable->professional_amount]);
            $transaction->splits()->updateOrCreate(['professional_id' => $payable->professional_id], ['percentage' => 100, 'amount' => $payable->professional_amount]);
        }
        $transaction->update([
            'status' => $status,
            'provider_payment_id' => $paymentId ?: $transaction->provider_payment_id,
            'payment_method' => $method ?: $transaction->payment_method,
            'approved_at' => in_array($status, ['approved', 'authorized'], true) ? ($transaction->approved_at ?: now()) : $transaction->approved_at,
            'refunded_at' => $status === 'refunded' ? now() : $transaction->refunded_at,
        ]);

        if ($transaction->isPaid()) {
            $this->passes->issue($payable);
            if (! $wasPaid) $this->notifications->paymentApproved($payable);
        }
        if (in_array($status, ['cancelled', 'refunded'], true)) $this->passes->cancel($payable);

        return $transaction;
    }

    public function prepareAttempt(Appointment|EventRegistration $payable): Transaction
    {
        $transaction=$this->for($payable);
        if(in_array($transaction->status,['rejected','cancelled'],true)){
            $transaction->update(['status'=>'pending','idempotency_key'=>hash('sha256',$payable->getMorphClass().':'.$payable->getKey().':'.Str::uuid())]);
            $payable->update(['payment_status'=>'pending','mp_payment_id'=>null]);
        }
        return $transaction;
    }
}
