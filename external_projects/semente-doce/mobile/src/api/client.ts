import Constants from 'expo-constants';

/**
 * Base da API: EXPO_PUBLIC_API_URL (env) vence; senão `extra.apiUrl` do app.json.
 * Em aparelho físico o domínio .local do host não resolve — rode o backend com
 * `php artisan serve --host 0.0.0.0` e exporte EXPO_PUBLIC_API_URL=http://<ip-do-pc>:8000/api/v1
 * (ver mobile/README.md).
 */
const fromEnv = process.env.EXPO_PUBLIC_API_URL;
const fromConfig = (Constants.expoConfig?.extra as { apiUrl?: string } | undefined)?.apiUrl;

export const API_URL = (fromEnv || fromConfig || 'http://semente-doce.meadadigital.local/api/v1').replace(/\/+$/, '');

export class ApiError extends Error {
    constructor(
        message: string,
        public status: number,
    ) {
        super(message);
        this.name = 'ApiError';
    }
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
    let res: Response;
    try {
        res = await fetch(`${API_URL}${path}`, {
            ...init,
            headers: {
                Accept: 'application/json',
                'Content-Type': 'application/json',
                ...(init?.headers ?? {}),
            },
        });
    } catch {
        throw new ApiError(
            'Não conseguimos falar com a confeitaria 🍮 — confira sua conexão e o endereço da API (EXPO_PUBLIC_API_URL).',
            0,
        );
    }

    const body = (await res.json().catch(() => null)) as { message?: string } | null;

    if (!res.ok) {
        throw new ApiError(body?.message ?? `Erro ${res.status} ao falar com a loja.`, res.status);
    }

    return body as T;
}

export const apiGet = <T>(path: string) => request<T>(path);

export const apiPost = <T>(path: string, data: unknown) =>
    request<T>(path, { method: 'POST', body: JSON.stringify(data) });
