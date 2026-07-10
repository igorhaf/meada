<?php

namespace App\Http\Controllers;

use App\Models\Category;
use App\Models\Product;
use App\Support\ListingFacets;
use Illuminate\Contracts\View\View;
use Illuminate\Http\Request;

class CategoryController extends Controller
{
    public function show(Request $request, Category $category): View
    {
        // A listagem inclui a própria categoria e suas subcategorias diretas.
        $categoryIds = $category->children()->pluck('id')->push($category->id)->all();

        $base = Product::active()->whereIn('category_id', $categoryIds);

        $facets = ListingFacets::build($base);

        $products = (clone $base)
            ->with('images')
            ->filter($request->only(['q', 'availability', 'unit', 'min', 'max', 'sort']))
            ->paginate(24)
            ->withQueryString();

        $breadcrumbs = array_values(array_filter([
            $category->parent,
            $category,
        ]));

        return view('categories.show', [
            'category' => $category,
            'products' => $products,
            'facets' => $facets,
            'breadcrumbs' => $breadcrumbs,
            'title' => $category->name,
            'action' => $category->url,
        ]);
    }
}
