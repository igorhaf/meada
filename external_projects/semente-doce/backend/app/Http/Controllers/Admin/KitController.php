<?php

namespace App\Http\Controllers\Admin;

use App\Http\Controllers\Controller;
use App\Models\Kit;
use App\Models\Product;
use Illuminate\Contracts\View\View;
use Illuminate\Http\RedirectResponse;
use Illuminate\Http\Request;
use Illuminate\Support\Str;

class KitController extends Controller
{
    public function index(): View
    {
        $kits = Kit::with('items')->orderBy('position')->latest()->get();

        return view('admin.kits.index', compact('kits'));
    }

    public function create(): View
    {
        return view('admin.kits.create', [
            'kit' => new Kit([
                'kit_type' => 'festa',
                'price' => 0,
                'is_active' => true,
                'is_made_to_order' => true,
            ]),
            'products' => $this->productOptions(),
            'items' => [],
        ]);
    }

    public function store(Request $request): RedirectResponse
    {
        $data = $this->validated($request);

        $kit = Kit::create($data + [
            'slug' => $this->uniqueSlug($data['name']),
        ]);

        $this->syncItems($kit, $request);

        return redirect()->route('admin.kits.edit', $kit)->with('status', 'Kit criado. Monte a composição abaixo.');
    }

    /** ⭐ O Montador de Kits vive na tela de edição. */
    public function edit(Kit $kit): View
    {
        $kit->load('items.product');

        $items = $kit->items->map(fn ($item) => [
            'product_id' => $item->product_id,
            'label' => $item->label,
            'qty' => (int) $item->qty,
            'unit_price' => (float) $item->unit_price,
        ])->values();

        return view('admin.kits.edit', [
            'kit' => $kit,
            'products' => $this->productOptions(),
            'items' => $items,
        ]);
    }

    public function update(Request $request, Kit $kit): RedirectResponse
    {
        $kit->update($this->validated($request));

        $this->syncItems($kit, $request);

        return redirect()->route('admin.kits.index')->with('status', 'Kit atualizado.');
    }

    public function destroy(Kit $kit): RedirectResponse
    {
        $kit->delete();

        return redirect()->route('admin.kits.index')->with('status', 'Kit removido.');
    }

    public function toggle(Kit $kit): RedirectResponse
    {
        $kit->update(['is_active' => ! $kit->is_active]);

        return back()->with('status', $kit->is_active
            ? "Kit \"{$kit->name}\" está ativo."
            : "Kit \"{$kit->name}\" foi pausado.");
    }

    /* --------------------------------------------------------------- helpers */

    /**
     * @return array<string,mixed>
     */
    private function validated(Request $request): array
    {
        return $request->validate([
            'name' => ['required', 'string', 'max:255'],
            'description' => ['nullable', 'string'],
            'kit_type' => ['required', 'in:' . implode(',', array_keys(Kit::TYPES))],
            'serves' => ['nullable', 'string', 'max:255'],
            'price' => ['required', 'numeric', 'min:0', 'max:1000000'],
            'image_path' => ['nullable', 'string', 'max:1000'],
            'is_active' => ['nullable', 'boolean'],
            'is_featured' => ['nullable', 'boolean'],
            'is_made_to_order' => ['nullable', 'boolean'],
            'lead_time_days' => ['nullable', 'integer', 'min:0', 'max:365'],
            'position' => ['nullable', 'integer', 'min:0'],
        ]);
    }

    /**
     * Recria os componentes do kit a partir do JSON `items` que o Montador submete.
     * Formato: [{product_id, label, qty, unit_price}]. Estratégia: apaga e recria.
     */
    private function syncItems(Kit $kit, Request $request): void
    {
        $raw = $request->input('items');
        if ($raw === null) {
            return; // sem o campo => não mexe (ex.: criação sem itens ainda)
        }

        $items = json_decode((string) $raw, true);
        if (! is_array($items)) {
            $items = [];
        }

        $kit->items()->delete();

        foreach (array_values($items) as $i => $item) {
            $label = trim((string) ($item['label'] ?? ''));
            if ($label === '') {
                continue;
            }

            $productId = $item['product_id'] ?? null;

            $kit->items()->create([
                'product_id' => is_numeric($productId) ? (int) $productId : null,
                'label' => $label,
                'qty' => max(1, (int) ($item['qty'] ?? 1)),
                'unit_price' => (float) ($item['unit_price'] ?? 0),
                'position' => $i,
            ]);
        }
    }

    /**
     * Produtos disponíveis para montar o kit (id, título, preço, unidade, imagem).
     *
     * @return \Illuminate\Support\Collection<int,array<string,mixed>>
     */
    private function productOptions()
    {
        return Product::active()
            ->with('images')
            ->orderBy('title')
            ->get()
            ->map(fn (Product $p) => [
                'id' => $p->id,
                'title' => $p->title,
                'price' => (float) $p->price,
                'unit' => $p->unit_label,
                'image' => $p->primary_image_url,
            ])
            ->values();
    }

    private function uniqueSlug(string $name): string
    {
        $base = Str::slug($name) ?: 'kit';
        $slug = $base;
        $i = 1;
        while (Kit::where('slug', $slug)->exists()) {
            $slug = $base . '-' . (++$i);
        }

        return $slug;
    }
}
