@extends('layouts.site')

@php
    /* wa.me exige só dígitos; número nacional ganha o DDI 55 — mas se o admin
       já salvou com +55 (>= 12 dígitos começando em 55), não duplica o DDI.
       DDD 55 (Santa Maria) tem 10-11 dígitos e ainda recebe o prefixo. */
    $whatsapp = $settings['contact.whatsapp'] ?? '';
    $whatsappDigits = preg_replace('/\D+/', '', $whatsapp);
    if (! (str_starts_with($whatsappDigits, '55') && strlen($whatsappDigits) >= 12)) {
        $whatsappDigits = '55' . $whatsappDigits;
    }
    $whatsappUrl = 'https://wa.me/' . $whatsappDigits;
    $instagram = $settings['contact.instagram'] ?? '';
    $instagramUrl = 'https://instagram.com/' . ltrim($instagram, '@');
    $email = $settings['contact.email'] ?? '';
@endphp

@section('content')
    <nav class="site-nav">
        <a href="#o-que-fazemos">Acompanhamento</a>
        <a href="#praticas">Práticas</a>
        <img class="site-nav-logo" src="{{ asset('images/logo-mark.png') }}" alt="Germinar">
        <a href="#cursos">Cursos</a>
        <a href="#contato">Contato</a>
    </nav>

    <header class="hero">
        <div>
            <span class="section-kicker">{{ $settings['hero.kicker'] ?? '' }}</span>
            <h1 class="hero-title">{{ $settings['hero.title'] ?? '' }}</h1>
            <p class="hero-subtitle">{{ $settings['hero.subtitle'] ?? '' }}</p>
            <div class="hero-actions">
                <a class="btn btn-primary" href="{{ $whatsappUrl }}" target="_blank" rel="noopener">{{ $settings['hero.cta_primary'] ?? '' }}</a>
                <a class="btn btn-ghost" href="#cursos">{{ $settings['hero.cta_secondary'] ?? '' }}</a>
            </div>
        </div>
        <div class="hero-photo">
            <img src="{{ asset($settings['hero.photo'] ?? 'images/hero.png') }}" alt="Doula da Germinar sorrindo">
        </div>
    </header>

    <section id="o-que-fazemos" class="services">
        <span class="section-kicker">{{ $settings['services.kicker'] ?? '' }}</span>
        @foreach ($services as $service)
            <div class="service-row">
                <h2 class="service-title">
                    <span class="service-dot{{ $service->dot_color === 'accent-2' ? ' service-dot--accent-2' : '' }}"></span>
                    {{ $service->title }}
                </h2>
                <p class="service-desc">{{ $service->description }}</p>
            </div>
        @endforeach
    </section>

    <section id="praticas" class="practices">
        <div class="practices-inner">
            <span class="section-kicker">{{ $settings['practices.kicker'] ?? '' }}</span>
            <h2 class="section-title">{{ $settings['practices.title'] ?? '' }}</h2>
            <div class="practices-grid">
                @foreach ($practices as $practice)
                    <div class="practice-card">
                        <h3>{{ $practice->title }}</h3>
                        <p>{{ $practice->description }}</p>
                    </div>
                @endforeach
            </div>
        </div>
    </section>

    <section id="cursos" class="courses">
        <span class="section-kicker">{{ $settings['courses.kicker'] ?? '' }}</span>
        <h2 class="section-title">{{ $settings['courses.title'] ?? '' }}</h2>
        <div class="courses-grid">
            @foreach ($courses as $course)
                <div class="course-card">
                    <span class="tag tag-{{ $course->tag_style }}">{{ $course->tag_label }}</span>
                    <h3>{{ $course->title }}</h3>
                    <p>{{ $course->description }}</p>
                    @if ($course->meta_info)
                        <span class="course-meta">{{ $course->meta_info }}</span>
                    @endif
                </div>
            @endforeach
        </div>
    </section>

    <section id="quem-somos" class="about">
        <div class="about-grid">
            <figure class="about-figure washed">
                <img src="{{ asset($settings['about.photo'] ?? 'images/equipe.png') }}" alt="Equipe da Germinar">
            </figure>
            <div>
                <span class="section-kicker">{{ $settings['about.kicker'] ?? '' }}</span>
                <h2 class="about-title">{{ $settings['about.title'] ?? '' }}</h2>
                <p class="about-text">{{ $settings['about.text'] ?? '' }}</p>
                <div class="about-cta">
                    <a class="btn btn-ghost" href="#contato">{{ $settings['about.cta'] ?? '' }}</a>
                </div>
            </div>
        </div>
    </section>

    <section id="contato" class="contact">
        <div class="contact-patch">
            <h3 class="contact-title">{{ $settings['contact.title'] ?? '' }}</h3>
            <p class="contact-subtitle">{{ $settings['contact.subtitle'] ?? '' }}</p>
            <div class="contact-actions">
                <a class="btn btn-primary" href="{{ $whatsappUrl }}" target="_blank" rel="noopener">WhatsApp {{ $whatsapp }}</a>
                <a class="btn btn-secondary" href="{{ $instagramUrl }}" target="_blank" rel="noopener">{{ $instagram }}</a>
            </div>
        </div>
        <footer class="site-footer">
            {{ $settings['footer.text'] ?? '' }} · <a href="mailto:{{ $email }}">{{ $email }}</a>
        </footer>
    </section>
@endsection
