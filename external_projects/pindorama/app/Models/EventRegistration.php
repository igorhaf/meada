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
    ];

    protected $casts = [
        'amount' => 'decimal:2',
        'discount_amount' => 'decimal:2',
        'paid_at' => 'datetime',
        'reminded' => 'boolean',
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

    public function getStatusLabelAttribute(): string
    {
        return self::STATUSES[$this->status] ?? $this->status;
    }
}
