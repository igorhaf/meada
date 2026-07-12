<?php

namespace App\Services\Google;

use Illuminate\Support\Facades\Cache;
use Illuminate\Support\Facades\Http;
use RuntimeException;

/**
 * Autenticação por SERVICE ACCOUNT (JWT RS256 → access token), sem SDK.
 * Habilita quando GOOGLE_SA_JSON_PATH aponta pro JSON de credenciais.
 * Calendários/planilhas precisam ser COMPARTILHADOS com o client_email da SA.
 */
class GoogleServiceAccount
{
    private const SCOPES = 'https://www.googleapis.com/auth/calendar https://www.googleapis.com/auth/spreadsheets';

    public function isConfigured(): bool
    {
        $path = config('services.google.sa_json_path');

        return $path && is_readable($path);
    }

    public function accessToken(): string
    {
        return Cache::remember('google_sa_token', now()->addMinutes(50), function () {
            $creds = json_decode(file_get_contents(config('services.google.sa_json_path')), true);
            if (! $creds || empty($creds['client_email']) || empty($creds['private_key'])) {
                throw new RuntimeException('Credenciais da service account Google inválidas.');
            }

            $now = time();
            $header = $this->b64(json_encode(['alg' => 'RS256', 'typ' => 'JWT']));
            $claims = $this->b64(json_encode([
                'iss' => $creds['client_email'],
                'scope' => self::SCOPES,
                'aud' => 'https://oauth2.googleapis.com/token',
                'iat' => $now,
                'exp' => $now + 3600,
            ]));

            $signature = '';
            openssl_sign("$header.$claims", $signature, $creds['private_key'], 'sha256WithRSAEncryption');
            $jwt = "$header.$claims.".$this->b64($signature);

            $response = Http::asForm()->post('https://oauth2.googleapis.com/token', [
                'grant_type' => 'urn:ietf:params:oauth:grant-type:jwt-bearer',
                'assertion' => $jwt,
            ]);

            if (! $response->successful()) {
                throw new RuntimeException('Token Google falhou: '.$response->body());
            }

            return $response->json('access_token');
        });
    }

    private function b64(string $data): string
    {
        return rtrim(strtr(base64_encode($data), '+/', '-_'), '=');
    }
}
