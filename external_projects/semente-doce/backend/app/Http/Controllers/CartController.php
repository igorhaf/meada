<?php

namespace App\Http\Controllers;

use App\Models\Product;
use Illuminate\Contracts\View\View;

class CartController extends Controller
{
    /**
     * A sacola vive no cliente (ilha Vue + localStorage). Esta página monta só a
     * casca (ilha CartPage) com as regras de entrega da loja e sugere alguns
     * docinhos para completar o pedido. O checkout recalcula tudo no servidor.
     */
    public function index(): View
    {
        $suggestions = Product::active()
            ->with('images')
            ->orderByDesc('sold_count')
            ->take(6)
            ->get();

        $user = auth()->user();

        $props = [
            'checkoutUrl' => route('checkout.store'),
            'loginUrl' => route('login'),
            'quoteUrl' => route('delivery.quote'),
            'csrf' => csrf_token(),
            'authenticated' => auth()->check(),
            'buyer' => [
                'name' => $user->name ?? '',
                'email' => $user->email ?? '',
                'phone' => $user->phone ?? '',
            ],
            'delivery' => [
                'minOrder' => config('delivery.min_order'),
                'freeAbove' => config('delivery.free_above'),
                'defaultFee' => (float) config('delivery.default_fee'),
                'pickupEnabled' => (bool) config('delivery.pickup_enabled'),
                'etaMin' => (int) config('delivery.eta_min'),
                'etaMax' => (int) config('delivery.eta_max'),
                'origin' => config('delivery.origin'),
            ],
        ];

        return view('cart', compact('props', 'suggestions'));
    }
}
