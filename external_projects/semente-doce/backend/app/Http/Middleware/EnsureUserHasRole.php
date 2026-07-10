<?php

namespace App\Http\Middleware;

use Closure;
use Illuminate\Http\Request;
use Symfony\Component\HttpFoundation\Response;

class EnsureUserHasRole
{
    /**
     * Usage: ->middleware('role:root').
     * A Semente Doce é loja única: só há dois papéis — 'customer' (quem compra e
     * encomenda) e 'root' (a doceria, que administra tudo). Root passa sempre.
     */
    public function handle(Request $request, Closure $next, string $role): Response
    {
        $user = $request->user();

        if (! $user) {
            return redirect()->route('login');
        }

        $allowed = $role === 'root'
            ? $user->isRoot()
            : $user->role === $role;

        abort_unless($allowed, 403, 'Você não tem permissão para acessar esta área.');

        return $next($request);
    }
}
