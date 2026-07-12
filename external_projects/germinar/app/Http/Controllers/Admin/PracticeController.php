<?php

namespace App\Http\Controllers\Admin;

use App\Http\Controllers\Controller;
use App\Models\Practice;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\RedirectResponse;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\DB;
use Illuminate\View\View;

class PracticeController extends Controller
{
    public function index(): View
    {
        return view('admin.practices.index', [
            'practices' => Practice::ordered()->get(),
        ]);
    }

    public function create(): View
    {
        return view('admin.practices.create');
    }

    public function store(Request $request): RedirectResponse
    {
        $data = $this->validated($request);
        $data['sort_order'] = (int) Practice::max('sort_order') + 1;

        Practice::create($data);

        return redirect()->route('admin.praticas.index')->with('status', 'Prática criada.');
    }

    public function edit(Practice $practice): View
    {
        return view('admin.practices.edit', compact('practice'));
    }

    public function update(Request $request, Practice $practice): RedirectResponse
    {
        $practice->update($this->validated($request));

        return redirect()->route('admin.praticas.index')->with('status', 'Prática atualizada.');
    }

    public function destroy(Request $request, Practice $practice): JsonResponse|RedirectResponse
    {
        $practice->delete();

        if ($request->wantsJson()) {
            return response()->json(['ok' => true]);
        }

        return redirect()->route('admin.praticas.index')->with('status', 'Prática excluída.');
    }

    public function reorder(Request $request): JsonResponse
    {
        $data = $request->validate([
            'ids' => ['required', 'array'],
            'ids.*' => ['integer', 'exists:practices,id'],
        ]);

        DB::transaction(function () use ($data): void {
            foreach ($data['ids'] as $position => $id) {
                Practice::whereKey($id)->update(['sort_order' => $position]);
            }
        });

        return response()->json(['ok' => true]);
    }

    public function toggle(Practice $practice): JsonResponse
    {
        $practice->update(['is_active' => ! $practice->is_active]);

        return response()->json(['ok' => true, 'is_active' => $practice->is_active]);
    }

    private function validated(Request $request): array
    {
        $data = $request->validate([
            'title' => ['required', 'string', 'max:255'],
            'description' => ['required', 'string'],
        ]);

        $data['is_active'] = $request->boolean('is_active');

        return $data;
    }
}
