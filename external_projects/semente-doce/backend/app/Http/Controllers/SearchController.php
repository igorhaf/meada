<?php

namespace App\Http\Controllers;

use App\Models\Category;
use App\Models\Product;
use App\Support\ListingFacets;
use Illuminate\Contracts\View\View;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;

class SearchController extends Controller
{
    public function index(Request $request): View
    {
        $term = trim((string) $request->query('q', ''));

        $base = Product::active()->search($term);

        $facets = ListingFacets::build($base);

        $products = (clone $base)
            ->with('images')
            ->filter($request->only(['availability', 'unit', 'min', 'max', 'sort']))
            ->paginate(24)
            ->withQueryString();

        return view('search', [
            'term' => $term,
            'products' => $products,
            'facets' => $facets,
            'title' => $term !== '' ? "Resultados para \"{$term}\"" : 'Todo o cardápio',
            'action' => route('search'),
        ]);
    }

    /**
     * Endpoint JSON leve que alimenta o autocomplete de busca (ilha Vue).
     */
    public function suggest(Request $request): JsonResponse
    {
        $term = trim((string) $request->query('q', ''));

        if (mb_strlen($term) < 2) {
            return response()->json(['products' => [], 'categories' => []]);
        }

        $products = Product::active()->search($term)
            ->with('images')
            ->orderByDesc('sold_count')
            ->take(6)
            ->get()
            ->map(fn (Product $p) => [
                'title' => $p->title,
                'url' => $p->url,
                'image' => $p->primary_image_url,
                'price' => money($p->price),
            ]);

        $like = '%' . str_replace(' ', '%', $term) . '%';
        $categories = Category::active()
            ->where('name', 'ilike', $like)
            ->take(4)
            ->get()
            ->map(fn (Category $c) => ['name' => $c->name, 'url' => $c->url]);

        return response()->json([
            'products' => $products,
            'categories' => $categories,
        ]);
    }
}
