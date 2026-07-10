<?php

namespace App\Http\Controllers\Admin;

use App\Http\Controllers\Controller;
use App\Models\Category;
use App\Models\Product;
use Illuminate\Contracts\View\View;
use Illuminate\Http\RedirectResponse;
use Illuminate\Http\Request;
use Illuminate\Support\Str;

class ProductController extends Controller
{
    public function index(Request $request): View
    {
        $products = Product::with(['images', 'category'])
            ->when($request->query('q'), fn ($q, $term) => $q->where('title', 'ilike', "%{$term}%"))
            ->orderBy('position')
            ->latest()
            ->paginate(15)
            ->withQueryString();

        return view('admin.products.index', compact('products'));
    }

    public function create(): View
    {
        return view('admin.products.create', [
            'product' => new Product([
                'unit' => 'unidade',
                'min_qty' => 1,
                'is_active' => true,
                'is_made_to_order' => false,
            ]),
            'categories' => $this->categoryOptions(),
        ]);
    }

    public function store(Request $request): RedirectResponse
    {
        $data = $this->validated($request);

        $product = Product::create($data + [
            'slug' => $this->uniqueSlug($data['title']),
        ]);

        $this->syncOptionGroups($product, $request);
        $this->syncImage($product, $request);

        return redirect()->route('admin.products.index')->with('status', 'Produto criado.');
    }

    public function edit(Product $product): View
    {
        return view('admin.products.edit', [
            'product' => $product->load(['images', 'optionGroups.options']),
            'categories' => $this->categoryOptions(),
        ]);
    }

    public function update(Request $request, Product $product): RedirectResponse
    {
        $product->update($this->validated($request));

        $this->syncOptionGroups($product, $request);
        $this->syncImage($product, $request);

        return redirect()->route('admin.products.index')->with('status', 'Produto atualizado.');
    }

    public function destroy(Product $product): RedirectResponse
    {
        $product->delete();

        return redirect()->route('admin.products.index')->with('status', 'Produto removido.');
    }

    public function toggle(Product $product): RedirectResponse
    {
        $product->update(['is_active' => ! $product->is_active]);

        return back()->with('status', $product->is_active
            ? "\"{$product->title}\" está no cardápio."
            : "\"{$product->title}\" saiu do cardápio.");
    }

    /* --------------------------------------------------------------- helpers */

    /**
     * @return array<string,mixed>
     */
    private function validated(Request $request): array
    {
        return $request->validate([
            'title' => ['required', 'string', 'max:255'],
            'category_id' => ['required', 'exists:categories,id'],
            'description' => ['nullable', 'string'],
            'unit' => ['required', 'in:' . implode(',', array_keys(Product::UNITS))],
            'flavor' => ['nullable', 'string', 'max:255'],
            'serves' => ['nullable', 'string', 'max:255'],
            'contains_allergens' => ['nullable', 'string', 'max:255'],
            'min_qty' => ['required', 'integer', 'min:1', 'max:100000'],
            'is_made_to_order' => ['nullable', 'boolean'],
            'lead_time_days' => ['nullable', 'integer', 'min:0', 'max:365'],
            'prep_minutes' => ['nullable', 'integer', 'min:0', 'max:100000'],
            'price' => ['required', 'numeric', 'min:0', 'max:1000000'],
            'compare_at_price' => ['nullable', 'numeric', 'min:0', 'max:1000000'],
            'sku' => ['nullable', 'string', 'max:100'],
            'is_active' => ['nullable', 'boolean'],
            'is_featured' => ['nullable', 'boolean'],
            'position' => ['nullable', 'integer', 'min:0'],
        ]);
    }

    private function categoryOptions()
    {
        return Category::with('children')->roots()->orderBy('position')->get();
    }

    private function uniqueSlug(string $title): string
    {
        $base = Str::slug($title) ?: 'produto';
        $slug = $base;
        $i = 1;
        while (Product::where('slug', $slug)->exists()) {
            $slug = $base . '-' . (++$i);
        }

        return $slug;
    }

    /**
     * Sincroniza os grupos de opção do produto a partir do JSON `option_groups`.
     * Formato: [{name, min_select, max_select, is_required, options:[{name, price_delta}]}].
     * Estratégia simples: apaga tudo e recria (o cascade remove as opções).
     */
    private function syncOptionGroups(Product $product, Request $request): void
    {
        $raw = $request->input('option_groups');
        if ($raw === null || trim((string) $raw) === '') {
            return; // campo ausente/vazio (ex.: JS não rodou) => não mexe nos grupos
        }

        $groups = json_decode((string) $raw, true);
        if (! is_array($groups)) {
            $groups = [];
        }

        $product->optionGroups()->delete(); // cascade apaga as product_options

        foreach (array_values($groups) as $gi => $group) {
            $name = trim((string) ($group['name'] ?? ''));
            if ($name === '') {
                continue;
            }

            $created = $product->optionGroups()->create([
                'name' => $name,
                'min_select' => (int) ($group['min_select'] ?? 0),
                'max_select' => max(1, (int) ($group['max_select'] ?? 1)),
                'is_required' => (bool) ($group['is_required'] ?? false),
                'position' => $gi,
            ]);

            foreach (array_values($group['options'] ?? []) as $oi => $option) {
                $optName = trim((string) ($option['name'] ?? ''));
                if ($optName === '') {
                    continue;
                }

                $created->options()->create([
                    'name' => $optName,
                    'price_delta' => (float) ($option['price_delta'] ?? 0),
                    'is_active' => true,
                    'position' => $oi,
                ]);
            }
        }
    }

    /**
     * Garante 1 imagem primária. Aceita uma URL colada (upload é backlog); sem URL,
     * cai num placeholder gerado a partir do slug.
     */
    private function syncImage(Product $product, Request $request): void
    {
        $url = trim((string) $request->input('image_url', ''));
        $path = $url !== '' ? $url : placeholder_image($product->slug, $product->title, 700, 700);

        $primary = $product->images()->where('is_primary', true)->first();

        if ($primary) {
            $primary->update(['path' => $path, 'alt' => $product->title]);
        } else {
            $product->images()->create([
                'path' => $path,
                'alt' => $product->title,
                'is_primary' => true,
                'position' => 0,
            ]);
        }
    }
}
