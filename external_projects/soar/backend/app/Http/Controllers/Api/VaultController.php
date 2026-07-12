<?php

namespace App\Http\Controllers\Api;

use App\Http\Controllers\Concerns\AuthorizesPageAccess;
use App\Http\Controllers\Controller;
use App\Models\Page;
use App\Models\VaultEntry;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;

class VaultController extends Controller
{
    use AuthorizesPageAccess;

    public function index(Request $request, Page $page): JsonResponse
    {
        $this->authorizeAccess($request, $page);
        $this->authorizeKind($page, 'vault');

        return response()->json($page->vaultEntries()->get());
    }

    public function store(Request $request, Page $page): JsonResponse
    {
        $this->authorizeAccess($request, $page);
        $this->authorizeKind($page, 'vault');

        $data = $request->validate([
            'title' => ['required', 'string', 'max:255'],
            'username' => ['nullable', 'string', 'max:255'],
            'secret' => ['required', 'string'],
            'url' => ['nullable', 'string', 'max:2048'],
            'notes' => ['nullable', 'string'],
        ]);

        $data['position'] = ($page->vaultEntries()->max('position') ?? -1) + 1;

        return response()->json($page->vaultEntries()->create($data), 201);
    }

    public function update(Request $request, Page $page, VaultEntry $entry): JsonResponse
    {
        $this->authorizeAccess($request, $page);
        abort_unless($entry->page_id === $page->id, 404);

        $data = $request->validate([
            'title' => ['sometimes', 'required', 'string', 'max:255'],
            'username' => ['sometimes', 'nullable', 'string', 'max:255'],
            'secret' => ['sometimes', 'required', 'string'],
            'url' => ['sometimes', 'nullable', 'string', 'max:2048'],
            'notes' => ['sometimes', 'nullable', 'string'],
        ]);

        $entry->update($data);

        return response()->json($entry->fresh());
    }

    /** A senha só sai daqui — sob demanda, nunca na listagem. */
    public function reveal(Request $request, Page $page, VaultEntry $entry): JsonResponse
    {
        $this->authorizeAccess($request, $page);
        abort_unless($entry->page_id === $page->id, 404);

        return response()->json(['secret' => $entry->secret]);
    }

    public function destroy(Request $request, Page $page, VaultEntry $entry): JsonResponse
    {
        $this->authorizeAccess($request, $page);
        abort_unless($entry->page_id === $page->id, 404);

        $entry->delete();

        return response()->json(['ok' => true]);
    }
}
