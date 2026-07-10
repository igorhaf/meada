<?php

namespace App\Http\Controllers\Admin;

use App\Http\Controllers\Controller;
use App\Models\PlatformCharge;
use App\Models\User;
use App\Services\BillingService;
use Illuminate\Http\RedirectResponse;
use Illuminate\Http\Request;
use Illuminate\View\View;

class ProfessionalController extends Controller
{
    public function index(Request $request): View
    {
        $professionals = User::where('is_professional', true)->where('role', '!=', 'root')
            ->withCount('services')
            ->when($request->query('q'), fn ($q, $term) => $q->where('name', 'ilike', "%{$term}%"))
            ->orderBy('name')
            ->paginate(20)
            ->withQueryString();

        return view('admin.professionals', compact('professionals'));
    }

    public function show(User $user): View
    {
        abort_unless($user->is_professional, 404);
        $charges = $user->charges()->latest()->get();

        return view('admin.professional-show', compact('user', 'charges'));
    }

    public function toggleVerified(User $user): RedirectResponse
    {
        abort_unless($user->is_professional, 404);
        $user->update(['is_verified' => ! $user->is_verified]);

        return back()->with('status', $user->is_verified
            ? "{$user->display_name} agora está verificado."
            : "{$user->display_name} não está mais verificado.");
    }

    public function updateBilling(Request $request, User $user): RedirectResponse
    {
        abort_unless($user->is_professional, 404);

        $data = $request->validate([
            'billing_monthly_fee' => ['required', 'numeric', 'min:0'],
            'billing_discount_percent' => ['required', 'numeric', 'min:0', 'max:100'],
            'billing_free' => ['nullable', 'boolean'],
            'billing_active' => ['nullable', 'boolean'],
            'billing_day' => ['required', 'integer', 'min:1', 'max:28'],
        ]);
        $data['billing_free'] = $request->boolean('billing_free');
        $data['billing_active'] = $request->boolean('billing_active');
        $user->update($data);

        return back()->with('status', 'Cobrança atualizada.');
    }

    public function generateMonthly(User $user, BillingService $billing): RedirectResponse
    {
        abort_unless($user->is_professional, 404);
        $charge = $billing->generateMonthly($user);

        return back()->with('status', "Mensalidade {$charge->reference_month} gerada ({$charge->status_label}).");
    }

    public function createCharge(Request $request, User $user, BillingService $billing): RedirectResponse
    {
        abort_unless($user->is_professional, 404);
        $data = $request->validate([
            'type' => ['required', 'in:registration,featured'],
            'description' => ['required', 'string', 'max:255'],
            'base_amount' => ['required', 'numeric', 'min:0'],
        ]);
        $billing->createCharge($user, $data['type'], $data['description'], (float) $data['base_amount']);

        return back()->with('status', 'Cobrança criada.');
    }

    public function chargeStatus(Request $request, PlatformCharge $charge): RedirectResponse
    {
        $data = $request->validate(['status' => ['required', 'in:pending,paid,waived']]);
        $charge->update([
            'status' => $data['status'],
            'paid_at' => in_array($data['status'], ['paid', 'waived']) ? ($charge->paid_at ?? now()) : null,
        ]);

        return back()->with('status', 'Status da cobrança atualizado.');
    }
}
