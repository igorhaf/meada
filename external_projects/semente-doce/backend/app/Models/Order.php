<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\Relations\BelongsTo;
use Illuminate\Database\Eloquent\Relations\HasMany;
use Illuminate\Support\Carbon;

class Order extends Model
{
    protected $fillable = [
        'reference', 'user_id', 'buyer_name', 'buyer_email', 'buyer_phone',
        'fulfillment_type', 'delivery_address', 'delivery_neighborhood', 'scheduled_for', 'notes',
        'status', 'payment_status', 'payment_method',
        'mp_preference_id', 'mp_payment_id', 'paid_at',
        'subtotal', 'delivery_fee', 'total',
    ];

    protected $casts = [
        'subtotal' => 'decimal:2',
        'delivery_fee' => 'decimal:2',
        'total' => 'decimal:2',
        'paid_at' => 'datetime',
        'scheduled_for' => 'date',
    ];

    /** Fluxo de atendimento da cozinha (Kanban do painel). */
    public const STATUSES = [
        'pending' => 'Aguardando pagamento',
        'paid' => 'Pago',
        'preparing' => 'Em preparo',
        'out_for_delivery' => 'Saiu para entrega',
        'ready' => 'Pronto para retirada',
        'delivered' => 'Entregue',
        'cancelled' => 'Cancelado',
    ];

    /** Rótulos amigáveis dos status de pagamento do Mercado Pago. */
    public const PAYMENT_STATUSES = [
        'pending' => 'Pendente',
        'in_process' => 'Em análise',
        'approved' => 'Aprovado',
        'authorized' => 'Autorizado',
        'rejected' => 'Recusado',
        'refunded' => 'Estornado',
        'charged_back' => 'Estornado',
        'cancelled' => 'Cancelado',
    ];

    public const FULFILLMENT = [
        'delivery' => 'Entrega',
        'pickup' => 'Retirada na loja',
    ];

    public function user(): BelongsTo
    {
        return $this->belongsTo(User::class);
    }

    public function items(): HasMany
    {
        return $this->hasMany(OrderItem::class);
    }

    public function getStatusLabelAttribute(): string
    {
        return self::STATUSES[$this->status] ?? ucfirst((string) $this->status);
    }

    public function getPaymentStatusLabelAttribute(): string
    {
        return self::PAYMENT_STATUSES[$this->payment_status] ?? ucfirst((string) $this->payment_status);
    }

    public function getFulfillmentLabelAttribute(): string
    {
        return self::FULFILLMENT[$this->fulfillment_type] ?? ucfirst((string) $this->fulfillment_type);
    }

    public function isPickup(): bool
    {
        return $this->fulfillment_type === 'pickup';
    }

    public function isPaid(): bool
    {
        return in_array($this->payment_status, ['approved', 'authorized'], true);
    }

    /**
     * Aplica um status de pagamento do Mercado Pago, mantendo o status de
     * atendimento e o paid_at em sincronia.
     */
    public function applyPaymentStatus(string $mpStatus, ?string $paymentId = null): void
    {
        $this->payment_status = $mpStatus;

        if ($paymentId) {
            $this->mp_payment_id = $paymentId;
        }

        if (in_array($mpStatus, ['approved', 'authorized'], true)) {
            $this->paid_at ??= Carbon::now();
            if ($this->status === 'pending') {
                $this->status = 'paid';
            }
        } elseif (in_array($mpStatus, ['rejected', 'cancelled'], true) && $this->status === 'pending') {
            $this->status = 'cancelled';
        }

        $this->save();
    }
}
