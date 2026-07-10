<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Builder;
use Illuminate\Database\Eloquent\Model;

class Expense extends Model
{
    protected $fillable = [
        'description', 'category', 'person', 'amount', 'expense_date',
        'is_recurring', 'recurrence_day', 'is_active', 'notes',
    ];

    protected $casts = [
        'amount' => 'decimal:2',
        'expense_date' => 'date',
        'is_recurring' => 'boolean',
        'is_active' => 'boolean',
    ];

    public const CATEGORIES = [
        'salario' => 'Salário',
        'aluguel' => 'Aluguel',
        'energia' => 'Energia',
        'agua' => 'Água',
        'gas' => 'Gás',
        'internet' => 'Internet',
        'embalagens' => 'Embalagens',
        'marketing' => 'Marketing',
        'manutencao' => 'Manutenção',
        'outros' => 'Outros',
    ];

    /* ------------------------------------------------------------------- Scopes */

    public function scopeRecurring(Builder $query): Builder
    {
        return $query->where('is_recurring', true);
    }

    public function scopeOneOff(Builder $query): Builder
    {
        return $query->where('is_recurring', false);
    }

    public function scopeSalaries(Builder $query): Builder
    {
        return $query->where('category', 'salario');
    }

    /** Recorrentes que CONTAM no mês (ativas e já iniciadas até o mês consultado). */
    public function scopeRecurringActiveIn(Builder $query, int $year, int $month): Builder
    {
        $endOfMonth = sprintf('%04d-%02d-01', $year, $month);

        return $query->recurring()
            ->where('is_active', true)
            ->whereDate('expense_date', '<', date('Y-m-d', strtotime("$endOfMonth +1 month")));
    }

    /* ---------------------------------------------------------------- Acessores */

    public function getCategoryLabelAttribute(): string
    {
        return self::CATEGORIES[$this->category] ?? ucfirst((string) $this->category);
    }
}
