<?php

namespace App\Http\Controllers\Admin;

use App\Http\Controllers\Controller;
use App\Models\CustomOrder;
use App\Models\Kit;
use App\Models\Order;
use App\Models\Product;
use Illuminate\Contracts\View\View;

class DashboardController extends Controller
{
    /** Visão geral da doceria: pedidos do dia, encomendas abertas, catálogo e faturamento. */
    public function index(): View
    {
        $stats = [
            'orders_today' => Order::whereDate('created_at', today())->count(),
            'custom_open' => CustomOrder::open()->count(),
            'products' => Product::active()->count(),
            'kits' => Kit::active()->count(),
            'revenue' => (float) Order::whereIn('payment_status', ['approved', 'authorized'])->sum('total'),
        ];

        $recentCustomOrders = CustomOrder::latest()->take(6)->get();

        $recentOrders = Order::with('items')->latest()->take(6)->get();

        return view('admin.dashboard', compact('stats', 'recentCustomOrders', 'recentOrders'));
    }
}
