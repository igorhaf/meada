<?php

namespace App\Models;

use App\Casts\UtcDateTime;
use Illuminate\Database\Eloquent\Builder;
use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\Relations\BelongsTo;

class AvailabilityBlock extends Model
{
    protected $fillable = [
        'professional_id', 'attendance_location_id', 'starts_at', 'ends_at', 'all_day', 'reason',
    ];

    protected $casts = [
        'starts_at' => UtcDateTime::class,
        'ends_at' => UtcDateTime::class,
        'all_day' => 'boolean',
    ];

    public function professional(): BelongsTo
    {
        return $this->belongsTo(User::class, 'professional_id');
    }

    public function location(): BelongsTo
    {
        return $this->belongsTo(AttendanceLocation::class, 'attendance_location_id');
    }

    /**
     * Blocks that overlap [$start, $end) and apply to the given location
     * (a NULL location = the whole professional / every location).
     */
    public function scopeOverlapping(Builder $query, \DateTimeInterface $start, \DateTimeInterface $end, ?int $locationId = null): Builder
    {
        return $query->where('starts_at', '<', $end)->where('ends_at', '>', $start)
            ->when($locationId, fn (Builder $q) => $q->where(function (Builder $inner) use ($locationId) {
                $inner->whereNull('attendance_location_id')->orWhere('attendance_location_id', $locationId);
            }));
    }
}
