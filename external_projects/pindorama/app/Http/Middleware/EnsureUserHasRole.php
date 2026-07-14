<?php

namespace App\Http\Middleware;

use Closure;
use Illuminate\Http\Request;
use Symfony\Component\HttpFoundation\Response;

class EnsureUserHasRole
{
    /**
     * Usage: ->middleware('role:professional') or 'role:root'.
     * Root always passes; 'professional' also accepts root (root is a super-tenant).
     */
    public function handle(Request $request, Closure $next, string $role): Response
    {
        $user = $request->user();

        if (! $user) {
            return redirect()->route('login');
        }

        abort_unless($user->is_active ?? true, 403, 'Seu acesso está desativado.');

        $allowed = match ($role) {
            'root' => $user->isRoot(),
            'professional' => $user->isProfessional(),
            default => $user->role === $role,
        };

        abort_unless($allowed, 403, 'Você não tem permissão para acessar esta área.');

        return $next($request);
    }
}
