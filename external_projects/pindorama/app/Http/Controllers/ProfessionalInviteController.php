<?php

namespace App\Http\Controllers;

use App\Models\ProfessionalInvite;
use Illuminate\Http\RedirectResponse;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\Auth;
use Illuminate\Validation\Rules\Password;
use Illuminate\View\View;

class ProfessionalInviteController extends Controller
{
    public function show(string $token): View
    {
        $invite = $this->invite($token);
        return view('auth.professional-invite', compact('invite', 'token'));
    }

    public function accept(Request $request, string $token): RedirectResponse
    {
        $invite = $this->invite($token);
        $data = $request->validate([
            'password' => ['required', 'confirmed', Password::min(8)],
            'accept_terms' => ['accepted'],
        ]);
        $invite->professional->update([
            'password' => $data['password'],
            'is_active' => true,
            'terms_accepted_at' => now(),
            'privacy_accepted_at' => now(),
        ]);
        $invite->update(['accepted_at' => now()]);
        ProfessionalInvite::where('professional_id', $invite->professional_id)->whereNull('accepted_at')->whereKeyNot($invite->id)->delete();
        Auth::login($invite->professional);
        $request->session()->regenerate();
        return redirect()->route('professional.profile.edit')->with('status', 'Acesso ativado. Complete seu perfil profissional.');
    }

    private function invite(string $token): ProfessionalInvite
    {
        $invite = ProfessionalInvite::with('professional')->where('token_hash', hash('sha256', $token))->firstOrFail();
        abort_if($invite->accepted_at || $invite->expires_at->isPast(), 410, 'Este convite expirou ou já foi utilizado.');
        return $invite;
    }
}
