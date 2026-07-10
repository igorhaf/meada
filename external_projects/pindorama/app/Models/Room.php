<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Builder;
use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\Relations\HasMany;

class Room extends Model
{
    protected $fillable = ['name', 'description', 'is_active', 'position'];

    protected $casts = ['is_active' => 'boolean'];

    public function attendanceLocations(): HasMany
    {
        return $this->hasMany(AttendanceLocation::class);
    }

    public function scopeActive(Builder $query): Builder
    {
        return $query->where('is_active', true);
    }
}
