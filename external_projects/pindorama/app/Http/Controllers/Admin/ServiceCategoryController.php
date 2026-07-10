<?php

namespace App\Http\Controllers\Admin;

use App\Http\Controllers\Controller;
use App\Models\ServiceCategory;
use Illuminate\Http\RedirectResponse;
use Illuminate\Http\Request;
use Illuminate\Support\Str;
use Illuminate\View\View;

class ServiceCategoryController extends Controller
{
    public function index(): View
    {
        $roots = ServiceCategory::with(['children' => fn ($q) => $q->orderBy('position')])
            ->roots()->orderBy('position')->get();

        return view('admin.practices.index', compact('roots'));
    }

    public function create(): View
    {
        return view('admin.practices.form', [
            'category' => new ServiceCategory(['is_active' => true]),
            'roots' => ServiceCategory::roots()->orderBy('name')->get(),
        ]);
    }

    public function store(Request $request): RedirectResponse
    {
        ServiceCategory::create($this->validated($request));

        return redirect()->route('admin.practices.index')->with('status', 'Prática criada.');
    }

    public function edit(ServiceCategory $serviceCategory): View
    {
        return view('admin.practices.form', [
            'category' => $serviceCategory,
            'roots' => ServiceCategory::roots()->where('id', '!=', $serviceCategory->id)->orderBy('name')->get(),
        ]);
    }

    public function update(Request $request, ServiceCategory $serviceCategory): RedirectResponse
    {
        $serviceCategory->update($this->validated($request, $serviceCategory));

        return redirect()->route('admin.practices.index')->with('status', 'Prática atualizada.');
    }

    public function destroy(ServiceCategory $serviceCategory): RedirectResponse
    {
        if ($serviceCategory->services()->exists()) {
            return back()->with('error', 'Não é possível remover uma prática com serviços vinculados.');
        }
        $serviceCategory->delete();

        return back()->with('status', 'Prática removida.');
    }

    /** @return array<string,mixed> */
    private function validated(Request $request, ?ServiceCategory $current = null): array
    {
        $data = $request->validate([
            'name' => ['required', 'string', 'max:255'],
            'parent_id' => ['nullable', 'integer', 'exists:service_categories,id'],
            'icon' => ['nullable', 'string', 'max:16'],
            'accent' => ['nullable', 'string', 'max:9'],
            'description' => ['nullable', 'string', 'max:1000'],
            'position' => ['nullable', 'integer', 'min:0'],
            'is_active' => ['nullable', 'boolean'],
        ]);
        $data['is_active'] = $request->boolean('is_active');
        $data['position'] = (int) ($data['position'] ?? 0);
        if (! $current || $current->name !== $data['name']) {
            $data['slug'] = $this->uniqueSlug($data['name'], $current?->id);
        }

        return $data;
    }

    private function uniqueSlug(string $name, ?int $ignoreId): string
    {
        $base = Str::slug($name) ?: 'pratica';
        $slug = $base;
        $i = 2;
        while (ServiceCategory::where('slug', $slug)->when($ignoreId, fn ($q) => $q->where('id', '!=', $ignoreId))->exists()) {
            $slug = $base . '-' . $i++;
        }

        return $slug;
    }
}
