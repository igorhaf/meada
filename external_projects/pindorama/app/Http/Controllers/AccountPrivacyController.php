<?php
namespace App\Http\Controllers;
use App\Models\User;
use App\Services\AuditService;
use Illuminate\Http\RedirectResponse;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\Auth;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Str;
use Illuminate\View\View;
use Symfony\Component\HttpFoundation\StreamedResponse;
class AccountPrivacyController extends Controller
{
    public function consentForm(): View{return view('account.consent');}
    public function consent(Request $request): RedirectResponse{$request->validate(['accept_terms'=>['accepted']]);$request->user()->update(['terms_accepted_at'=>now(),'privacy_accepted_at'=>now()]);return redirect()->route('home')->with('status','Consentimento registrado.');}
    public function show(Request $request): View{return view('account.privacy',['user'=>$request->user()]);}
    public function export(Request $request): StreamedResponse
    {
        $user=$request->user();$data=['account'=>$user->only(['name','email','created_at','terms_accepted_at','privacy_accepted_at']),'appointments'=>$user->appointmentsAsCustomer()->get()->toArray(),'event_registrations'=>$user->eventRegistrations()->with('event:id,title')->get()->toArray(),'transactions'=>\App\Models\Transaction::where('customer_id',$user->id)->get()->toArray()];
        return response()->streamDownload(fn()=>print(json_encode($data,JSON_PRETTY_PRINT|JSON_UNESCAPED_UNICODE)),'meus-dados-pindorama.json',['Content-Type'=>'application/json']);
    }
    public function destroy(Request $request,AuditService $audit): RedirectResponse
    {
        $data=$request->validate(['password'=>['required','current_password'],'confirmation'=>['required','in:EXCLUIR']]);$user=$request->user();abort_if($user->isProfessional(),403,'Perfis profissionais devem ser encerrados pelo root devido aos registros financeiros e agendas vinculados.');
        $audit->record('customer.anonymized',$user,$user->only(['name','email']),['status'=>'anonymized']);
        DB::transaction(function()use($user){$user->appointmentsAsCustomer()->update(['patient_name'=>'Usuário removido','patient_email'=>null,'patient_phone'=>null,'notes'=>null]);$user->eventRegistrations()->update(['participant_name'=>'Usuário removido','participant_email'=>null,'participant_phone'=>null]);\App\Models\AccessPass::where('holder_id',$user->id)->update(['holder_name'=>'Usuário removido','status'=>'cancelled']);$user->update(['name'=>'Usuário removido','email'=>'deleted+'.$user->id.'+'.time().'@anon.invalid','password'=>Str::random(64),'google_id'=>null,'avatar'=>null,'phone'=>null,'whatsapp'=>null,'is_active'=>false]);});
        Auth::logout();$request->session()->invalidate();$request->session()->regenerateToken();return redirect()->route('home')->with('status','Sua conta e seus dados pessoais foram anonimizados.');
    }
}
