<?php

namespace App\Http\Controllers\Professional;

use App\Http\Controllers\Controller;
use App\Models\ServiceCategory;
use App\Models\User;
use Illuminate\Http\RedirectResponse;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\Storage;
use Illuminate\Support\Str;
use Illuminate\View\View;

class ProfileController extends Controller
{
    public function edit(Request $request): View
    {
        $user = $request->user();
        $user->load('specialties');

        return view('professional.profile.edit', [
            'user' => $user,
            'categories' => ServiceCategory::active()->orderBy('name')->get(),
            'selectedSpecialties' => $user->specialties->pluck('id')->all(),
        ]);
    }

    public function update(Request $request): RedirectResponse
    {
        $user = $request->user();

        $data = $request->validate([
            'professional_name' => ['required', 'string', 'max:255'],
            'headline' => ['nullable', 'string', 'max:255'],
            'bio' => ['nullable', 'string', 'max:5000'],
            'city' => ['nullable', 'string', 'max:120'],
            'state' => ['nullable', 'string', 'max:60'],
            'phone' => ['nullable', 'string', 'max:40'],
            'whatsapp' => ['nullable', 'string', 'max:40'],
            'instagram_url' => ['nullable', 'url', 'max:255'],
            'facebook_url' => ['nullable', 'url', 'max:255'],
            'youtube_url' => ['nullable', 'url', 'max:255'],
            'website_url' => ['nullable', 'url', 'max:255'],
            'registration_council' => ['nullable', 'string', 'max:120'],
            'avatar' => ['nullable', 'image', 'mimes:jpeg,jpg,png,webp', 'max:5120'],
            'banner' => ['nullable', 'image', 'mimes:jpeg,jpg,png,webp', 'max:5120'],
            'specialties' => ['nullable', 'array'],
            'specialties.*' => ['integer', 'exists:service_categories,id'],
        ]);

        $user->fill([
            'professional_name' => $data['professional_name'],
            'headline' => $data['headline'] ?? null,
            'bio' => $data['bio'] ?? null,
            'city' => $data['city'] ?? null,
            'state' => $data['state'] ?? null,
            'phone' => $data['phone'] ?? null,
            'whatsapp' => $data['whatsapp'] ?? null,
            'instagram_url' => $data['instagram_url'] ?? null,
            'facebook_url' => $data['facebook_url'] ?? null,
            'youtube_url' => $data['youtube_url'] ?? null,
            'website_url' => $data['website_url'] ?? null,
            'registration_council' => $data['registration_council'] ?? null,
        ]);

        // Slug: gera na primeira vez a partir do nome.
        if (! $user->professional_slug) {
            $user->professional_slug = $this->uniqueSlug($data['professional_name'], $user->id);
        }

        if ($request->hasFile('avatar')) {
            $user->avatar_path = $this->replaceUpload($request->file('avatar'), 'professionals/avatars', $user->avatar_path);
        }
        if ($request->hasFile('banner')) {
            $user->banner_path = $this->replaceUpload($request->file('banner'), 'professionals/banners', $user->banner_path);
        }

        $user->save();

        // Denormaliza o nome do terapeuta nos serviços (usado em cards/facetas).
        $user->services()->update([
            'professional_name' => $user->professional_name,
            'professional_city' => $user->city,
            'professional_state' => $user->state,
        ]);

        $user->specialties()->sync($data['specialties'] ?? []);

        return redirect()->route('professional.profile.edit')->with('status', 'Perfil atualizado com sucesso.');
    }

    private function replaceUpload($file, string $dir, ?string $previous): string
    {
        $path = $file->store($dir, 'public');
        if ($previous && str_starts_with($previous, '/storage/')) {
            Storage::disk('public')->delete(str_replace('/storage/', '', $previous));
        }

        return '/storage/' . $path;
    }

    private function uniqueSlug(string $name, int $ignoreId): string
    {
        $base = Str::slug($name) ?: 'terapeuta';
        $slug = $base;
        $i = 2;
        while (User::where('professional_slug', $slug)->where('id', '!=', $ignoreId)->exists()) {
            $slug = $base . '-' . $i++;
        }

        return $slug;
    }
}
