<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\Relations\BelongsTo;

class TaskItem extends Model
{
    protected $fillable = ['page_id', 'content', 'assigned_user_id', 'due_date', 'done', 'position'];

    protected function casts(): array
    {
        return [
            'due_date' => 'date:Y-m-d',
            'done' => 'boolean',
        ];
    }

    public function page(): BelongsTo
    {
        return $this->belongsTo(Page::class);
    }

    public function assignee(): BelongsTo
    {
        return $this->belongsTo(User::class, 'assigned_user_id');
    }
}
