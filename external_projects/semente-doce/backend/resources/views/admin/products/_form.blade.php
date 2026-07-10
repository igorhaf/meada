@php
    // Semente para o editor de grupos de opção. Se houve erro de validação, usa o
    // JSON que o usuário acabou de enviar; senão, monta a partir do produto.
    $seedOptionGroups = old('option_groups');
    if ($seedOptionGroups === null) {
        $seedOptionGroups = ($product->exists && $product->relationLoaded('optionGroups'))
            ? $product->optionGroups->map(fn ($g) => [
                'name' => $g->name,
                'min_select' => (int) $g->min_select,
                'max_select' => (int) $g->max_select,
                'is_required' => (bool) $g->is_required,
                'options' => $g->options->map(fn ($o) => [
                    'name' => $o->name,
                    'price_delta' => (float) $o->price_delta,
                ])->values(),
            ])->values()->toJson()
            : '[]';
    }

    $primaryImage = $product->exists ? $product->primary_image_url : null;
@endphp

<form method="POST" action="{{ $action }}" class="grid gap-6 lg:grid-cols-3">
    @csrf
    @isset($method) @method($method) @endisset

    @if ($errors->any())
        <div class="rounded-xl bg-red-50 px-4 py-3 text-sm text-red-700 lg:col-span-3">
            <ul class="list-inside list-disc space-y-0.5">
                @foreach ($errors->all() as $error)<li>{{ $error }}</li>@endforeach
            </ul>
        </div>
    @endif

    {{-- Coluna principal --}}
    <div class="space-y-6 lg:col-span-2">
        <div class="card space-y-4 p-6">
            <div>
                <label class="field-label">Nome do item *</label>
                <input name="title" value="{{ old('title', $product->title) }}" required placeholder="Ex.: Brigadeiro gourmet" class="field-input">
            </div>
            <div>
                <label class="field-label">Descrição</label>
                <textarea name="description" rows="4" class="field-input">{{ old('description', $product->description) }}</textarea>
            </div>
            <div class="grid gap-4 sm:grid-cols-3">
                <div>
                    <label class="field-label">Sabor</label>
                    <input name="flavor" value="{{ old('flavor', $product->flavor) }}" placeholder="Chocolate belga" class="field-input">
                </div>
                <div>
                    <label class="field-label">Rende / serve</label>
                    <input name="serves" value="{{ old('serves', $product->serves) }}" placeholder="Serve 8 a 10 fatias" class="field-input">
                </div>
                <div>
                    <label class="field-label">Alérgenos</label>
                    <input name="contains_allergens" value="{{ old('contains_allergens', $product->contains_allergens) }}" placeholder="glúten, leite, ovo" class="field-input">
                </div>
            </div>
            <div>
                <label class="field-label">Imagem (URL)</label>
                <input name="image_url" value="{{ old('image_url') }}" placeholder="https://… (deixe em branco p/ placeholder automático)" class="field-input">
                @if($primaryImage)
                    <div class="mt-3 flex items-center gap-3">
                        <img src="{{ $primaryImage }}" alt="" class="h-16 w-16 rounded-xl object-cover">
                        <span class="text-xs text-neutral-400">Imagem atual. Cole uma nova URL para trocar.</span>
                    </div>
                @endif
            </div>
        </div>

        {{-- Editor de grupos de opção (estilo iFood) --}}
        <div class="card space-y-4 p-6">
            <div class="flex items-center justify-between">
                <div>
                    <h3 class="text-sm font-bold text-neutral-800">🧩 Grupos de opção</h3>
                    <p class="text-xs text-neutral-400">Recheio, cobertura, tamanho… cada opção pode somar ao preço (delta em R$).</p>
                </div>
                <button type="button" id="optg-add" class="btn-outline !px-4 !py-2 text-xs">+ Grupo</button>
            </div>

            <div id="optg-list" class="space-y-4"></div>
            <p id="optg-empty" class="hidden py-4 text-center text-sm text-neutral-400">Nenhum grupo. Este item vai direto ao carrinho.</p>

            {{-- Hidden que o controller lê e sincroniza --}}
            <input type="hidden" name="option_groups" id="option_groups">
        </div>
    </div>

    {{-- Barra lateral --}}
    <div class="space-y-6">
        <div class="card space-y-4 p-6">
            <div>
                <label class="field-label">Categoria *</label>
                <select name="category_id" required class="field-input">
                    <option value="">Selecione…</option>
                    @foreach($categories as $root)
                        <option value="{{ $root->id }}" @selected((int) old('category_id', $product->category_id) === $root->id)>{{ $root->name }}</option>
                        @foreach($root->children as $child)
                            <option value="{{ $child->id }}" @selected((int) old('category_id', $product->category_id) === $child->id)>— {{ $child->name }}</option>
                        @endforeach
                    @endforeach
                </select>
            </div>
            <div>
                <label class="field-label">Unidade de venda *</label>
                <select name="unit" class="field-input">
                    @foreach(\App\Models\Product::UNITS as $value => $label)
                        <option value="{{ $value }}" @selected(old('unit', $product->unit) === $value)>{{ $label }}</option>
                    @endforeach
                </select>
            </div>
        </div>

        <div class="card space-y-4 p-6">
            <div class="grid grid-cols-2 gap-3">
                <div>
                    <label class="field-label">Preço (R$) *</label>
                    <input name="price" type="number" step="0.01" min="0" value="{{ old('price', $product->price) }}" required class="field-input">
                </div>
                <div>
                    <label class="field-label">Preço "de"</label>
                    <input name="compare_at_price" type="number" step="0.01" min="0" value="{{ old('compare_at_price', $product->compare_at_price) }}" class="field-input">
                </div>
                <div>
                    <label class="field-label">Pedido mínimo</label>
                    <input name="min_qty" type="number" min="1" value="{{ old('min_qty', $product->min_qty ?: 1) }}" required class="field-input">
                </div>
                <div>
                    <label class="field-label">SKU</label>
                    <input name="sku" value="{{ old('sku', $product->sku) }}" class="field-input">
                </div>
            </div>
        </div>

        <div class="card space-y-4 p-6">
            <h3 class="text-sm font-bold text-neutral-800">⏱️ Preparo</h3>
            <label class="flex items-center gap-2 text-sm text-neutral-700">
                <input type="hidden" name="is_made_to_order" value="0">
                <input type="checkbox" name="is_made_to_order" value="1" @checked(old('is_made_to_order', $product->is_made_to_order)) class="rounded border-neutral-300 text-brand-600 focus:ring-brand-500">
                Sob encomenda (precisa de agendamento)
            </label>
            <div class="grid grid-cols-2 gap-3">
                <div>
                    <label class="field-label">Prazo (dias)</label>
                    <input name="lead_time_days" type="number" min="0" value="{{ old('lead_time_days', $product->lead_time_days) }}" placeholder="p/ encomenda" class="field-input">
                </div>
                <div>
                    <label class="field-label">Preparo (min)</label>
                    <input name="prep_minutes" type="number" min="0" value="{{ old('prep_minutes', $product->prep_minutes) }}" placeholder="pronta-entrega" class="field-input">
                </div>
            </div>
        </div>

        <div class="card space-y-4 p-6">
            <div>
                <label class="field-label">Ordem na vitrine</label>
                <input name="position" type="number" min="0" value="{{ old('position', $product->position ?: 0) }}" class="field-input">
            </div>
            <label class="flex items-center gap-2 text-sm text-neutral-700">
                <input type="hidden" name="is_active" value="0">
                <input type="checkbox" name="is_active" value="1" @checked(old('is_active', $product->is_active)) class="rounded border-neutral-300 text-brand-600 focus:ring-brand-500">
                No cardápio (visível na loja)
            </label>
            <label class="flex items-center gap-2 text-sm text-neutral-700">
                <input type="hidden" name="is_featured" value="0">
                <input type="checkbox" name="is_featured" value="1" @checked(old('is_featured', $product->is_featured)) class="rounded border-neutral-300 text-brand-600 focus:ring-brand-500">
                ⭐ Destacar na home
            </label>
        </div>

        <div class="flex gap-2">
            <button type="submit" class="btn-brand flex-1">{{ $submitLabel }}</button>
            <a href="{{ route('admin.products.index') }}" class="btn-outline">Cancelar</a>
        </div>
    </div>
</form>

<script type="application/json" id="optg-seed">{!! $seedOptionGroups !!}</script>
@verbatim
<script>
(function () {
    const list = document.getElementById('optg-list');
    const hidden = document.getElementById('option_groups');
    const empty = document.getElementById('optg-empty');
    const addBtn = document.getElementById('optg-add');
    if (!list || !hidden) return;

    const inputCls = 'w-full rounded-lg border border-neutral-300 px-2.5 py-1.5 text-sm focus:border-brand-400 focus:outline-none';

    function optionRow(opt) {
        const row = document.createElement('div');
        row.className = 'optopt flex items-center gap-2';
        row.innerHTML =
            '<input class="opt-name ' + inputCls + '" placeholder="Nome da opção">' +
            '<div class="flex items-center gap-1 shrink-0"><span class="text-xs text-neutral-400">+R$</span>' +
            '<input class="opt-delta w-20 rounded-lg border border-neutral-300 px-2 py-1.5 text-sm focus:border-brand-400 focus:outline-none" type="number" step="0.01" value="0"></div>' +
            '<button type="button" class="opt-del rounded-lg px-2 py-1.5 text-xs text-red-500 hover:bg-red-50">✕</button>';
        row.querySelector('.opt-name').value = (opt && opt.name) || '';
        row.querySelector('.opt-delta').value = (opt && (opt.price_delta ?? 0)) || 0;
        row.querySelector('.opt-del').addEventListener('click', () => { row.remove(); serialize(); });
        return row;
    }

    function groupCard(group) {
        const card = document.createElement('div');
        card.className = 'optgroup rounded-2xl border border-neutral-200 bg-neutral-50 p-4 space-y-3';
        card.innerHTML =
            '<div class="flex items-center gap-2">' +
                '<input class="g-name flex-1 rounded-lg border border-neutral-300 px-3 py-2 text-sm font-medium focus:border-brand-400 focus:outline-none" placeholder="Nome do grupo (ex.: Recheio)">' +
                '<button type="button" class="g-del rounded-lg px-2.5 py-2 text-xs font-medium text-red-500 hover:bg-red-100">Remover grupo</button>' +
            '</div>' +
            '<div class="flex flex-wrap items-center gap-3 text-xs text-neutral-600">' +
                '<label class="flex items-center gap-1">mín <input class="g-min w-14 rounded border border-neutral-300 px-2 py-1" type="number" min="0" value="0"></label>' +
                '<label class="flex items-center gap-1">máx <input class="g-max w-14 rounded border border-neutral-300 px-2 py-1" type="number" min="1" value="1"></label>' +
                '<label class="flex items-center gap-1"><input class="g-req rounded border-neutral-300 text-brand-600" type="checkbox"> obrigatório</label>' +
            '</div>' +
            '<div class="g-opts space-y-2"></div>' +
            '<button type="button" class="g-addopt text-xs font-semibold text-brand-700 hover:underline">+ opção</button>';

        card.querySelector('.g-name').value = (group && group.name) || '';
        card.querySelector('.g-min').value = (group && (group.min_select ?? 0)) || 0;
        card.querySelector('.g-max').value = (group && (group.max_select ?? 1)) || 1;
        card.querySelector('.g-req').checked = !!(group && group.is_required);

        const opts = card.querySelector('.g-opts');
        ((group && group.options) || []).forEach((o) => opts.appendChild(optionRow(o)));

        card.querySelector('.g-del').addEventListener('click', () => { card.remove(); serialize(); });
        card.querySelector('.g-addopt').addEventListener('click', () => { opts.appendChild(optionRow({})); serialize(); });
        return card;
    }

    function serialize() {
        const groups = [];
        list.querySelectorAll('.optgroup').forEach((card) => {
            const name = card.querySelector('.g-name').value.trim();
            if (!name) return;
            const options = [];
            card.querySelectorAll('.optopt').forEach((row) => {
                const oname = row.querySelector('.opt-name').value.trim();
                if (!oname) return;
                options.push({ name: oname, price_delta: parseFloat(row.querySelector('.opt-delta').value) || 0 });
            });
            groups.push({
                name: name,
                min_select: parseInt(card.querySelector('.g-min').value, 10) || 0,
                max_select: parseInt(card.querySelector('.g-max').value, 10) || 1,
                is_required: card.querySelector('.g-req').checked,
                options: options,
            });
        });
        hidden.value = JSON.stringify(groups);
        empty.classList.toggle('hidden', list.children.length > 0);
    }

    list.addEventListener('input', serialize);
    addBtn.addEventListener('click', () => { list.appendChild(groupCard({})); serialize(); });

    // Semear
    let seed = [];
    try { seed = JSON.parse(document.getElementById('optg-seed').textContent || '[]'); } catch (e) { seed = []; }
    (Array.isArray(seed) ? seed : []).forEach((g) => list.appendChild(groupCard(g)));
    serialize();
})();
</script>
@endverbatim
