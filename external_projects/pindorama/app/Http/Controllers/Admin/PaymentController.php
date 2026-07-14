<?php

namespace App\Http\Controllers\Admin;

use App\Http\Controllers\Controller;
use App\Models\Payout;
use App\Models\PlatformCharge;
use App\Models\Transaction;
use App\Models\TransactionSplit;
use App\Models\User;
use App\Services\AuditService;
use App\Services\MercadoPagoService;
use Illuminate\Contracts\View\View;
use Illuminate\Http\Request;
use Illuminate\Http\RedirectResponse;
use Illuminate\Support\Str;
use Symfony\Component\HttpFoundation\StreamedResponse;
use Illuminate\Validation\ValidationException;
use Barryvdh\DomPDF\Facade\Pdf;

class PaymentController extends Controller
{
    public function index(Request $request, MercadoPagoService $mp): View
    {
        $publicKey = $mp->publicKey();

        $config = [
            'enabled' => $mp->enabled(),
            'environment' => $mp->environmentLabel(),
            'sandbox' => $mp->isSandbox(),
            'public_key' => $publicKey ? Str::limit($publicKey, 12, '…') : null,
        ];

        $paid = ['approved', 'authorized'];
        $query = $this->filtered($request);
        $totals = [
            'approved' => (clone $query)->whereIn('status', $paid)->count(),
            'pending' => (clone $query)->whereIn('status', ['pending','in_process'])->count(),
            'rejected' => (clone $query)->whereIn('status', ['rejected', 'cancelled','refunded'])->count(),
            'revenue' => (float) (clone $query)->whereIn('status', $paid)->sum('gross_amount'),
            'commission' => (float) (clone $query)->whereIn('status', $paid)->sum('house_amount'),
            'professional' => $this->professionalTotal($request, clone $query),
            'platform_charges' => (float) PlatformCharge::whereIn('status',['paid','waived'])->sum('amount'),
            'paid_out' => (float) Payout::where('status','paid')->sum('amount'),
        ];

        $transactions = $query->with('professional','customer','payable')->latest()->paginate(20)->withQueryString();
        $payouts=Payout::with('professional')->latest()->limit(20)->get();
        $professionals=User::where('is_professional',true)->where('role','!=','root')->orderBy('professional_name')->get();

        return view('admin.payments', compact('config', 'totals', 'transactions','payouts','professionals'));
    }

    public function payout(Request $request, AuditService $audit): RedirectResponse
    {
        $data=$request->validate(['professional_id'=>['required','exists:users,id'],'amount'=>['required','numeric','min:0.01'],'period_start'=>['required','date'],'period_end'=>['required','date','after_or_equal:period_start'],'notes'=>['nullable','string','max:2000']]);
        $earned=(float)\App\Models\TransactionSplit::where('professional_id',$data['professional_id'])->whereHas('transaction',fn($q)=>$q->whereIn('status',['approved','authorized'])->whereDate('created_at','>=',$data['period_start'])->whereDate('created_at','<=',$data['period_end']))->sum('amount');
        $committed=(float)Payout::where('professional_id',$data['professional_id'])->whereIn('status',['pending','paid'])->where('period_start','<=',$data['period_end'])->where('period_end','>=',$data['period_start'])->sum('amount');
        if((float)$data['amount']>round($earned-$committed,2))throw ValidationException::withMessages(['amount'=>'O valor excede o saldo disponível no período ('.money(max(0,$earned-$committed)).').']);
        $payout=Payout::create($data+['reference'=>'REP-'.strtoupper(Str::random(8)),'status'=>'pending','created_by'=>$request->user()->id]);
        $audit->record('payout.created',$payout,[],$payout->toArray());
        return back()->with('status','Repasse registrado como pendente.');
    }

    public function payoutStatus(Request $request,Payout $payout,AuditService $audit): RedirectResponse
    {
        $data=$request->validate(['status'=>['required','in:pending,paid,cancelled']]);$before=$payout->toArray();$payout->update(['status'=>$data['status'],'paid_at'=>$data['status']==='paid'?now():null]);$audit->record('payout.status_changed',$payout,$before,$payout->toArray());return back()->with('status','Repasse atualizado.');
    }

    public function export(Request $request): StreamedResponse
    {
        $rows=$this->filtered($request)->with('professional','customer')->orderBy('created_at')->get();
        return response()->streamDownload(function()use($rows){$out=fopen('php://output','w');fputcsv($out,['Referencia','Data','Tipo','Cliente','Profissional','Bruto','Casa','Profissional','Status','Metodo']);foreach($rows as $tx)fputcsv($out,[$tx->reference,$tx->created_at,$tx->payable_type,$tx->customer?->name,$tx->professional?->display_name,$tx->gross_amount,$tx->house_amount,$tx->professional_amount,$tx->status,$tx->payment_method]);fclose($out);},'financeiro-pindorama.csv',['Content-Type'=>'text/csv; charset=UTF-8']);
    }

    public function pdf(Request $request): \Symfony\Component\HttpFoundation\Response
    {
        $query = $this->filtered($request);
        $paid = ['approved', 'authorized'];
        $rows = (clone $query)->with('professional', 'customer', 'payable')->orderBy('created_at')->get();
        $totals = [
            'revenue' => (float) (clone $query)->whereIn('status', $paid)->sum('gross_amount'),
            'house' => (float) (clone $query)->whereIn('status', $paid)->sum('house_amount'),
            'professional' => $this->professionalTotal($request, clone $query),
        ];
        $professional = $request->query('professional_id')
            ? User::find($request->query('professional_id'))
            : null;

        return Pdf::loadView('admin.payments-pdf', compact('rows', 'totals', 'professional'))
            ->setPaper('a4', 'landscape')
            ->download('financeiro-pindorama-'.now()->format('Y-m-d').'.pdf');
    }

    private function filtered(Request $request)
    {
        return Transaction::query()->when($request->query('professional_id'),fn($q,$id)=>$q->whereHas('splits',fn($split)=>$split->where('professional_id',$id)))->when($request->query('status'),fn($q,$status)=>$q->where('status',$status))->when($request->query('from'),fn($q,$date)=>$q->whereDate('created_at','>=',$date))->when($request->query('to'),fn($q,$date)=>$q->whereDate('created_at','<=',$date))->when($request->query('type'),fn($q,$type)=>$q->where('payable_type',$type==='event'?\App\Models\EventRegistration::class:\App\Models\Appointment::class));
    }

    private function professionalTotal(Request $request, $query): float
    {
        if ($professionalId = $request->query('professional_id')) {
            return (float) TransactionSplit::where('professional_id', $professionalId)
                ->whereHas('transaction', fn ($transaction) => $transaction
                    ->whereIn('status', ['approved', 'authorized'])
                    ->whereIn('id', (clone $query)->select('id')))
                ->sum('amount');
        }

        return (float) $query->whereIn('status', ['approved', 'authorized'])->sum('professional_amount');
    }
}
