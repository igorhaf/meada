<?php

namespace App\Http\Controllers;

use App\Models\Service;
use App\Models\ServiceCategory;
use App\Support\ListingFacets;
use Illuminate\Contracts\View\View;
use Illuminate\Http\Request;

class ServiceCategoryController extends Controller
{
    public function show(Request $request, ServiceCategory $serviceCategory): View
    {
        // Lista os serviços da prática mais os das suas sub-práticas diretas.
        $categoryIds = $serviceCategory->children()->pluck('id')->push($serviceCategory->id)->all();

        $base = Service::active()->whereIn('service_category_id', $categoryIds);

        $facets = ListingFacets::build($base);

        $services = (clone $base)
            ->with('images')
            ->filter($request->only(['q', 'modality', 'city', 'min', 'max', 'sort']))
            ->paginate(24)
            ->withQueryString();

        $breadcrumbs = array_values(array_filter([
            $serviceCategory->parent,
            $serviceCategory,
        ]));

        return view('practices.show', [
            'category' => $serviceCategory,
            'services' => $services,
            'facets' => $facets,
            'breadcrumbs' => $breadcrumbs,
            'title' => $serviceCategory->name,
            'action' => $serviceCategory->url,
        ]);
    }
}
