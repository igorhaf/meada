<section class="admin-card">
    <div class="admin-grid-2">
        <div class="field">
            <label for="tag_label">Etiqueta (ex.: Formação, Treinamento, Workshop)</label>
            <input class="input" id="tag_label" name="tag_label"
                   value="{{ old('tag_label', $course?->tag_label) }}" required>
        </div>
        <div class="field">
            <label for="tag_style">Cor da etiqueta</label>
            <select class="input" id="tag_style" name="tag_style">
                <option value="accent" @selected(old('tag_style', $course?->tag_style ?? 'accent') === 'accent')>Terracota</option>
                <option value="accent-2" @selected(old('tag_style', $course?->tag_style) === 'accent-2')>Sálvia</option>
                <option value="neutral" @selected(old('tag_style', $course?->tag_style) === 'neutral')>Neutra</option>
            </select>
        </div>
    </div>
    <div class="field">
        <label for="title">Título</label>
        <input class="input" id="title" name="title"
               value="{{ old('title', $course?->title) }}" required>
    </div>
    <div class="field">
        <label for="description">Descrição</label>
        <textarea class="input" id="description" name="description" rows="4" required>{{ old('description', $course?->description) }}</textarea>
    </div>
    <div class="field">
        <label for="meta_info">Informações extras (ex.: "Presencial + online · Próxima turma em março")</label>
        <input class="input" id="meta_info" name="meta_info"
               value="{{ old('meta_info', $course?->meta_info) }}">
    </div>
    <label class="admin-check">
        <input type="checkbox" name="is_active" value="1"
               @checked(old('is_active', $course?->is_active ?? true))>
        Ativo no site
    </label>
</section>
