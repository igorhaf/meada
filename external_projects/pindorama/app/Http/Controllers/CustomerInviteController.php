<?php
namespace App\Http\Controllers;
use App\Models\CustomerInvite;
use Illuminate\Http\RedirectResponse;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\Auth;
use Illuminate\Validation\Rules\Password;
use Illuminate\View\View;
class CustomerInviteController extends Controller
{
    public function show(string $token):View{$invite=$this->invite($token);return view('auth.customer-invite',compact('invite','token'));}
    public function accept(Request $request,string $token):RedirectResponse{$invite=$this->invite($token);$data=$request->validate(['password'=>['required','confirmed',Password::min(8)],'accept_terms'=>['accepted']]);$invite->customer->update(['password'=>$data['password'],'terms_accepted_at'=>now(),'privacy_accepted_at'=>now()]);$invite->update(['accepted_at'=>now()]);Auth::login($invite->customer);$request->session()->regenerate();return redirect()->route('home')->with('status','Acesso ativado.');}
    private function invite(string $token):CustomerInvite{$invite=CustomerInvite::with('customer')->where('token_hash',hash('sha256',$token))->firstOrFail();abort_if($invite->accepted_at||$invite->expires_at->isPast(),410,'Convite expirado ou utilizado.');return $invite;}
}
