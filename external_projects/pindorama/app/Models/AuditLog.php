<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\Relations\BelongsTo;
use Illuminate\Database\Eloquent\Relations\MorphTo;

class AuditLog extends Model
{
    protected $fillable = ['actor_id', 'action', 'subject_type', 'subject_id', 'before', 'after', 'reason', 'ip_address'];
    protected $casts = ['before' => 'array', 'after' => 'array'];
    public function actor(): BelongsTo { return $this->belongsTo(User::class, 'actor_id'); }
    public function subject(): MorphTo { return $this->morphTo(); }
}
