<?php

namespace App\Models;

use App\Casts\UtcDateTime;
use Illuminate\Database\Eloquent\Builder;
use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\Relations\BelongsTo;

class Appointment extends Model
{
    protected $fillable = [
        'reference', 'professional_id', 'service_id', 'attendance_location_id', 'customer_id',
        'patient_name', 'patient_email', 'patient_phone',
        'service_title', 'service_price', 'duration_minutes', 'modality', 'location_label',
        'start_at', 'end_at', 'timezone', 'status', 'meeting_link', 'notes',
        'payment_status', 'payment_method', 'mp_payment_id', 'paid_at', 'total',
        'commission_amount', 'professional_amount',
        'confirmed_at', 'completed_at', 'cancelled_at', 'cancelled_by',
        'health_data_consent', 'consent_at', 'reminded_at',
    ];

    protected $casts = [
        'start_at' => UtcDateTime::class,
        'end_at' => UtcDateTime::class,
        'paid_at' => 'datetime',
        'confirmed_at' => 'datetime',
        'completed_at' => 'datetime',
        'cancelled_at' => 'datetime',
        'service_price' => 'decimal:2',
        'total' => 'decimal:2',
        'health_data_consent' => 'boolean',
        'consent_at' => 'datetime',
        'reminded_at' => 'datetime',
    ];

    /** Fulfillment states. pending = aguardando aceite do terapeuta. */
    public const STATUSES = [
        'pending' => 'Aguardando confirmação',
        'confirmed' => 'Confirmado',
        'completed' => 'Concluído',
        'cancelled' => 'Cancelado',
        'no_show' => 'Não compareceu',
    ];

    /** Statuses that occupy a slot (counted in conflict detection). */
    public const BLOCKING_STATUSES = ['pending', 'confirmed'];

    /** Mercado Pago payment statuses → labels. */
    public const PAYMENT_STATUSES = [
        'pending' => 'Pendente',
        'in_process' => 'Em análise',
        'approved' => 'Aprovado',
        'authorized' => 'Autorizado',
        'rejected' => 'Recusado',
        'refunded' => 'Estornado',
        'cancelled' => 'Cancelado',
    ];

    /* ---------------------------------------------------------------- Relations */

    public function professional(): BelongsTo
    {
        return $this->belongsTo(User::class, 'professional_id');
    }

    public function service(): BelongsTo
    {
        return $this->belongsTo(Service::class);
    }

    public function location(): BelongsTo
    {
        return $this->belongsTo(AttendanceLocation::class, 'attendance_location_id');
    }

    public function customer(): BelongsTo
    {
        return $this->belongsTo(User::class, 'customer_id');
    }

    public function transactions(): \Illuminate\Database\Eloquent\Relations\MorphMany
    {
        return $this->morphMany(Transaction::class, 'payable');
    }

    public function accessPasses(): \Illuminate\Database\Eloquent\Relations\MorphMany
    {
        return $this->morphMany(AccessPass::class, 'passable');
    }

    /* ------------------------------------------------------------------- Scopes */

    public function scopeBlocking(Builder $query): Builder
    {
        return $query->whereIn('status', self::BLOCKING_STATUSES);
    }

    /* ------------------------------------------------------------------- Status */

    public function isPaid(): bool
    {
        return in_array($this->payment_status, ['approved', 'authorized'], true);
    }

    public function isBlocking(): bool
    {
        return in_array($this->status, self::BLOCKING_STATUSES, true);
    }

    /**
     * Map a Mercado Pago payment status onto this appointment.
     *
     * Approving a payment IS the aceite for the prepay flow: a still-pending
     * appointment auto-confirms. (Pay-on-site appointments never reach here —
     * they wait for the therapist's manual confirm.)
     */
    public function applyPaymentStatus(string $mpStatus, ?string $paymentId = null): void
    {
        $this->payment_status = array_key_exists($mpStatus, self::PAYMENT_STATUSES) ? $mpStatus : 'pending';
        $this->payment_method = $this->payment_method ?: 'mercadopago';
        if ($paymentId) {
            $this->mp_payment_id = $paymentId;
        }

        if (in_array($mpStatus, ['approved', 'authorized'], true)) {
            $this->paid_at ??= now();
            if ($this->status === 'pending') {
                $this->status = 'confirmed';
                $this->confirmed_at ??= now();
            }
        }

        $this->save();
    }

    /** Demo fallback when Mercado Pago is disabled. */
    public function markSimulatedPaid(): void
    {
        $this->payment_method = 'simulado';
        $this->applyPaymentStatus('approved');
    }

    public function getStatusLabelAttribute(): string
    {
        return self::STATUSES[$this->status] ?? ucfirst($this->status);
    }

    public function getPaymentStatusLabelAttribute(): string
    {
        return self::PAYMENT_STATUSES[$this->payment_status] ?? ucfirst($this->payment_status);
    }
}
