<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\Relations\BelongsTo;

class VaultEntry extends Model
{
    protected $fillable = ['page_id', 'title', 'username', 'secret', 'url', 'notes', 'position'];

    protected $hidden = ['secret']; // nunca sai em listagem; só via endpoint reveal

    protected function casts(): array
    {
        return ['secret' => 'encrypted'];
    }

    public function page(): BelongsTo
    {
        return $this->belongsTo(Page::class);
    }
}
