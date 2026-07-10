<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Builder;
use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\Relations\BelongsTo;
use Illuminate\Support\Carbon;

class CustomOrder extends Model
{
    protected $fillable = [
        'reference', 'user_id', 'product_id', 'kit_id',
        'customer_name', 'customer_phone', 'customer_email',
        'title', 'description', 'quantity', 'flavor', 'message_on_item', 'reference_photo_url',
        'fulfillment_type', 'delivery_address', 'event_date',
        'status', 'quoted_price', 'admin_notes', 'quoted_at', 'confirmed_at',
    ];

    protected $casts = [
        'event_date' => 'date',
        'quoted_price' => 'decimal:2',
        'quoted_at' => 'datetime',
        'confirmed_at' => 'datetime',
    ];

    /** Fluxo da encomenda (gate humano — a loja orça e confirma). */
    public const STATUSES = [
        'requested' => 'Solicitada',
        'quoted' => 'Orçada',
        'confirmed' => 'Confirmada',
        'producing' => 'Em produção',
        'ready' => 'Pronta',
        'delivered' => 'Entregue',
        'declined' => 'Recusada',
        'cancelled' => 'Cancelada',
    ];

    /** Colunas do Kanban de encomendas (na ordem do fluxo). */
    public const BOARD = ['requested', 'quoted', 'confirmed', 'producing', 'ready', 'delivered'];

    public function user(): BelongsTo
    {
        return $this->belongsTo(User::class);
    }

    public function product(): BelongsTo
    {
        return $this->belongsTo(Product::class);
    }

    public function kit(): BelongsTo
    {
        return $this->belongsTo(Kit::class);
    }

    public function scopeOpen(Builder $query): Builder
    {
        return $query->whereNotIn('status', ['delivered', 'declined', 'cancelled']);
    }

    public function getStatusLabelAttribute(): string
    {
        return self::STATUSES[$this->status] ?? ucfirst((string) $this->status);
    }

    public function getFulfillmentLabelAttribute(): string
    {
        return $this->fulfillment_type === 'delivery' ? 'Entrega' : 'Retirada na loja';
    }

    public function isQuoted(): bool
    {
        return $this->quoted_price !== null;
    }

    /** Marca o orçamento (preço definido pela loja). */
    public function quote(float $price, ?string $notes = null): void
    {
        $this->quoted_price = $price;
        $this->admin_notes = $notes ?? $this->admin_notes;
        $this->quoted_at ??= Carbon::now();
        if ($this->status === 'requested') {
            $this->status = 'quoted';
        }
        $this->save();
    }
}
