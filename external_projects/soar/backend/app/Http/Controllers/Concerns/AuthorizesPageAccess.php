<?php

namespace App\Http\Controllers\Concerns;

use App\Models\Page;
use Illuminate\Http\Request;

trait AuthorizesPageAccess
{
    protected function authorizeAccess(Request $request, Page $page): void
    {
        abort_unless(
            $page->isAccessibleBy($request->user()),
            403,
            'Você não tem acesso a esta página.',
        );
    }

    protected function authorizeKind(Page $page, string $kind): void
    {
        abort_unless($page->kind === $kind, 422, 'Tipo de página incompatível com esta operação.');
    }
}
