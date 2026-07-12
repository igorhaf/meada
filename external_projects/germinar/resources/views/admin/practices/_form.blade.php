<section class="admin-card">
    <div class="field">
        <label for="title">Título</label>
        <input class="input" id="title" name="title"
               value="{{ old('title', $practice?->title) }}" required>
    </div>
    <div class="field">
        <label for="description">Descrição</label>
        <textarea class="input" id="description" name="description" rows="4" required>{{ old('description', $practice?->description) }}</textarea>
    </div>
    <label class="admin-check">
        <input type="checkbox" name="is_active" value="1"
               @checked(old('is_active', $practice?->is_active ?? true))>
        Ativa no site
    </label>
</section>
