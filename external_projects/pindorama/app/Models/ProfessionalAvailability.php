<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\Relations\BelongsTo;

class ProfessionalAvailability extends Model
{
    protected $fillable = [
        'professional_id', 'attendance_location_id', 'weekday', 'start_time', 'end_time', 'is_active',
    ];

    protected $casts = [
        'weekday' => 'integer',
        'is_active' => 'boolean',
    ];

    public const WEEKDAYS = [
        0 => 'Domingo',
        1 => 'Segunda',
        2 => 'Terça',
        3 => 'Quarta',
        4 => 'Quinta',
        5 => 'Sexta',
        6 => 'Sábado',
    ];

    public function professional(): BelongsTo
    {
        return $this->belongsTo(User::class, 'professional_id');
    }

    public function location(): BelongsTo
    {
        return $this->belongsTo(AttendanceLocation::class, 'attendance_location_id');
    }

    /** "09:00" (drops the seconds for display/forms). */
    public function getStartHmAttribute(): string
    {
        return substr((string) $this->start_time, 0, 5);
    }

    public function getEndHmAttribute(): string
    {
        return substr((string) $this->end_time, 0, 5);
    }
}
