<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\Relations\BelongsTo;

class EventRegistration extends Model
{
    protected $fillable = [
        'reference', 'event_id', 'customer_id', 'participant_name', 'participant_email', 'participant_phone',
        'status', 'amount', 'discount_amount',
        'payment_status', 'payment_method', 'mp_payment_id', 'paid_at', 'reminded',
        'house_amount', 'professional_amount', 'cancelled_at', 'checked_in_at', 'checked_in_by', 'consent_at',
    ];

    protected $casts = [
        'amount' => 'decimal:2',
        'discount_amount' => 'decimal:2',
        'paid_at' => 'datetime',
        'reminded' => 'boolean',
        'house_amount' => 'decimal:2',
        'professional_amount' => 'decimal:2',
        'cancelled_at' => 'datetime',
        'checked_in_at' => 'datetime',
        'consent_at' => 'datetime',
    ];

    public const STATUSES = [
        'registered' => 'Inscrito',
        'confirmed' => 'Confirmado',
        'cancelled' => 'Cancelado',
        'attended' => 'Compareceu',
    ];

    public function event(): BelongsTo
    {
        return $this->belongsTo(Event::class);
    }

    public function customer(): BelongsTo
    {
        return $this->belongsTo(User::class, 'customer_id');
    }

    public function isPaid(): bool
    {
        return in_array($this->payment_status, ['approved', 'authorized'], true);
    }

    public function transactions(): \Illuminate\Database\Eloquent\Relations\MorphMany
    {
        return $this->morphMany(Transaction::class, 'payable');
    }

    public function accessPasses(): \Illuminate\Database\Eloquent\Relations\MorphMany
    {
        return $this->morphMany(AccessPass::class, 'passable');
    }

    public function applyPaymentStatus(string $status, ?string $paymentId = null, ?string $method = null): void
    {
        $this->payment_status = $status;
        if ($paymentId) $this->mp_payment_id = $paymentId;
        if ($method) $this->payment_method = $method;
        if ($this->isPaid()) {
            $this->paid_at ??= now();
            if ($this->status === 'registered') $this->status = 'confirmed';
        }
        $this->save();
    }

    public function getStatusLabelAttribute(): string
    {
        return self::STATUSES[$this->status] ?? $this->status;
    }
}
