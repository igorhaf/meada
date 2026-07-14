<?php

namespace App\Http\Controllers\Professional;

use App\Http\Controllers\Controller;
use App\Models\Payout;
use App\Models\Transaction;
use Illuminate\Http\Request;
use Illuminate\View\View;

class FinanceController extends Controller
{
    public function index(Request $request): View
    {
        $professionalId=$request->user()->id;$query=Transaction::whereHas('splits',fn($q)=>$q->where('professional_id',$professionalId))->when($request->query('from'),fn($q,$d)=>$q->whereDate('created_at','>=',$d))->when($request->query('to'),fn($q,$d)=>$q->whereDate('created_at','<=',$d));
        $net=(float)\App\Models\TransactionSplit::where('professional_id',$professionalId)->whereHas('transaction',fn($q)=>$q->whereIn('status',['approved','authorized'])->when($request->query('from'),fn($x,$d)=>$x->whereDate('created_at','>=',$d))->when($request->query('to'),fn($x,$d)=>$x->whereDate('created_at','<=',$d)))->sum('amount');
        $totals=['gross'=>(float)(clone $query)->whereIn('status',['approved','authorized'])->sum('gross_amount'),'house'=>(float)(clone $query)->whereIn('status',['approved','authorized'])->sum('house_amount'),'net'=>$net,'paid_out'=>(float)Payout::where('professional_id',$professionalId)->where('status','paid')->sum('amount')];
        $transactions=$query->with(['payable','customer','splits'=>fn($q)=>$q->where('professional_id',$professionalId)])->latest()->paginate(20)->withQueryString();$payouts=Payout::where('professional_id',$professionalId)->latest()->get();
        return view('professional.finance',compact('transactions','payouts','totals'));
    }
}
