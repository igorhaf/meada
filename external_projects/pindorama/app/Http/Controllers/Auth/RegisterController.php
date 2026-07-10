<?php

namespace App\Http\Controllers\Auth;

use App\Http\Controllers\Controller;
use App\Models\User;
use Illuminate\Contracts\View\View;
use Illuminate\Http\RedirectResponse;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\Auth;
use Illuminate\Support\Str;
use Illuminate\Validation\Rules\Password;

class RegisterController extends Controller
{
    public function create(): View
    {
        return view('auth.register');
    }

    public function store(Request $request): RedirectResponse
    {
        $data = $request->validate([
            'name' => ['required', 'string', 'max:255'],
            'email' => ['required', 'email', 'max:255', 'unique:users,email'],
            'password' => ['required', 'confirmed', Password::min(8)],
            'become_professional' => ['nullable', 'boolean'],
            'professional_name' => ['nullable', 'required_if:become_professional,1', 'string', 'max:255'],
        ]);

        $isProfessional = config('pindorama.professionals_enabled') && (bool) ($data['become_professional'] ?? false);

        $user = User::create([
            'name' => $data['name'],
            'email' => $data['email'],
            'password' => $data['password'],   // hashed via the model cast
            'role' => $isProfessional ? 'professional' : 'customer',
            'is_professional' => $isProfessional,
            'professional_name' => $isProfessional ? $data['professional_name'] : null,
            'professional_slug' => $isProfessional ? $this->uniqueSlug($data['professional_name']) : null,
        ]);

        Auth::login($user);
        $request->session()->regenerate();

        return redirect()->intended($isProfessional ? route('professional.dashboard') : route('home'));
    }

    private function uniqueSlug(string $name): string
    {
        $base = Str::slug($name) ?: 'terapeuta';
        $slug = $base;
        $i = 1;

        while (User::where('professional_slug', $slug)->exists()) {
            $slug = $base . '-' . (++$i);
        }

        return $slug;
    }
}
