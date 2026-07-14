<?php

namespace App\Models;

use App\Casts\UtcDateTime;
use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\Relations\BelongsTo;
use Illuminate\Database\Eloquent\Relations\BelongsToMany;

class EventSession extends Model
{
    protected $fillable = ['event_id', 'room_id', 'title', 'starts_at', 'ends_at', 'timezone', 'modality', 'location_label', 'meeting_link', 'status'];

    protected $casts = ['starts_at' => UtcDateTime::class, 'ends_at' => UtcDateTime::class];

    public function event(): BelongsTo { return $this->belongsTo(Event::class); }
    public function room(): BelongsTo { return $this->belongsTo(Room::class); }
    public function professionals(): BelongsToMany { return $this->belongsToMany(User::class, 'event_session_professional', 'event_session_id', 'professional_id'); }
}
