<?php

namespace App\Http\Controllers\Api;

use App\Http\Controllers\Concerns\AuthorizesPageAccess;
use App\Http\Controllers\Controller;
use App\Models\Page;
use App\Models\RegistroEntry;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;

/**
 * Registros: página com template dinâmico (pages.meta->template = lista de campos)
 * e entradas em JSON conforme o template — cartões, fichas dos filhos, cachorro etc.
 */
class RegistroController extends Controller
{
    use AuthorizesPageAccess;

    public function index(Request $request, Page $page): JsonResponse
    {
        $this->authorizeAccess($request, $page);
        $this->authorizeKind($page, 'registro');

        return response()->json([
            'template' => $page->meta['template'] ?? [],
            'entries' => $page->registroEntries()->get(),
        ]);
    }

    public function updateTemplate(Request $request, Page $page): JsonResponse
    {
        $this->authorizeAccess($request, $page);
        $this->authorizeKind($page, 'registro');

        $data = $request->validate([
            'template' => ['required', 'array', 'max:30'],
            'template.*.key' => ['required', 'string', 'max:60'],
            'template.*.label' => ['required', 'string', 'max:120'],
            'template.*.type' => ['sometimes', 'in:text,number,date'],
        ]);

        $meta = $page->meta ?? [];
        $meta['template'] = $data['template'];
        $page->update(['meta' => $meta]);

        return response()->json(['template' => $meta['template']]);
    }

    public function store(Request $request, Page $page): JsonResponse
    {
        $this->authorizeAccess($request, $page);
        $this->authorizeKind($page, 'registro');

        $data = $request->validate(['data' => ['required', 'array']]);
        $position = ($page->registroEntries()->max('position') ?? -1) + 1;

        return response()->json(
            $page->registroEntries()->create(['data' => $data['data'], 'position' => $position]),
            201,
        );
    }

    public function update(Request $request, Page $page, RegistroEntry $entry): JsonResponse
    {
        $this->authorizeAccess($request, $page);
        abort_unless($entry->page_id === $page->id, 404);

        $data = $request->validate(['data' => ['required', 'array']]);
        $entry->update(['data' => $data['data']]);

        return response()->json($entry->fresh());
    }

    public function destroy(Request $request, Page $page, RegistroEntry $entry): JsonResponse
    {
        $this->authorizeAccess($request, $page);
        abort_unless($entry->page_id === $page->id, 404);

        $entry->delete();

        return response()->json(['ok' => true]);
    }
}
