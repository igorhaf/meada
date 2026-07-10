<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Builder;
use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\Relations\BelongsTo;
use Illuminate\Database\Eloquent\Relations\BelongsToMany;
use Illuminate\Database\Eloquent\Relations\HasMany;

class AttendanceLocation extends Model
{
    protected $fillable = [
        'professional_id', 'room_id', 'name', 'is_online',
        'address', 'neighborhood', 'city', 'state', 'zip', 'complement', 'map_url',
        'is_active', 'position',
    ];

    protected $casts = [
        'is_online' => 'boolean',
        'is_active' => 'boolean',
    ];

    public function professional(): BelongsTo
    {
        return $this->belongsTo(User::class, 'professional_id');
    }

    public function room(): BelongsTo
    {
        return $this->belongsTo(Room::class);
    }

    public function services(): BelongsToMany
    {
        return $this->belongsToMany(Service::class, 'attendance_location_service');
    }

    public function availabilities(): HasMany
    {
        return $this->hasMany(ProfessionalAvailability::class, 'attendance_location_id');
    }

    public function scopeActive(Builder $query): Builder
    {
        return $query->where('is_active', true);
    }

    public function getFullAddressAttribute(): string
    {
        if ($this->is_online) {
            return 'Atendimento online';
        }

        return collect([$this->address, $this->neighborhood, $this->city, $this->state])
            ->filter()->implode(', ');
    }
}
