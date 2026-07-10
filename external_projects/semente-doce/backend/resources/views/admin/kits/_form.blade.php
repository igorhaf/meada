@php($montador = $montador ?? false)

<form method="POST" action="{{ $action }}" class="space-y-6">
    @csrf
    @isset($method) @method($method) @endisset

    @if ($errors->any())
        <div class="rounded-xl bg-red-50 px-4 py-3 text-sm text-red-700">
            <ul class="list-inside list-disc space-y-0.5">
                @foreach ($errors->all() as $error)<li>{{ $error }}</li>@endforeach
            </ul>
        </div>
    @endif

    <div class="grid gap-6 lg:grid-cols-3">
        {{-- Dados do kit --}}
        <div class="space-y-6 lg:col-span-2">
            <div class="card space-y-4 p-6">
                <div>
                    <label class="field-label">Nome do kit *</label>
                    <input name="name" value="{{ old('name', $kit->name) }}" required placeholder="Ex.: Kit Festa Infantil" class="field-input">
                </div>
                <div>
                    <label class="field-label">Descrição</label>
                    <textarea name="description" rows="3" class="field-input">{{ old('description', $kit->description) }}</textarea>
                </div>
                <div class="grid gap-4 sm:grid-cols-2">
                    <div>
                        <label class="field-label">Serve / rende</label>
                        <input name="serves" value="{{ old('serves', $kit->serves) }}" placeholder="Serve 20 pessoas" class="field-input">
                    </div>
                    <div>
                        <label class="field-label">Imagem (URL)</label>
                        <input name="image_path" value="{{ old('image_path', $kit->image_path) }}" placeholder="https://…" class="field-input">
                    </div>
                </div>
            </div>
        </div>

        {{-- Configuração --}}
        <div class="space-y-6">
            <div class="card space-y-4 p-6">
                <div>
                    <label class="field-label">Tipo *</label>
                    <select name="kit_type" class="field-input">
                        @foreach(\App\Models\Kit::TYPES as $value => $label)
                            <option value="{{ $value }}" @selected(old('kit_type', $kit->kit_type) === $value)>{{ $label }}</option>
                        @endforeach
                    </select>
                </div>
                <div>
                    <label class="field-label">Preço do kit (R$) *</label>
                    <input name="price" type="number" step="0.01" min="0" value="{{ old('price', $kit->price) }}" required class="field-input">
                    <p class="mt-1 text-xs text-neutral-400">Compare com o total dos componentes para calibrar a economia.</p>
                </div>
                <div>
                    <label class="field-label">Ordem na vitrine</label>
                    <input name="position" type="number" min="0" value="{{ old('position', $kit->position ?: 0) }}" class="field-input">
                </div>
            </div>

            <div class="card space-y-4 p-6">
                <label class="flex items-center gap-2 text-sm text-neutral-700">
                    <input type="hidden" name="is_active" value="0">
                    <input type="checkbox" name="is_active" value="1" @checked(old('is_active', $kit->is_active)) class="rounded border-neutral-300 text-brand-600 focus:ring-brand-500">
                    Ativo (visível na loja)
                </label>
                <label class="flex items-center gap-2 text-sm text-neutral-700">
                    <input type="hidden" name="is_featured" value="0">
                    <input type="checkbox" name="is_featured" value="1" @checked(old('is_featured', $kit->is_featured)) class="rounded border-neutral-300 text-brand-600 focus:ring-brand-500">
                    ⭐ Destacar na home
                </label>
                <label class="flex items-center gap-2 text-sm text-neutral-700">
                    <input type="hidden" name="is_made_to_order" value="0">
                    <input type="checkbox" name="is_made_to_order" value="1" @checked(old('is_made_to_order', $kit->is_made_to_order)) class="rounded border-neutral-300 text-brand-600 focus:ring-brand-500">
                    Sob encomenda (agendado)
                </label>
                <div>
                    <label class="field-label">Prazo (dias)</label>
                    <input name="lead_time_days" type="number" min="0" value="{{ old('lead_time_days', $kit->lead_time_days) }}" class="field-input">
                </div>
            </div>
        </div>
    </div>

    {{-- ⭐ Montador de kits (só na edição, quando o kit já existe) --}}
    @if($montador)
        <div class="card p-6">
            <div class="mb-4">
                <h3 class="text-lg font-extrabold text-neutral-900">🧺 Montador do kit</h3>
                <p class="text-sm text-neutral-500">Busque itens do cardápio e monte a composição. O total sugerido ajuda a definir o preço do kit acima.</p>
            </div>
            <div data-island="KitBuilder" data-props='@json(['products' => $products, 'initialItems' => $items])'></div>
        </div>
    @endif

    <div class="flex gap-2">
        <button type="submit" class="btn-brand">{{ $submitLabel }}</button>
        <a href="{{ route('admin.kits.index') }}" class="btn-outline">Cancelar</a>
    </div>
</form>
