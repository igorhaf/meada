<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\Relations\BelongsTo;

class Payout extends Model
{
    protected $fillable = ['reference', 'professional_id', 'amount', 'status', 'period_start', 'period_end', 'paid_at', 'notes', 'created_by'];
    protected $casts = ['amount' => 'decimal:2', 'period_start' => 'date', 'period_end' => 'date', 'paid_at' => 'datetime'];
    public function professional(): BelongsTo { return $this->belongsTo(User::class, 'professional_id'); }
}
