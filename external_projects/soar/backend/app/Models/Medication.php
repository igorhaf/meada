<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\Relations\BelongsTo;
use Illuminate\Database\Eloquent\Relations\HasMany;

class Medication extends Model
{
    protected $fillable = [
        'page_id', 'person', 'name', 'dose', 'schedule_times', 'controlled',
        'prescription_until', 'stock', 'low_stock_threshold', 'notes', 'active',
    ];

    protected function casts(): array
    {
        return [
            'schedule_times' => 'array',
            'controlled' => 'boolean',
            'prescription_until' => 'date:Y-m-d',
            'active' => 'boolean',
        ];
    }

    public function page(): BelongsTo
    {
        return $this->belongsTo(Page::class);
    }

    public function logs(): HasMany
    {
        return $this->hasMany(MedicationLog::class)->orderByDesc('taken_at');
    }
}
