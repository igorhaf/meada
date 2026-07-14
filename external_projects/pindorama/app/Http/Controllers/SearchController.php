<?php

namespace App\Http\Controllers;

use App\Models\Service;
use App\Models\ServiceCategory;
use App\Models\User;
use App\Support\ListingFacets;
use Illuminate\Contracts\View\View;
use Illuminate\Database\Eloquent\Builder;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\Schema;

class SearchController extends Controller
{
    public function index(Request $request): View
    {
        $term = trim((string) $request->query('q', ''));

        // O termo de busca é a base do resultado; os demais filtros refinam por cima.
        $base = Service::active()->search($term);

        $facets = ListingFacets::build($base);

        $services = (clone $base)
            ->with('images')
            ->filter($request->only(['modality', 'city', 'min', 'max', 'sort']))
            ->paginate(24)
            ->withQueryString();

        return view('search', [
            'term' => $term,
            'services' => $services,
            'facets' => $facets,
            'title' => $term !== '' ? "Resultados para \"{$term}\"" : 'Todos os serviços',
            'action' => route('search'),
        ]);
    }

    /**
     * Endpoint JSON leve que alimenta o autocomplete de busca em Vue.
     */
    public function suggest(Request $request): JsonResponse
    {
        $term = trim((string) $request->query('q', ''));

        if (mb_strlen($term) < 2) {
            return response()->json(['services' => [], 'professionals' => [], 'categories' => []]);
        }

        $like = '%' . str_replace(' ', '%', $term) . '%';

        $services = Service::active()->search($term)
            ->with('images')
            ->orderByDesc('bookings_count')
            ->take(6)
            ->get()
            ->map(fn (Service $s) => [
                'title' => $s->title,
                'url' => $s->url,
                'image' => $s->cover_url,
                'price' => money($s->price),
            ]);

        // Os campos do terapeuta migram de store_* para professional_* numa fase posterior;
        // resolvemos a coluna existente para não quebrar o autocomplete durante a transição.
        $nameCol = Schema::hasColumn('users', 'professional_name') ? 'professional_name' : 'store_name';
        $cityCol = Schema::hasColumn('users', 'professional_city') ? 'professional_city' : 'store_location';
        $slugCol = Schema::hasColumn('users', 'professional_slug') ? 'professional_slug' : null;

        $professionals = User::query()
            ->where('is_professional', true)
            ->where('is_active', true)
            ->where(function (Builder $q) use ($like, $nameCol) {
                $q->where('name', 'ilike', $like)
                    ->orWhere($nameCol, 'ilike', $like);
            })
            ->take(3)
            ->get()
            ->map(fn (User $u) => [
                'name' => $u->{$nameCol} ?: $u->name,
                'url' => ($slugCol && $u->{$slugCol}) ? route('professionals.show', $u->{$slugCol}) : null,
                'city' => $cityCol ? $u->{$cityCol} : null,
            ]);

        $categories = ServiceCategory::active()
            ->where('name', 'ilike', $like)
            ->take(4)
            ->get()
            ->map(fn (ServiceCategory $c) => ['name' => $c->name, 'url' => $c->url]);

        return response()->json([
            'services' => $services,
            'professionals' => $professionals,
            'categories' => $categories,
        ]);
    }
}
