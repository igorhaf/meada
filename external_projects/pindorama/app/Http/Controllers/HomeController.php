<?php

namespace App\Http\Controllers;

use App\Models\Banner;
use App\Models\Service;
use App\Models\ServiceCategory;
use App\Models\User;
use Illuminate\Contracts\View\View;
use Illuminate\Support\Facades\Schema;

class HomeController extends Controller
{
    public function index(): View
    {
        $heroBanners = Banner::active()->placement('hero')->get();
        $stripBanners = Banner::active()->placement('strip')->get();

        // Atalhos de práticas (acupuntura, reiki, ayurveda, MTC, ...).
        $categories = ServiceCategory::active()->roots()->orderBy('position')->get();

        $base = Service::query()->active()->with('images');

        // Ofertas: serviços com preço promocional (maior desconto primeiro).
        $deals = (clone $base)
            ->whereNotNull('compare_at_price')
            ->whereColumn('compare_at_price', '>', 'price')
            ->orderByRaw('(compare_at_price - price) / compare_at_price DESC')
            ->take(10)->get();

        $newest = (clone $base)->latest()->take(10)->get();
        $mostBooked = (clone $base)->orderByDesc('bookings_count')->take(10)->get();

        // Terapeutas em destaque. A coluna is_verified pode ainda não existir
        // (migração de store_* -> professional_* é fase posterior): cai para
        // apenas is_professional quando ausente.
        $professionalsQuery = User::where('is_professional', true)->where('is_active', true);
        if (Schema::hasColumn('users', 'is_verified')) {
            $professionalsQuery->where('is_verified', true);
        }
        $featuredProfessionals = $professionalsQuery->take(8)->get();

        // Link para a landing do terapeuta só existe quando o slug está disponível.
        $professionalSlugAvailable = Schema::hasColumn('users', 'professional_slug');

        // Vitrines por prática: as 3 primeiras raízes com seus 6 serviços mais
        // agendados, descartando as vazias.
        $showcases = $categories->take(3)->map(function (ServiceCategory $category) {
            return [
                'category' => $category,
                'services' => Service::active()
                    ->where('service_category_id', $category->id)
                    ->with('images')
                    ->orderByDesc('bookings_count')
                    ->take(6)
                    ->get(),
            ];
        })->filter(fn ($row) => $row['services']->isNotEmpty());

        return view('home', compact(
            'heroBanners', 'stripBanners', 'categories',
            'deals', 'newest', 'mostBooked',
            'featuredProfessionals', 'professionalSlugAvailable', 'showcases'
        ));
    }
}
