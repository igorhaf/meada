@extends('layouts.admin')

@section('title', 'Configurações')
@section('heading', 'Configurações do site')

@section('content')
<form method="POST" action="{{ route('admin.settings.update') }}"
      enctype="multipart/form-data" class="admin-form">
    @csrf
    @method('PUT')

    <section class="admin-card">
        <h2>Contato</h2>
        <div class="admin-grid-2">
            <div class="field">
                <label for="contact_whatsapp">WhatsApp</label>
                <input class="input" id="contact_whatsapp" name="contact_whatsapp"
                       value="{{ old('contact_whatsapp', $settings['contact.whatsapp'] ?? '') }}" required>
            </div>
            <div class="field">
                <label for="contact_instagram">Instagram</label>
                <input class="input" id="contact_instagram" name="contact_instagram"
                       value="{{ old('contact_instagram', $settings['contact.instagram'] ?? '') }}" required>
            </div>
            <div class="field">
                <label for="contact_email">E-mail</label>
                <input class="input" id="contact_email" name="contact_email"
                       value="{{ old('contact_email', $settings['contact.email'] ?? '') }}" required>
            </div>
        </div>
    </section>

    <section class="admin-card">
        <h2>Herói</h2>
        <div class="field">
            <label for="hero_kicker">Kicker (linha pequena acima do título)</label>
            <input class="input" id="hero_kicker" name="hero_kicker"
                   value="{{ old('hero_kicker', $settings['hero.kicker'] ?? '') }}" required>
        </div>
        <div class="field">
            <label for="hero_title">Título</label>
            <input class="input" id="hero_title" name="hero_title"
                   value="{{ old('hero_title', $settings['hero.title'] ?? '') }}" required>
        </div>
        <div class="field">
            <label for="hero_subtitle">Subtítulo</label>
            <textarea class="input" id="hero_subtitle" name="hero_subtitle" rows="3" required>{{ old('hero_subtitle', $settings['hero.subtitle'] ?? '') }}</textarea>
        </div>
        <div class="admin-grid-2">
            <div class="field">
                <label for="hero_cta_primary">Botão principal</label>
                <input class="input" id="hero_cta_primary" name="hero_cta_primary"
                       value="{{ old('hero_cta_primary', $settings['hero.cta_primary'] ?? '') }}" required>
            </div>
            <div class="field">
                <label for="hero_cta_secondary">Botão secundário</label>
                <input class="input" id="hero_cta_secondary" name="hero_cta_secondary"
                       value="{{ old('hero_cta_secondary', $settings['hero.cta_secondary'] ?? '') }}" required>
            </div>
        </div>
        <div class="admin-photo-field">
            <img class="admin-img-preview" src="{{ asset($settings['hero.photo'] ?? 'images/hero.png') }}" alt="Foto atual do herói">
            <div class="field">
                <label for="hero_photo">Trocar foto do herói (PNG/JPG até 4&nbsp;MB)</label>
                <input class="input" type="file" id="hero_photo" name="hero_photo" accept="image/*">
            </div>
        </div>
    </section>

    <section class="admin-card">
        <h2>Títulos das seções</h2>
        <div class="admin-grid-2">
            <div class="field">
                <label for="services_kicker">Kicker — O que fazemos</label>
                <input class="input" id="services_kicker" name="services_kicker"
                       value="{{ old('services_kicker', $settings['services.kicker'] ?? '') }}" required>
            </div>
            <div class="field">
                <label for="practices_kicker">Kicker — Práticas integrativas</label>
                <input class="input" id="practices_kicker" name="practices_kicker"
                       value="{{ old('practices_kicker', $settings['practices.kicker'] ?? '') }}" required>
            </div>
            <div class="field">
                <label for="practices_title">Título — Práticas integrativas</label>
                <input class="input" id="practices_title" name="practices_title"
                       value="{{ old('practices_title', $settings['practices.title'] ?? '') }}" required>
            </div>
            <div class="field">
                <label for="courses_kicker">Kicker — Cursos e treinamentos</label>
                <input class="input" id="courses_kicker" name="courses_kicker"
                       value="{{ old('courses_kicker', $settings['courses.kicker'] ?? '') }}" required>
            </div>
            <div class="field">
                <label for="courses_title">Título — Cursos e treinamentos</label>
                <input class="input" id="courses_title" name="courses_title"
                       value="{{ old('courses_title', $settings['courses.title'] ?? '') }}" required>
            </div>
        </div>
    </section>

    <section class="admin-card">
        <h2>Quem somos</h2>
        <div class="admin-grid-2">
            <div class="field">
                <label for="about_kicker">Kicker</label>
                <input class="input" id="about_kicker" name="about_kicker"
                       value="{{ old('about_kicker', $settings['about.kicker'] ?? '') }}" required>
            </div>
            <div class="field">
                <label for="about_title">Título</label>
                <input class="input" id="about_title" name="about_title"
                       value="{{ old('about_title', $settings['about.title'] ?? '') }}" required>
            </div>
        </div>
        <div class="field">
            <label for="about_text">Texto</label>
            <textarea class="input" id="about_text" name="about_text" rows="5" required>{{ old('about_text', $settings['about.text'] ?? '') }}</textarea>
        </div>
        <div class="field">
            <label for="about_cta">Texto do botão</label>
            <input class="input" id="about_cta" name="about_cta"
                   value="{{ old('about_cta', $settings['about.cta'] ?? '') }}" required>
        </div>
        <div class="admin-photo-field">
            <img class="admin-img-preview" src="{{ asset($settings['about.photo'] ?? 'images/equipe.png') }}" alt="Foto atual da equipe">
            <div class="field">
                <label for="about_photo">Trocar foto da equipe (PNG/JPG até 4&nbsp;MB)</label>
                <input class="input" type="file" id="about_photo" name="about_photo" accept="image/*">
            </div>
        </div>
    </section>

    <section class="admin-card">
        <h2>Bloco de contato</h2>
        <div class="field">
            <label for="contact_title">Título</label>
            <input class="input" id="contact_title" name="contact_title"
                   value="{{ old('contact_title', $settings['contact.title'] ?? '') }}" required>
        </div>
        <div class="field">
            <label for="contact_subtitle">Subtítulo</label>
            <input class="input" id="contact_subtitle" name="contact_subtitle"
                   value="{{ old('contact_subtitle', $settings['contact.subtitle'] ?? '') }}" required>
        </div>
    </section>

    <section class="admin-card">
        <h2>Rodapé</h2>
        <div class="field">
            <label for="footer_text">Texto do rodapé</label>
            <input class="input" id="footer_text" name="footer_text"
                   value="{{ old('footer_text', $settings['footer.text'] ?? '') }}" required>
        </div>
    </section>

    <div class="admin-form-actions">
        <button type="submit" class="btn btn-primary">Salvar configurações</button>
    </div>
</form>
@endsection
