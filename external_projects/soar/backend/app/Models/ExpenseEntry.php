<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\Relations\BelongsTo;

class ExpenseEntry extends Model
{
    protected $fillable = [
        'page_id', 'date', 'description', 'category', 'amount_cents',
        'paid_by', 'card', 'synced_to_sheet',
    ];

    protected function casts(): array
    {
        return [
            'date' => 'date:Y-m-d',
            'synced_to_sheet' => 'boolean',
        ];
    }

    public function page(): BelongsTo
    {
        return $this->belongsTo(Page::class);
    }
}
