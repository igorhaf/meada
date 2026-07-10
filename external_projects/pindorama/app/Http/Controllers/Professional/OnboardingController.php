<?php

namespace App\Http\Controllers\Professional;

use App\Http\Controllers\Controller;
use App\Models\User;
use Illuminate\Http\RedirectResponse;
use Illuminate\Http\Request;
use Illuminate\Support\Str;
use Illuminate\View\View;

class OnboardingController extends Controller
{
    public function create(): View|RedirectResponse
    {
        if (auth()->user()->isProfessional()) {
            return redirect()->route('professional.profile.edit');
        }

        return view('professional.onboarding');
    }

    public function store(Request $request): RedirectResponse
    {
        $user = $request->user();

        if ($user->isProfessional()) {
            return redirect()->route('professional.profile.edit');
        }

        $data = $request->validate([
            'professional_name' => ['required', 'string', 'max:255'],
        ]);

        $user->fill([
            'role' => $user->isRoot() ? 'root' : 'professional',
            'is_professional' => true,
            'professional_name' => $data['professional_name'],
            'professional_slug' => $this->uniqueSlug($data['professional_name']),
        ])->save();

        return redirect()->route('professional.profile.edit')
            ->with('status', 'Bem-vindo(a)! Complete seu perfil para começar a receber agendamentos.');
    }

    private function uniqueSlug(string $name): string
    {
        $base = Str::slug($name) ?: 'terapeuta';
        $slug = $base;
        $i = 2;
        while (User::where('professional_slug', $slug)->exists()) {
            $slug = $base . '-' . $i++;
        }

        return $slug;
    }
}
