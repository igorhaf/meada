<?php

use Illuminate\Foundation\Application;
use Illuminate\Foundation\Configuration\Exceptions;
use Illuminate\Foundation\Configuration\Middleware;
use Illuminate\Http\Request;

return Application::configure(basePath: dirname(__DIR__))
    ->withRouting(
        web: __DIR__.'/../routes/web.php',
        commands: __DIR__.'/../routes/console.php',
        health: '/up',
    )
    ->withMiddleware(function (Middleware $middleware): void {
        $middleware->redirectGuestsTo('/entrar');
        $middleware->redirectUsersTo('/admin');

        // Confia no proxy (Cloudflare/Caddy/nginx) e honra X-Forwarded-Proto,
        // pra o Laravel gerar URLs https e não quebrar assets por mixed content.
        $middleware->trustProxies(at: '*', headers:
            Request::HEADER_X_FORWARDED_FOR |
            Request::HEADER_X_FORWARDED_HOST |
            Request::HEADER_X_FORWARDED_PORT |
            Request::HEADER_X_FORWARDED_PROTO
        );
    })
    ->withExceptions(function (Exceptions $exceptions): void {
        // expectsJson: os fetch do admin (reorder/toggle/destroy) mandam
        // Accept: application/json — sem isso, 401/419 viravam redirect HTML
        // que o fetch segue e o Vue lia como sucesso silencioso.
        $exceptions->shouldRenderJsonWhen(
            fn (Request $request) => $request->expectsJson() || $request->is('api/*'),
        );
    })->create();
