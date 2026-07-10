<?php

namespace App\Http\Controllers\Admin;

use App\Http\Controllers\Controller;
use App\Models\Appointment;
use App\Services\MercadoPagoService;
use Illuminate\Contracts\View\View;
use Illuminate\Support\Str;

class PaymentController extends Controller
{
    public function index(MercadoPagoService $mp): View
    {
        $publicKey = $mp->publicKey();

        $config = [
            'enabled' => $mp->enabled(),
            'environment' => $mp->environmentLabel(),
            'sandbox' => $mp->isSandbox(),
            'public_key' => $publicKey ? Str::limit($publicKey, 12, '…') : null,
        ];

        $paid = ['approved', 'authorized'];
        $totals = [
            'approved' => Appointment::whereIn('payment_status', $paid)->count(),
            'pending' => Appointment::where('payment_status', 'pending')->count(),
            'rejected' => Appointment::whereIn('payment_status', ['rejected', 'cancelled'])->count(),
            'revenue' => (float) Appointment::whereIn('payment_status', $paid)->sum('total'),
            'commission' => (float) Appointment::whereIn('payment_status', $paid)->sum('commission_amount'),
        ];

        $appointments = Appointment::with('professional')->latest()->paginate(15);

        return view('admin.payments', compact('config', 'totals', 'appointments'));
    }
}
