<section class="admin-card">
    <div class="field">
        <label for="title">Título</label>
        <input class="input" id="title" name="title"
               value="{{ old('title', $service?->title) }}" required>
    </div>
    <div class="field">
        <label for="description">Descrição</label>
        <textarea class="input" id="description" name="description" rows="4" required>{{ old('description', $service?->description) }}</textarea>
    </div>
    <div class="field">
        <label for="dot_color">Cor do marcador</label>
        <select class="input" id="dot_color" name="dot_color">
            <option value="accent" @selected(old('dot_color', $service?->dot_color ?? 'accent') === 'accent')>Terracota</option>
            <option value="accent-2" @selected(old('dot_color', $service?->dot_color) === 'accent-2')>Sálvia</option>
        </select>
    </div>
    <label class="admin-check">
        <input type="checkbox" name="is_active" value="1"
               @checked(old('is_active', $service?->is_active ?? true))>
        Ativo no site
    </label>
</section>
