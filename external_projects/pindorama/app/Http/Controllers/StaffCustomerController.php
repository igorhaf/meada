<?php
namespace App\Http\Controllers;
use App\Models\CustomerInvite;
use App\Models\User;
use App\Notifications\CustomerInviteNotification;
use App\Services\AuditService;
use Illuminate\Http\RedirectResponse;
use Illuminate\Http\Request;
use Illuminate\Support\Str;
use Illuminate\View\View;
class StaffCustomerController extends Controller
{
    public function create(Request $request):View{return view('staff.customer-form',['root'=>$request->user()->isRoot()]);}
    public function store(Request $request,AuditService $audit):RedirectResponse{$data=$request->validate(['name'=>['required','string','max:255'],'email'=>['required','email','unique:users,email'],'phone'=>['nullable','string','max:40']]);$customer=User::create(['name'=>$data['name'],'email'=>$data['email'],'phone'=>$data['phone']??null,'password'=>Str::random(48),'role'=>'customer','is_professional'=>false,'is_active'=>true]);$token=Str::random(64);CustomerInvite::create(['customer_id'=>$customer->id,'token_hash'=>hash('sha256',$token),'expires_at'=>now()->addHours(72),'created_by'=>$request->user()->id]);$url=route('customer-invites.show',$token);try{$customer->notify(new CustomerInviteNotification($url));}catch(\Throwable $e){report($e);}$audit->record('customer.created_by_staff',$customer,[],$customer->only(['name','email']));return back()->with('status','Cliente criado e convite gerado.')->with('invite_url',$url);}
}
