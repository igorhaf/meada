<?php

namespace App\Http\Controllers\Admin;

use App\Http\Controllers\Controller;
use App\Models\DeliveryZone;
use Illuminate\Contracts\View\View;
use Illuminate\Http\RedirectResponse;
use Illuminate\Http\Request;

class DeliveryZoneController extends Controller
{
    public function index(): View
    {
        $zones = DeliveryZone::orderBy('position')->orderBy('neighborhood')->get();

        return view('admin.delivery-zones.index', compact('zones'));
    }

    public function store(Request $request): RedirectResponse
    {
        DeliveryZone::create($this->validated($request));

        return back()->with('status', 'Bairro adicionado.');
    }

    public function update(Request $request, DeliveryZone $deliveryZone): RedirectResponse
    {
        $deliveryZone->update($this->validated($request));

        return back()->with('status', 'Bairro atualizado.');
    }

    public function destroy(DeliveryZone $deliveryZone): RedirectResponse
    {
        $deliveryZone->delete();

        return back()->with('status', 'Bairro removido.');
    }

    /* --------------------------------------------------------------- helpers */

    /**
     * @return array<string,mixed>
     */
    private function validated(Request $request): array
    {
        return $request->validate([
            'neighborhood' => ['required', 'string', 'max:255'],
            'fee' => ['required', 'numeric', 'min:0', 'max:100000'],
            'eta_min' => ['nullable', 'integer', 'min:0', 'max:100000'],
            'eta_max' => ['nullable', 'integer', 'min:0', 'max:100000'],
            'position' => ['nullable', 'integer', 'min:0'],
            'is_active' => ['nullable', 'boolean'],
        ]);
    }
}
