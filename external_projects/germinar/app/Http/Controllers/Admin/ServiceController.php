<?php

namespace App\Http\Controllers\Admin;

use App\Http\Controllers\Controller;
use App\Models\Service;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\RedirectResponse;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\DB;
use Illuminate\View\View;

class ServiceController extends Controller
{
    public function index(): View
    {
        return view('admin.services.index', [
            'services' => Service::ordered()->get(),
        ]);
    }

    public function create(): View
    {
        return view('admin.services.create');
    }

    public function store(Request $request): RedirectResponse
    {
        $data = $this->validated($request);
        $data['sort_order'] = (int) Service::max('sort_order') + 1;

        Service::create($data);

        return redirect()->route('admin.servicos.index')->with('status', 'Serviço criado.');
    }

    public function edit(Service $service): View
    {
        return view('admin.services.edit', compact('service'));
    }

    public function update(Request $request, Service $service): RedirectResponse
    {
        $service->update($this->validated($request));

        return redirect()->route('admin.servicos.index')->with('status', 'Serviço atualizado.');
    }

    public function destroy(Request $request, Service $service): JsonResponse|RedirectResponse
    {
        $service->delete();

        if ($request->wantsJson()) {
            return response()->json(['ok' => true]);
        }

        return redirect()->route('admin.servicos.index')->with('status', 'Serviço excluído.');
    }

    public function reorder(Request $request): JsonResponse
    {
        $data = $request->validate([
            'ids' => ['required', 'array'],
            'ids.*' => ['integer', 'exists:services,id'],
        ]);

        DB::transaction(function () use ($data): void {
            foreach ($data['ids'] as $position => $id) {
                Service::whereKey($id)->update(['sort_order' => $position]);
            }
        });

        return response()->json(['ok' => true]);
    }

    public function toggle(Service $service): JsonResponse
    {
        $service->update(['is_active' => ! $service->is_active]);

        return response()->json(['ok' => true, 'is_active' => $service->is_active]);
    }

    private function validated(Request $request): array
    {
        $data = $request->validate([
            'title' => ['required', 'string', 'max:255'],
            'description' => ['required', 'string'],
            'dot_color' => ['required', 'in:accent,accent-2'],
        ]);

        $data['is_active'] = $request->boolean('is_active');

        return $data;
    }
}
