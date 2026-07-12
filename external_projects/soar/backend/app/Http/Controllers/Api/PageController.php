<?php

namespace App\Http\Controllers\Api;

use App\Http\Controllers\Controller;
use App\Models\Page;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;
use Illuminate\Support\Collection;
use Illuminate\Validation\Rule;

class PageController extends Controller
{
    /**
     * Retorna as duas árvores da primeira tela do dashboard:
     * a compartilhada (grupo único) e a pessoal do usuário logado.
     */
    public function tree(Request $request): JsonResponse
    {
        $userId = $request->user()->id;

        $shared = Page::shared()
            ->orderBy('position')
            ->get(['id', 'parent_id', 'scope', 'kind', 'title', 'icon', 'position']);

        $personal = Page::personalOf($userId)
            ->orderBy('position')
            ->get(['id', 'parent_id', 'scope', 'kind', 'title', 'icon', 'position']);

        return response()->json([
            'shared' => $this->buildTree($shared),
            'personal' => $this->buildTree($personal),
        ]);
    }

    public function show(Request $request, Page $page): JsonResponse
    {
        $this->authorizeAccess($request, $page);

        return response()->json($page->only([
            'id', 'parent_id', 'owner_id', 'scope', 'kind', 'title', 'icon', 'content', 'meta', 'position', 'updated_at',
        ]));
    }

    public function store(Request $request): JsonResponse
    {
        $data = $request->validate([
            'scope' => ['required', Rule::in([Page::SCOPE_SHARED, Page::SCOPE_PERSONAL])],
            'kind' => ['sometimes', Rule::in(Page::KINDS)],
            'parent_id' => ['nullable', 'integer', 'exists:pages,id'],
            'title' => ['required', 'string', 'max:255'],
            'icon' => ['nullable', 'string', 'max:16'],
            'content' => ['nullable', 'string'],
        ]);

        $user = $request->user();

        if (($data['parent_id'] ?? null) !== null) {
            $parent = Page::findOrFail($data['parent_id']);
            $this->authorizeAccess($request, $parent);

            if ($parent->scope !== $data['scope']) {
                return response()->json([
                    'error' => 'A página filha deve ter o mesmo escopo da página pai.',
                    'reason' => 'scope_mismatch',
                ], 422);
            }
        }

        $position = Page::where('scope', $data['scope'])
            ->when($data['scope'] === Page::SCOPE_PERSONAL, fn ($q) => $q->where('owner_id', $user->id))
            ->where('parent_id', $data['parent_id'] ?? null)
            ->max('position');

        $page = Page::create([
            'scope' => $data['scope'],
            'kind' => $data['kind'] ?? 'note',
            'parent_id' => $data['parent_id'] ?? null,
            'owner_id' => $data['scope'] === Page::SCOPE_PERSONAL ? $user->id : null,
            'title' => $data['title'],
            'icon' => $data['icon'] ?? null,
            'content' => $data['content'] ?? null,
            'position' => $position === null ? 0 : $position + 1,
        ]);

        return response()->json($page, 201);
    }

    public function update(Request $request, Page $page): JsonResponse
    {
        $this->authorizeAccess($request, $page);

        $data = $request->validate([
            'title' => ['sometimes', 'required', 'string', 'max:255'],
            'icon' => ['sometimes', 'nullable', 'string', 'max:16'],
            'content' => ['sometimes', 'nullable', 'string'],
        ]);

        $page->update($data);

        return response()->json($page->fresh());
    }

    public function move(Request $request, Page $page): JsonResponse
    {
        $this->authorizeAccess($request, $page);

        $data = $request->validate([
            'parent_id' => ['present', 'nullable', 'integer', 'exists:pages,id'],
            'position' => ['required', 'integer', 'min:0'],
        ]);

        if ($data['parent_id'] !== null) {
            $parent = Page::findOrFail($data['parent_id']);
            $this->authorizeAccess($request, $parent);

            if ($parent->scope !== $page->scope) {
                return response()->json([
                    'error' => 'Não é possível mover a página para outro escopo.',
                    'reason' => 'scope_mismatch',
                ], 422);
            }

            // Impede ciclo: o novo pai não pode ser a própria página nem um descendente dela.
            $cursor = $parent;
            while ($cursor !== null) {
                if ($cursor->id === $page->id) {
                    return response()->json([
                        'error' => 'Não é possível mover a página para dentro de si mesma.',
                        'reason' => 'circular_move',
                    ], 422);
                }
                $cursor = $cursor->parent;
            }
        }

        $page->update([
            'parent_id' => $data['parent_id'],
            'position' => $data['position'],
        ]);

        return response()->json($page->fresh());
    }

    public function destroy(Request $request, Page $page): JsonResponse
    {
        $this->authorizeAccess($request, $page);

        $page->delete(); // filhos caem em cascata (FK cascadeOnDelete)

        return response()->json(['ok' => true]);
    }

    /**
     * @param  Collection<int, Page>  $pages
     * @return array<int, array<string, mixed>>
     */
    private function buildTree(Collection $pages, ?int $parentId = null): array
    {
        return $pages
            ->where('parent_id', $parentId)
            ->map(fn (Page $page) => [
                'id' => $page->id,
                'parent_id' => $page->parent_id,
                'scope' => $page->scope,
                'kind' => $page->kind,
                'title' => $page->title,
                'icon' => $page->icon,
                'position' => $page->position,
                'children' => $this->buildTree($pages, $page->id),
            ])
            ->values()
            ->all();
    }

    private function authorizeAccess(Request $request, Page $page): void
    {
        abort_unless(
            $page->isAccessibleBy($request->user()),
            403,
            'Você não tem acesso a esta página.',
        );
    }
}
