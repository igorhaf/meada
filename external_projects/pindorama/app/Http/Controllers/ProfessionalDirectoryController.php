<?php

namespace App\Http\Controllers;

use App\Models\Service;
use App\Models\User;
use Illuminate\Contracts\View\View;
use Illuminate\Http\Request;

class ProfessionalDirectoryController extends Controller
{
    /**
     * Diretório público de terapeutas.
     *
     * Lista profissionais (is_professional) que já têm ao menos um serviço ativo —
     * profissionais sem catálogo ativo não aparecem. Filtro opcional por cidade,
     * derivada dos campos denormalizados dos serviços (professional_city).
     */
    public function index(Request $request): View
    {
        $city = $request->query('city');
        if (is_array($city)) {
            $city = $city[0] ?? null;
        }
        $city = is_string($city) && $city !== '' ? $city : null;

        $professionals = User::query()
            ->where('is_professional', true)
            ->whereHas('services', fn ($q) => $q->active())
            ->when($city, fn ($q, $c) => $q->whereHas(
                'services',
                fn ($s) => $s->active()->where('professional_city', $c)
            ))
            ->with(['services' => fn ($q) => $q->active()->with('category')])
            ->orderBy('name')
            ->paginate(24)
            ->withQueryString();

        // Facet de cidades: valores distintos denormalizados nos serviços ativos.
        $cities = Service::query()
            ->active()
            ->whereNotNull('professional_city')
            ->distinct()
            ->orderBy('professional_city')
            ->pluck('professional_city');

        return view('professionals.index', compact('professionals', 'cities', 'city'));
    }
}
