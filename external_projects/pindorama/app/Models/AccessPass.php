<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\Relations\BelongsTo;
use Illuminate\Database\Eloquent\Relations\MorphTo;

class AccessPass extends Model
{
    protected $fillable = ['public_code', 'token_hash', 'passable_type', 'passable_id', 'holder_id', 'holder_name', 'status', 'valid_from', 'valid_until', 'used_at', 'checked_in_by', 'check_in_location'];
    protected $casts = ['valid_from' => 'datetime', 'valid_until' => 'datetime', 'used_at' => 'datetime'];

    public function passable(): MorphTo { return $this->morphTo(); }
    public function holder(): BelongsTo { return $this->belongsTo(User::class, 'holder_id'); }
    public function operator(): BelongsTo { return $this->belongsTo(User::class, 'checked_in_by'); }
    public function isValid(): bool { return $this->status === 'valid' && (! $this->valid_until || $this->valid_until->isFuture()); }
    public function getRouteKeyName(): string { return 'public_code'; }
}
