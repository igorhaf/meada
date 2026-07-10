<?php

namespace App\Models;

use App\Casts\UtcDateTime;
use Illuminate\Database\Eloquent\Builder;
use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\Relations\BelongsTo;
use Illuminate\Database\Eloquent\Relations\HasMany;

class Event extends Model
{
    protected $fillable = [
        'professional_id', 'title', 'slug', 'description', 'type', 'modality', 'location_label',
        'starts_at', 'ends_at', 'timezone', 'capacity', 'price', 'is_free', 'allow_discount',
        'discount_percent', 'cover_path', 'status', 'reminder_hours', 'reminded_at',
    ];

    protected $casts = [
        'starts_at' => UtcDateTime::class,
        'ends_at' => UtcDateTime::class,
        'reminded_at' => 'datetime',
        'price' => 'decimal:2',
        'discount_percent' => 'decimal:2',
        'is_free' => 'boolean',
        'allow_discount' => 'boolean',
    ];

    public const TYPES = [
        'roda' => 'Roda de terapia',
        'curso' => 'Curso presencial',
        'certificacao' => 'Certificação',
    ];

    public const STATUSES = ['draft' => 'Rascunho', 'published' => 'Publicado', 'cancelled' => 'Cancelado'];

    public function professional(): BelongsTo
    {
        return $this->belongsTo(User::class, 'professional_id');
    }

    public function registrations(): HasMany
    {
        return $this->hasMany(EventRegistration::class);
    }

    /** Inscrições que ocupam vaga (não canceladas). */
    public function activeRegistrations(): HasMany
    {
        return $this->registrations()->where('status', '!=', 'cancelled');
    }

    public function scopePublished(Builder $query): Builder
    {
        return $query->where('status', 'published');
    }

    public function scopeUpcoming(Builder $query): Builder
    {
        return $query->where('starts_at', '>=', now()->utc());
    }

    public function getRouteKeyName(): string
    {
        return 'slug';
    }

    public function getUrlAttribute(): string
    {
        return route('events.show', $this->slug);
    }

    public function getCoverUrlAttribute(): string
    {
        return $this->cover_path ?: placeholder_image($this->slug, $this->title);
    }

    public function getTypeLabelAttribute(): string
    {
        return self::TYPES[$this->type] ?? $this->type;
    }

    public function getSpotsLeftAttribute(): ?int
    {
        if ((int) $this->capacity === 0) {
            return null; // ilimitado
        }

        return max(0, (int) $this->capacity - $this->activeRegistrations()->count());
    }

    public function isFull(): bool
    {
        $left = $this->spots_left;

        return $left !== null && $left <= 0;
    }
}
