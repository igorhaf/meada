<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\Relations\BelongsTo;

class RegistroEntry extends Model
{
    protected $fillable = ['page_id', 'data', 'position'];

    protected function casts(): array
    {
        return ['data' => 'array'];
    }

    public function page(): BelongsTo
    {
        return $this->belongsTo(Page::class);
    }
}
