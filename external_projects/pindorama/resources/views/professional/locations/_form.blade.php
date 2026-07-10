@php($input = 'w-full rounded-xl border border-neutral-300 px-4 py-2.5 text-sm focus:border-brand-500 focus:outline-none focus:ring-1 focus:ring-brand-500')

<div class="card space-y-4 p-6">
    <div>
        <label class="mb-1 block text-sm font-medium text-neutral-700">Nome do local</label>
        <input type="text" name="name" value="{{ old('name', $location->name) }}" required placeholder="Ex.: Consultório Vila Mariana" class="{{ $input }}">
        @error('name')<p class="mt-1 text-xs text-red-600">{{ $message }}</p>@enderror
    </div>

    <label class="flex items-center gap-2 rounded-xl bg-brand-50 p-3 text-sm text-neutral-700">
        <input type="checkbox" name="is_online" value="1" id="is_online" @checked(old('is_online', $location->is_online)) class="rounded border-neutral-300 text-brand-600 focus:ring-brand-500">
        <span>💻 <strong>Atendimento online</strong> (teleconsulta) — sem endereço físico</span>
    </label>

    <div id="physical_fields" class="grid gap-4 sm:grid-cols-2 {{ old('is_online', $location->is_online) ? 'hidden' : '' }}">
        <div class="sm:col-span-2">
            <label class="mb-1 block text-sm font-medium text-neutral-700">Endereço</label>
            <input type="text" name="address" value="{{ old('address', $location->address) }}" class="{{ $input }}">
        </div>
        <div>
            <label class="mb-1 block text-sm font-medium text-neutral-700">Bairro</label>
            <input type="text" name="neighborhood" value="{{ old('neighborhood', $location->neighborhood) }}" class="{{ $input }}">
        </div>
        <div>
            <label class="mb-1 block text-sm font-medium text-neutral-700">CEP</label>
            <input type="text" name="zip" value="{{ old('zip', $location->zip) }}" class="{{ $input }}">
        </div>
        <div>
            <label class="mb-1 block text-sm font-medium text-neutral-700">Cidade</label>
            <input type="text" name="city" value="{{ old('city', $location->city) }}" class="{{ $input }}">
        </div>
        <div>
            <label class="mb-1 block text-sm font-medium text-neutral-700">Estado (UF)</label>
            <input type="text" name="state" value="{{ old('state', $location->state) }}" maxlength="60" class="{{ $input }}">
        </div>
        <div class="sm:col-span-2">
            <label class="mb-1 block text-sm font-medium text-neutral-700">Complemento</label>
            <input type="text" name="complement" value="{{ old('complement', $location->complement) }}" class="{{ $input }}">
        </div>
        <div class="sm:col-span-2">
            <label class="mb-1 block text-sm font-medium text-neutral-700">Link do mapa (opcional)</label>
            <input type="url" name="map_url" value="{{ old('map_url', $location->map_url) }}" class="{{ $input }}">
        </div>
    </div>

    <label class="flex items-center gap-2 text-sm text-neutral-700">
        <input type="checkbox" name="is_active" value="1" @checked(old('is_active', $location->is_active ?? true)) class="rounded border-neutral-300 text-brand-600 focus:ring-brand-500">
        Local ativo
    </label>
</div>

<div class="mt-4 flex items-center justify-between">
    <a href="{{ route('professional.locations.index') }}" class="text-sm text-neutral-500 hover:underline">← Voltar</a>
    <button type="submit" class="btn-brand">Salvar local</button>
</div>

<script>
    document.getElementById('is_online')?.addEventListener('change', function () {
        document.getElementById('physical_fields').classList.toggle('hidden', this.checked);
    });
</script>
