<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\Relations\BelongsTo;

class PlatformCharge extends Model
{
    protected $fillable = [
        'reference', 'professional_id', 'type', 'description',
        'base_amount', 'discount_amount', 'amount', 'reference_month',
        'status', 'due_date', 'paid_at', 'payment_method', 'mp_payment_id',
    ];

    protected $casts = [
        'base_amount' => 'decimal:2',
        'discount_amount' => 'decimal:2',
        'amount' => 'decimal:2',
        'due_date' => 'date',
        'paid_at' => 'datetime',
    ];

    public const TYPES = [
        'subscription' => 'Mensalidade',
        'registration' => 'Cadastro',
        'featured' => 'Anúncio destaque',
    ];

    public const STATUSES = [
        'pending' => 'Pendente',
        'paid' => 'Pago',
        'waived' => 'Isento',
    ];

    public function professional(): BelongsTo
    {
        return $this->belongsTo(User::class, 'professional_id');
    }

    public function isPaid(): bool
    {
        return in_array($this->status, ['paid', 'waived'], true);
    }

    public function getTypeLabelAttribute(): string
    {
        return self::TYPES[$this->type] ?? $this->type;
    }

    public function getStatusLabelAttribute(): string
    {
        return self::STATUSES[$this->status] ?? $this->status;
    }
}
