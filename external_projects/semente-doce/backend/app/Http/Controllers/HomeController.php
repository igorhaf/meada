<?php

namespace App\Http\Controllers;

use App\Models\Banner;
use App\Models\Category;
use App\Models\Kit;
use App\Models\Product;
use Illuminate\Contracts\View\View;

class HomeController extends Controller
{
    public function index(): View
    {
        $heroBanners = Banner::active()->placement('hero')->get();
        $stripBanners = Banner::active()->placement('strip')->get();

        $categories = Category::active()->roots()->orderBy('position')->get();

        $base = Product::query()->active()->with('images');

        $deals = (clone $base)
            ->whereNotNull('compare_at_price')
            ->whereColumn('compare_at_price', '>', 'price')
            ->orderByRaw('(compare_at_price - price) / compare_at_price DESC')
            ->take(10)->get();

        $newest = (clone $base)->readyToEat()->latest()->take(10)->get();
        $bestSellers = (clone $base)->orderByDesc('sold_count')->take(10)->get();
        $madeToOrder = (clone $base)->madeToOrder()->orderByDesc('sold_count')->take(8)->get();

        // Kits em destaque — a estrela montada pela doceria.
        $featuredKits = Kit::active()->with('items')
            ->orderByDesc('is_featured')->orderBy('position')
            ->take(4)->get();

        // Vitrines por categoria ("compre por categoria").
        $showcases = $categories->take(3)->map(function (Category $category) {
            return [
                'category' => $category,
                'products' => Product::active()
                    ->where('category_id', $category->id)
                    ->with('images')
                    ->orderByDesc('sold_count')
                    ->take(6)
                    ->get(),
            ];
        })->filter(fn ($row) => $row['products']->isNotEmpty());

        return view('home', compact(
            'heroBanners', 'stripBanners', 'categories',
            'deals', 'newest', 'bestSellers', 'madeToOrder', 'featuredKits', 'showcases'
        ));
    }
}
