<?php

namespace App\Models;

use App\Casts\UtcDateTime;
use Illuminate\Database\Eloquent\Builder;
use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\Relations\BelongsTo;
use Illuminate\Database\Eloquent\Relations\HasMany;
use Illuminate\Database\Eloquent\Relations\BelongsToMany;

class Event extends Model
{
    protected $fillable = [
        'professional_id', 'title', 'slug', 'description', 'type', 'modality', 'location_label',
        'starts_at', 'ends_at', 'timezone', 'capacity', 'price', 'is_free', 'allow_discount',
        'discount_percent', 'cover_path', 'status', 'reminder_hours', 'reminded_at',
        'created_by', 'room_id', 'house_percentage',
    ];

    protected $casts = [
        'starts_at' => UtcDateTime::class,
        'ends_at' => UtcDateTime::class,
        'reminded_at' => 'datetime',
        'price' => 'decimal:2',
        'discount_percent' => 'decimal:2',
        'is_free' => 'boolean',
        'allow_discount' => 'boolean',
        'house_percentage' => 'decimal:2',
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

    public function instructors(): BelongsToMany
    {
        return $this->belongsToMany(User::class, 'event_professional', 'event_id', 'professional_id')
            ->withPivot(['role', 'can_view_financials', 'can_manage_attendance', 'revenue_percentage', 'position'])
            ->withTimestamps()
            ->orderByPivot('position');
    }

    public function sessions(): HasMany
    {
        return $this->hasMany(EventSession::class)->orderBy('starts_at');
    }

    public function room(): BelongsTo
    {
        return $this->belongsTo(Room::class);
    }

    public function creator(): BelongsTo
    {
        return $this->belongsTo(User::class, 'created_by');
    }

    public function transactions(): \Illuminate\Database\Eloquent\Relations\MorphMany
    {
        return $this->morphMany(Transaction::class, 'payable');
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
