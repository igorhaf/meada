<?php

namespace App\Http\Controllers;

use App\Models\AccessPass;
use App\Models\Appointment;
use App\Models\EventRegistration;
use App\Services\AuditService;
use Illuminate\Http\RedirectResponse;
use Illuminate\Http\Request;
use Illuminate\View\View;
use Illuminate\Support\Facades\URL;

class AccessPassController extends Controller
{
    public function lookupForm(): View { return view('passes.lookup'); }
    public function lookup(Request $request): RedirectResponse
    {
        $data=$request->validate(['code'=>['required','string','max:32']]);$pass=AccessPass::where('public_code',strtoupper(preg_replace('/\s+/','',$data['code'])))->first();
        if(!$pass||!$this->canOperate($request,$pass))return back()->with('error','Passaporte não encontrado ou sem permissão para este operador.');
        return redirect(URL::temporarySignedRoute('passes.show',now()->addMinutes(30),['pass'=>$pass]));
    }
    public function show(Request $request, AccessPass $pass): View
    {
        abort_unless($request->hasValidSignature() || $this->canOperate($request, $pass) || $pass->holder_id === $request->user()?->id, 403);
        $pass->load('passable');

        return view('passes.show', ['pass' => $pass, 'canCheckIn' => $this->canOperate($request, $pass)]);
    }

    public function checkIn(Request $request, AccessPass $pass, AuditService $audit): RedirectResponse
    {
        abort_unless($this->canOperate($request, $pass), 403);
        if (! $pass->isValid()) return back()->with('error', 'Este passaporte não está válido para check-in.');
        if ($pass->valid_from && now()->lt($pass->valid_from)) return back()->with('error', 'Este passaporte ainda não está dentro da janela de validade.');

        $pass->update(['status' => 'used', 'used_at' => now(), 'checked_in_by' => $request->user()->id, 'check_in_location' => $request->input('location')]);
        if ($pass->passable instanceof EventRegistration) {
            $pass->passable->update(['status' => 'attended', 'checked_in_at' => now(), 'checked_in_by' => $request->user()->id]);
        }
        $audit->record('access_pass.checked_in', $pass);

        return back()->with('status', 'Check-in confirmado.');
    }

    private function canOperate(Request $request, AccessPass $pass): bool
    {
        $user = $request->user();
        if (! $user) return false;
        if ($user->isRoot()) return true;

        $pass->loadMissing('passable');
        if ($pass->passable instanceof Appointment) return $pass->passable->professional_id === $user->id;
        if ($pass->passable instanceof EventRegistration) {
            return $pass->passable->event()->whereHas('instructors', fn ($q) => $q->whereKey($user->id)->where('event_professional.can_manage_attendance', true))->exists();
        }

        return false;
    }
}
