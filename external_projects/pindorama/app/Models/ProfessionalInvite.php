<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\Relations\BelongsTo;

class ProfessionalInvite extends Model
{
    protected $fillable = ['professional_id', 'token_hash', 'expires_at', 'accepted_at', 'created_by'];
    protected $casts = ['expires_at' => 'datetime', 'accepted_at' => 'datetime'];
    public function professional(): BelongsTo { return $this->belongsTo(User::class, 'professional_id'); }
}
