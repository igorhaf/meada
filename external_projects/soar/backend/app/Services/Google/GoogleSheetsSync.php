<?php

namespace App\Services\Google;

use App\Models\ExpenseEntry;
use Illuminate\Support\Facades\Http;
use Illuminate\Support\Facades\Log;
use Throwable;

/**
 * Sincroniza lançamentos de gastos com o Google Sheets (append, best-effort).
 * A planilha vem de pages.meta->google_sheet_id (por página) ou do env global.
 */
class GoogleSheetsSync
{
    private const API = 'https://sheets.googleapis.com/v4/spreadsheets';

    public function __construct(private readonly GoogleServiceAccount $auth)
    {
    }

    public function appendExpense(ExpenseEntry $entry): void
    {
        $sheetId = $entry->page->meta['google_sheet_id'] ?? config('services.google.sheet_id_gastos');
        if (! $this->auth->isConfigured() || ! $sheetId) {
            return;
        }

        try {
            $range = $entry->page->meta['sheet_range'] ?? 'A:F';
            $response = Http::withToken($this->auth->accessToken())
                ->post(self::API."/{$sheetId}/values/".rawurlencode($range).':append?valueInputOption=USER_ENTERED', [
                    'values' => [[
                        $entry->date->format('d/m/Y'),
                        $entry->description,
                        $entry->category ?? '',
                        number_format($entry->amount_cents / 100, 2, ',', ''),
                        $entry->paid_by ?? '',
                        $entry->card ?? '',
                    ]],
                ]);

            if ($response->successful()) {
                $entry->forceFill(['synced_to_sheet' => true])->saveQuietly();
            } else {
                Log::warning('Google Sheets append falhou', ['status' => $response->status()]);
            }
        } catch (Throwable $e) {
            Log::warning('Sync Google Sheets falhou (best-effort)', ['error' => $e->getMessage()]);
        }
    }
}
