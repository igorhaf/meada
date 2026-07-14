<?php

namespace App\Services;

use App\Models\AuditLog;
use Illuminate\Database\Eloquent\Model;

class AuditService
{
    public function record(string $action, ?Model $subject = null, array $before = [], array $after = [], ?string $reason = null): AuditLog
    {
        return AuditLog::create([
            'actor_id' => auth()->id(), 'action' => $action,
            'subject_type' => $subject?->getMorphClass(), 'subject_id' => $subject?->getKey(),
            'before' => $before ?: null, 'after' => $after ?: null, 'reason' => $reason,
            'ip_address' => request()?->ip(),
        ]);
    }
}
