@php($modalities = \App\Models\Service::MODALITIES)

<form method="POST" action="{{ $action }}" enctype="multipart/form-data" class="grid gap-6 lg:grid-cols-3">
    @csrf
    @isset($method) @method($method) @endisset

    @if ($errors->any())
        <div class="lg:col-span-3 rounded-xl bg-red-50 px-4 py-3 text-sm text-red-700">
            <ul class="list-inside list-disc space-y-0.5">
                @foreach ($errors->all() as $error)<li>{{ $error }}</li>@endforeach
            </ul>
        </div>
    @endif

    {{-- Main --}}
    <div class="space-y-6 lg:col-span-2">
        <div class="card space-y-4 p-6">
            <div>
                <label class="mb-1 block text-sm font-medium text-neutral-700">Título *</label>
                <input name="title" value="{{ old('title', $service->title) }}" required placeholder="Ex.: Sessão de acupuntura"
                    class="w-full rounded-xl border border-neutral-300 px-4 py-2.5 text-sm focus:border-brand-400 focus:outline-none focus:ring-2 focus:ring-brand-100">
            </div>
            <div>
                <label class="mb-1 block text-sm font-medium text-neutral-700">Descrição</label>
                <textarea name="description" rows="5" placeholder="Descreva o atendimento, indicações e o que o cliente pode esperar."
                    class="w-full rounded-xl border border-neutral-300 px-4 py-2.5 text-sm focus:border-brand-400 focus:outline-none focus:ring-2 focus:ring-brand-100">{{ old('description', $service->description) }}</textarea>
            </div>
            <div class="grid gap-4 sm:grid-cols-2">
                <div>
                    <label class="mb-1 block text-sm font-medium text-neutral-700">Duração (min) *</label>
                    <input name="duration_minutes" type="number" min="5" max="480" value="{{ old('duration_minutes', $service->duration_minutes) }}" required
                        class="w-full rounded-xl border border-neutral-300 px-4 py-2.5 text-sm focus:border-brand-400 focus:outline-none">
                    <p class="mt-1 text-xs text-neutral-400">Dimensiona os horários da sua agenda.</p>
                </div>
                <div>
                    <label class="mb-1 block text-sm font-medium text-neutral-700">Intervalo após (min)</label>
                    <input name="buffer_minutes" type="number" min="0" max="120" value="{{ old('buffer_minutes', $service->buffer_minutes) }}"
                        class="w-full rounded-xl border border-neutral-300 px-4 py-2.5 text-sm focus:border-brand-400 focus:outline-none">
                    <p class="mt-1 text-xs text-neutral-400">Tempo de descanso/preparo entre atendimentos.</p>
                </div>
            </div>
        </div>

        <div class="card space-y-3 p-6">
            <label class="block text-sm font-medium text-neutral-700">Imagem de capa</label>

            @if($service->exists)
                <img src="{{ $service->cover_url }}" alt="" class="aspect-video w-full max-w-sm rounded-xl border border-neutral-200 object-cover">
            @endif

            <input type="file" name="cover" accept="image/png,image/jpeg,image/webp"
                class="w-full rounded-xl border border-dashed border-neutral-300 px-4 py-3 text-sm text-neutral-600 file:mr-3 file:rounded-lg file:border-0 file:bg-brand-50 file:px-3 file:py-1.5 file:text-sm file:font-semibold file:text-brand-700 hover:border-brand-400">
            <p class="text-xs text-neutral-400">Envie uma foto do seu computador (JPG, PNG ou WEBP, até 5&nbsp;MB). Sem imagem, geramos um placeholder colorido.</p>
        </div>
    </div>

    {{-- Sidebar --}}
    <div class="space-y-6">
        <div class="card space-y-4 p-6">
            <div>
                <label class="mb-1 block text-sm font-medium text-neutral-700">Prática *</label>
                <select name="service_category_id" required class="w-full rounded-xl border border-neutral-300 px-3 py-2.5 text-sm focus:border-brand-400 focus:outline-none">
                    <option value="">Selecione…</option>
                    @foreach($categories as $root)
                        <optgroup label="{{ $root->name }}">
                            @foreach($root->children as $child)
                                <option value="{{ $child->id }}" @selected((int) old('service_category_id', $service->service_category_id) === $child->id)>{{ $child->name }}</option>
                            @endforeach
                        </optgroup>
                    @endforeach
                </select>
            </div>
            <div>
                <label class="mb-1 block text-sm font-medium text-neutral-700">Modalidade *</label>
                <select name="modality" class="w-full rounded-xl border border-neutral-300 px-3 py-2.5 text-sm focus:border-brand-400 focus:outline-none">
                    @foreach($modalities as $value => $label)
                        <option value="{{ $value }}" @selected(old('modality', $service->modality) === $value)>{{ $label }}</option>
                    @endforeach
                </select>
            </div>
        </div>

        <div class="card space-y-4 p-6">
            <div class="grid grid-cols-2 gap-3">
                <div>
                    <label class="mb-1 block text-sm font-medium text-neutral-700">Preço (R$) *</label>
                    <input name="price" type="number" step="0.01" min="0" value="{{ old('price', $service->price) }}" required class="w-full rounded-xl border border-neutral-300 px-3 py-2.5 text-sm focus:border-brand-400 focus:outline-none">
                </div>
                <div>
                    <label class="mb-1 block text-sm font-medium text-neutral-700">Preço "de"</label>
                    <input name="compare_at_price" type="number" step="0.01" min="0" value="{{ old('compare_at_price', $service->compare_at_price) }}" class="w-full rounded-xl border border-neutral-300 px-3 py-2.5 text-sm focus:border-brand-400 focus:outline-none">
                </div>
                <div class="col-span-2">
                    <label class="mb-1 block text-sm font-medium text-neutral-700">Máx. parcelas</label>
                    <input name="max_installments" type="number" min="1" max="12" value="{{ old('max_installments', $service->max_installments) }}" class="w-full rounded-xl border border-neutral-300 px-3 py-2.5 text-sm focus:border-brand-400 focus:outline-none">
                </div>
            </div>
            <label class="flex items-center gap-2 text-sm text-neutral-700">
                <input type="hidden" name="requires_prepayment" value="0">
                <input type="checkbox" name="requires_prepayment" value="1" @checked(old('requires_prepayment', $service->requires_prepayment)) class="rounded border-neutral-300 text-brand-600 focus:ring-brand-500">
                💳 Exigir pagamento antecipado
            </label>
            <label class="flex items-center gap-2 text-sm text-neutral-700">
                <input type="hidden" name="is_active" value="0">
                <input type="checkbox" name="is_active" value="1" @checked(old('is_active', $service->is_active)) class="rounded border-neutral-300 text-brand-600 focus:ring-brand-500">
                Publicar (visível no catálogo)
            </label>
        </div>

        <div class="card space-y-3 p-6">
            <label class="block text-sm font-medium text-neutral-700">Onde você atende este serviço</label>
            @forelse($locations as $loc)
                <label class="flex items-center gap-2 rounded-lg border border-neutral-200 px-3 py-2 text-sm has-[:checked]:border-brand-500 has-[:checked]:bg-brand-50">
                    <input type="checkbox" name="locations[]" value="{{ $loc->id }}" @checked(in_array($loc->id, old('locations', $selectedLocations))) class="rounded border-neutral-300 text-brand-600 focus:ring-brand-500">
                    <span>{{ $loc->is_online ? '💻' : '📍' }} {{ $loc->name }}</span>
                </label>
            @empty
                <p class="text-xs text-neutral-500">Você ainda não tem locais.
                    <a href="{{ $locationCreateRoute ?? route('professional.locations.create') }}" class="font-medium text-brand-700 hover:underline">Cadastrar um local</a> para receber agendamentos.
                </p>
            @endforelse
        </div>

        <div class="flex gap-2">
            <button type="submit" class="btn-brand flex-1">{{ $submitLabel }}</button>
            <a href="{{ $cancelRoute ?? route('professional.services.index') }}" class="btn-outline">Cancelar</a>
        </div>
    </div>
</form>
