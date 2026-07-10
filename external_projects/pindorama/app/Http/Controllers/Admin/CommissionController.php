<?php

namespace App\Http\Controllers\Admin;

use App\Http\Controllers\Controller;
use App\Models\CommissionRule;
use App\Models\Room;
use App\Models\Service;
use App\Models\ServiceCategory;
use App\Models\User;
use Illuminate\Http\RedirectResponse;
use Illuminate\Http\Request;
use Illuminate\View\View;

class CommissionController extends Controller
{
    public function index(): View
    {
        $rules = CommissionRule::orderByRaw("array_position(ARRAY['default','room','professional','service_category','service']::text[], scope_type)")
            ->get();

        // Rótulos para exibir o alvo de cada regra.
        $labels = [
            'room' => Room::pluck('name', 'id'),
            'professional' => User::where('is_professional', true)->pluck('name', 'id'),
            'service_category' => ServiceCategory::pluck('name', 'id'),
            'service' => Service::pluck('title', 'id'),
        ];

        return view('admin.commission', [
            'rules' => $rules,
            'labels' => $labels,
            'scopeTypes' => CommissionRule::SCOPE_TYPES,
            'rateTypes' => CommissionRule::RATE_TYPES,
            'rooms' => Room::orderBy('name')->get(),
            'professionals' => User::where('is_professional', true)->where('role', '!=', 'root')->orderBy('name')->get(),
            'categories' => ServiceCategory::orderBy('name')->get(),
            'services' => Service::orderBy('title')->get(),
        ]);
    }

    public function store(Request $request): RedirectResponse
    {
        $data = $request->validate([
            'scope_type' => ['required', 'in:default,room,professional,service_category,service'],
            'scope_id' => ['nullable', 'integer'],
            'rate_type' => ['required', 'in:percent,fixed'],
            'rate_value' => ['required', 'numeric', 'min:0'],
        ]);

        $scopeId = $data['scope_type'] === 'default' ? null : $data['scope_id'];
        if ($data['scope_type'] !== 'default' && ! $scopeId) {
            return back()->with('error', 'Selecione o alvo da regra.');
        }
        if ($data['rate_type'] === 'percent' && $data['rate_value'] > 100) {
            return back()->with('error', 'Percentual não pode passar de 100%.');
        }

        CommissionRule::updateOrCreate(
            ['scope_type' => $data['scope_type'], 'scope_id' => $scopeId],
            ['rate_type' => $data['rate_type'], 'rate_value' => $data['rate_value'], 'is_active' => true],
        );

        return back()->with('status', 'Regra de comissão salva.');
    }

    public function destroy(CommissionRule $rule): RedirectResponse
    {
        $rule->delete();

        return back()->with('status', 'Regra removida.');
    }
}
