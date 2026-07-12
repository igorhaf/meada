<?php

namespace App\Http\Controllers\Api;

use App\Http\Controllers\Concerns\AuthorizesPageAccess;
use App\Http\Controllers\Controller;
use App\Models\Page;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;

/**
 * Dieta: perfil (pessoa, restrições, objetivos) vive em pages.meta; o plano gerado
 * vive em pages.content. A geração via Elo é ASSÍNCRONA: aqui só marcamos
 * meta.generate_requested — o scheduler (que roda junto do Elo) processa e grava.
 */
class DietController extends Controller
{
    use AuthorizesPageAccess;

    public function updateProfile(Request $request, Page $page): JsonResponse
    {
        $this->authorizeAccess($request, $page);
        $this->authorizeKind($page, 'diet');

        $data = $request->validate([
            'person' => ['required', 'string', 'max:100'],
            'restrictions' => ['nullable', 'string', 'max:2000'],
            'goals' => ['nullable', 'string', 'max:2000'],
        ]);

        $meta = $page->meta ?? [];
        $meta['person'] = $data['person'];
        $meta['restrictions'] = $data['restrictions'] ?? '';
        $meta['goals'] = $data['goals'] ?? '';
        $page->update(['meta' => $meta]);

        return response()->json($page->fresh());
    }

    public function generate(Request $request, Page $page): JsonResponse
    {
        $this->authorizeAccess($request, $page);
        $this->authorizeKind($page, 'diet');

        if (empty($page->meta['person'])) {
            return response()->json([
                'error' => 'Preencha o perfil (pessoa) antes de gerar a dieta.',
                'reason' => 'diet_profile_missing',
            ], 422);
        }

        $meta = $page->meta;
        $meta['generate_requested'] = true;
        $meta['generate_requested_by'] = $request->user()->id;
        $page->update(['meta' => $meta]);

        return response()->json(['status' => 'queued']);
    }
}
