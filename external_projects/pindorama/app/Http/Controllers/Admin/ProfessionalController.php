<?php

namespace App\Http\Controllers\Admin;

use App\Http\Controllers\Controller;
use App\Models\PlatformCharge;
use App\Models\ProfessionalInvite;
use App\Models\ServiceCategory;
use App\Models\User;
use App\Notifications\ProfessionalInviteNotification;
use App\Services\AuditService;
use App\Services\BillingService;
use Illuminate\Http\RedirectResponse;
use Illuminate\Http\Request;
use Illuminate\View\View;
use Illuminate\Support\Facades\Storage;
use Illuminate\Support\Str;

class ProfessionalController extends Controller
{
    public function create(): View
    {
        return view('admin.professional-form', ['user' => new User(['timezone' => config('pindorama.timezone'), 'is_active' => true]), 'categories' => ServiceCategory::active()->orderBy('name')->get(), 'selectedSpecialties' => []]);
    }

    public function store(Request $request, AuditService $audit): RedirectResponse
    {
        $data = $this->validatedProfile($request);
        $user = User::create($data + [
            'password' => Str::random(48), 'role' => 'professional', 'is_professional' => true,
            'professional_slug' => $this->uniqueSlug($data['professional_name']),
        ]);
        foreach (['avatar' => ['professionals/avatars', 'avatar_path'], 'banner' => ['professionals/banners', 'banner_path']] as $input => [$dir, $column]) {
            if ($request->hasFile($input)) $user->update([$column => '/storage/'.$request->file($input)->store($dir, 'public')]);
        }
        $user->specialties()->sync($request->input('specialties', []));
        $audit->record('professional.created', $user, [], $user->only(['name', 'email', 'professional_name']));
        $url = $this->invite($user, $request->user()->id);

        return redirect()->route('admin.professionals.show', $user)->with('status', 'Profissional criado. O convite foi gerado.')->with('invite_url', $url);
    }

    public function edit(User $user): View
    {
        abort_unless($user->is_professional && ! $user->isRoot(), 404);
        $user->load('specialties');
        return view('admin.professional-form', ['user' => $user, 'categories' => ServiceCategory::active()->orderBy('name')->get(), 'selectedSpecialties' => $user->specialties->pluck('id')->all()]);
    }

    public function update(Request $request, User $user, AuditService $audit): RedirectResponse
    {
        abort_unless($user->is_professional && ! $user->isRoot(), 404);
        $before = $user->toArray();
        $data = $this->validatedProfile($request, $user);
        $user->fill($data);
        foreach (['avatar' => ['professionals/avatars', 'avatar_path'], 'banner' => ['professionals/banners', 'banner_path']] as $input => [$dir, $column]) {
            if ($request->hasFile($input)) {
                if ($user->{$column} && str_starts_with($user->{$column}, '/storage/')) Storage::disk('public')->delete(str_replace('/storage/', '', $user->{$column}));
                $user->{$column} = '/storage/'.$request->file($input)->store($dir, 'public');
            }
        }
        $user->save();
        $user->specialties()->sync($request->input('specialties', []));
        $user->services()->update(['professional_name' => $user->professional_name, 'professional_city' => $user->city, 'professional_state' => $user->state]);
        $audit->record('professional.updated', $user, $before, $user->toArray());
        return redirect()->route('admin.professionals.show', $user)->with('status', 'Profissional atualizado.');
    }

    public function toggleActive(User $user, AuditService $audit): RedirectResponse
    {
        abort_unless($user->is_professional && ! $user->isRoot(), 404);
        $user->update(['is_active' => ! $user->is_active]);
        $audit->record('professional.active_changed', $user, [], ['is_active' => $user->is_active]);
        return back()->with('status', $user->is_active ? 'Acesso reativado.' : 'Acesso e exibição pública desativados.');
    }

    public function resendInvite(Request $request, User $user): RedirectResponse
    {
        abort_unless($user->is_professional && ! $user->isRoot(), 404);
        $url = $this->invite($user, $request->user()->id);
        return back()->with('status', 'Novo convite gerado.')->with('invite_url', $url);
    }
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
        $user->load(['services.category','attendanceLocations.room','availabilities','instructedEvents']);
        $appointments = $user->appointmentsAsProfessional()->with('customer')->latest('start_at')->limit(10)->get();
        $transactions = $user->transactions()->latest()->limit(10)->get();

        return view('admin.professional-show', compact('user', 'charges','appointments','transactions'));
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

    /** @return array<string,mixed> */
    private function validatedProfile(Request $request, ?User $user = null): array
    {
        $data = $request->validate([
            'name' => ['required', 'string', 'max:255'],
            'email' => ['required', 'email', 'max:255', 'unique:users,email,'.($user?->id ?: 'NULL')],
            'professional_name' => ['required', 'string', 'max:255'],
            'headline' => ['nullable', 'string', 'max:255'], 'bio' => ['nullable', 'string', 'max:5000'],
            'city' => ['nullable', 'string', 'max:120'], 'state' => ['nullable', 'string', 'max:60'],
            'phone' => ['nullable', 'string', 'max:40'], 'whatsapp' => ['nullable', 'string', 'max:40'],
            'registration_council' => ['nullable', 'string', 'max:120'], 'timezone' => ['required', 'timezone'],
            'instagram_url' => ['nullable', 'url', 'max:255'], 'facebook_url' => ['nullable', 'url', 'max:255'],
            'youtube_url' => ['nullable', 'url', 'max:255'], 'website_url' => ['nullable', 'url', 'max:255'],
            'avatar' => ['nullable', 'image', 'max:5120'], 'banner' => ['nullable', 'image', 'max:8192'],
            'specialties' => ['nullable', 'array'], 'specialties.*' => ['integer', 'exists:service_categories,id'],
        ]);
        unset($data['avatar'], $data['banner'], $data['specialties']);
        return $data;
    }

    private function uniqueSlug(string $name): string
    {
        $base = Str::slug($name) ?: 'terapeuta'; $slug = $base; $i = 2;
        while (User::where('professional_slug', $slug)->exists()) $slug = $base.'-'.$i++;
        return $slug;
    }

    private function invite(User $user, int $createdBy): string
    {
        ProfessionalInvite::where('professional_id', $user->id)->whereNull('accepted_at')->delete();
        $token = Str::random(64);
        ProfessionalInvite::create(['professional_id' => $user->id, 'token_hash' => hash('sha256', $token), 'expires_at' => now()->addHours(72), 'created_by' => $createdBy]);
        $url = route('professional-invites.show', $token);
        try { $user->notify(new ProfessionalInviteNotification($url)); } catch (\Throwable $e) { report($e); }
        return $url;
    }
}
