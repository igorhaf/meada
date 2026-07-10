<?php

namespace App\Http\Controllers\Admin;

use App\Http\Controllers\Controller;
use App\Models\Order;
use Illuminate\Contracts\View\View;
use Illuminate\Http\RedirectResponse;
use Illuminate\Http\Request;

class OrderController extends Controller
{
    public function index(Request $request): View
    {
        $status = $request->query('status');

        $orders = Order::with('items')
            ->when($status && array_key_exists($status, Order::STATUSES),
                fn ($q) => $q->where('status', $status))
            ->when($request->query('q'),
                fn ($q, $term) => $q->where('reference', 'ilike', "%{$term}%")
                    ->orWhere('buyer_name', 'ilike', "%{$term}%"))
            ->latest()
            ->paginate(20)
            ->withQueryString();

        // Contadores por status (para as abas de filtro).
        $counts = Order::query()
            ->selectRaw('status, count(*) as total')
            ->groupBy('status')
            ->pluck('total', 'status');

        return view('admin.orders.index', compact('orders', 'counts', 'status'));
    }

    public function show(Order $order): View
    {
        $order->load('items');

        return view('admin.orders.show', compact('order'));
    }

    public function status(Request $request, Order $order): RedirectResponse
    {
        $data = $request->validate([
            'status' => ['required', 'in:' . implode(',', array_keys(Order::STATUSES))],
        ]);

        $order->update(['status' => $data['status']]);

        return back()->with('status', 'Pedido movido para "' . Order::STATUSES[$data['status']] . '".');
    }
}
