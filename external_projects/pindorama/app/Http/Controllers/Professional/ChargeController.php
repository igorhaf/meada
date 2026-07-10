<?php

namespace App\Http\Controllers\Professional;

use App\Http\Controllers\Controller;
use App\Models\PlatformCharge;
use App\Services\MercadoPagoService;
use Illuminate\Http\RedirectResponse;
use Illuminate\Http\Request;
use Illuminate\View\View;

class ChargeController extends Controller
{
    public function index(Request $request): View
    {
        $charges = $request->user()->charges()->latest()->get();

        return view('professional.charges.index', compact('charges'));
    }

    /**
     * Paga uma cobrança da plataforma. Com o MP ligado o ideal é o Brick; aqui,
     * para o MVP/dev (MP desligado), marca como paga (simulado). A conta MP é a da
     * plataforma — o terapeuta paga a Pindorama.
     */
    public function pay(Request $request, PlatformCharge $charge, MercadoPagoService $mp): RedirectResponse
    {
        abort_unless($charge->professional_id === $request->user()->id, 403);

        if ($charge->isPaid()) {
            return back();
        }

        $charge->update([
            'status' => 'paid',
            'paid_at' => now(),
            'payment_method' => $mp->enabled() ? 'mercadopago' : 'simulado',
        ]);

        return back()->with('status', 'Cobrança paga.');
    }
}
