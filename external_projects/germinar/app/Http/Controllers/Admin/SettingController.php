<?php

namespace App\Http\Controllers\Admin;

use App\Http\Controllers\Controller;
use App\Models\Setting;
use Illuminate\Http\RedirectResponse;
use Illuminate\Http\Request;
use Illuminate\View\View;

class SettingController extends Controller
{
    /**
     * Mapa explícito input => chave da setting (o "." vira "_" no name do
     * input; o mapa evita ambiguidade em chaves que já contêm "_").
     */
    private const TEXT_FIELDS = [
        'contact_whatsapp' => 'contact.whatsapp',
        'contact_instagram' => 'contact.instagram',
        'contact_email' => 'contact.email',
        'hero_kicker' => 'hero.kicker',
        'hero_title' => 'hero.title',
        'hero_subtitle' => 'hero.subtitle',
        'hero_cta_primary' => 'hero.cta_primary',
        'hero_cta_secondary' => 'hero.cta_secondary',
        'services_kicker' => 'services.kicker',
        'practices_kicker' => 'practices.kicker',
        'practices_title' => 'practices.title',
        'courses_kicker' => 'courses.kicker',
        'courses_title' => 'courses.title',
        'about_kicker' => 'about.kicker',
        'about_title' => 'about.title',
        'about_text' => 'about.text',
        'about_cta' => 'about.cta',
        'contact_title' => 'contact.title',
        'contact_subtitle' => 'contact.subtitle',
        'footer_text' => 'footer.text',
    ];

    private const IMAGE_FIELDS = [
        'hero_photo' => 'hero.photo',
        'about_photo' => 'about.photo',
    ];

    public function edit(): View
    {
        return view('admin.settings.edit', [
            'settings' => Setting::all_map(),
        ]);
    }

    public function update(Request $request): RedirectResponse
    {
        $rules = [];

        foreach (array_keys(self::TEXT_FIELDS) as $input) {
            $rules[$input] = ['required', 'string'];
        }

        foreach (array_keys(self::IMAGE_FIELDS) as $input) {
            $rules[$input] = ['nullable', 'image', 'max:4096'];
        }

        $validated = $request->validate($rules);

        foreach (self::TEXT_FIELDS as $input => $key) {
            Setting::set($key, $validated[$input]);
        }

        foreach (self::IMAGE_FIELDS as $input => $key) {
            if ($request->hasFile($input)) {
                $path = $request->file($input)->store('uploads', 'public');
                Setting::set($key, 'storage/'.$path);
            }
        }

        return back()->with('status', 'Configurações salvas.');
    }
}
