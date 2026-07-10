<?php

namespace App\Http\Controllers\Admin;

use App\Http\Controllers\Controller;
use App\Models\CustomOrder;
use Illuminate\Contracts\View\View;
use Illuminate\Http\RedirectResponse;
use Illuminate\Http\Request;
use Illuminate\Support\Carbon;

class CustomOrderController extends Controller
{
    /** ⭐ Kanban das encomendas — uma coluna por status do fluxo (CustomOrder::BOARD). */
    public function index(): View
    {
        // Cards do quadro, agrupados por status na ordem do fluxo.
        $board = CustomOrder::whereIn('status', CustomOrder::BOARD)
            ->orderBy('event_date')
            ->latest('created_at')
            ->get()
            ->groupBy('status');

        // Encomendas encerradas (recusadas/canceladas/entregues antigas ficam fora do quadro ativo).
        $archived = CustomOrder::whereIn('status', ['declined', 'cancelled'])
            ->latest()
            ->take(20)
            ->get();

        return view('admin.custom-orders.index', compact('board', 'archived'));
    }

    public function show(CustomOrder $customOrder): View
    {
        $customOrder->load(['product', 'kit', 'user']);

        return view('admin.custom-orders.show', compact('customOrder'));
    }

    /** Orça a encomenda: define o preço e as observações da loja. */
    public function quote(Request $request, CustomOrder $customOrder): RedirectResponse
    {
        $data = $request->validate([
            'quoted_price' => ['required', 'numeric', 'min:0', 'max:1000000'],
            'admin_notes' => ['nullable', 'string', 'max:2000'],
        ]);

        $customOrder->quote((float) $data['quoted_price'], $data['admin_notes'] ?? null);

        return redirect()->route('admin.custom-orders.show', $customOrder)
            ->with('status', 'Orçamento enviado: ' . money($customOrder->quoted_price) . '.');
    }

    /** Move a encomenda para outro status do fluxo. */
    public function status(Request $request, CustomOrder $customOrder): RedirectResponse
    {
        $data = $request->validate([
            'status' => ['required', 'in:' . implode(',', array_keys(CustomOrder::STATUSES))],
        ]);

        $customOrder->status = $data['status'];

        if ($data['status'] === 'confirmed' && $customOrder->confirmed_at === null) {
            $customOrder->confirmed_at = Carbon::now();
        }

        $customOrder->save();

        return back()->with('status', 'Encomenda movida para "' . CustomOrder::STATUSES[$data['status']] . '".');
    }
}
