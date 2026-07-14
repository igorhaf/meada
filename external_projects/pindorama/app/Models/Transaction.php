<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\Relations\BelongsTo;
use Illuminate\Database\Eloquent\Relations\MorphTo;
use Illuminate\Database\Eloquent\Relations\HasMany;

class Transaction extends Model
{
    protected $fillable = ['reference', 'payable_type', 'payable_id', 'customer_id', 'professional_id', 'gross_amount', 'discount_amount', 'house_amount', 'professional_amount', 'provider', 'provider_payment_id', 'payment_method', 'status', 'idempotency_key', 'approved_at', 'refunded_at', 'settled_at', 'metadata'];
    protected $casts = ['gross_amount' => 'decimal:2', 'discount_amount' => 'decimal:2', 'house_amount' => 'decimal:2', 'professional_amount' => 'decimal:2', 'approved_at' => 'datetime', 'refunded_at' => 'datetime', 'settled_at' => 'datetime', 'metadata' => 'array'];

    public function payable(): MorphTo { return $this->morphTo(); }
    public function customer(): BelongsTo { return $this->belongsTo(User::class); }
    public function professional(): BelongsTo { return $this->belongsTo(User::class); }
    public function isPaid(): bool { return in_array($this->status, ['approved', 'authorized'], true); }
    public function splits(): HasMany { return $this->hasMany(TransactionSplit::class); }
}
