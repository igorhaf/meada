<?php

namespace App\Http\Controllers\Admin;

use App\Http\Controllers\Controller;
use App\Models\Service;
use Illuminate\Contracts\View\View;
use Illuminate\Http\RedirectResponse;
use Illuminate\Http\Request;

class FeaturedController extends Controller
{
    public function index(Request $request): View
    {
        $featured = Service::where('is_featured', true)->with('images')->latest('updated_at')->get();

        $services = Service::with('images')
            ->when($request->query('q'), fn ($q, $term) => $q->where('title', 'ilike', "%{$term}%"))
            ->orderByDesc('bookings_count')
            ->paginate(12)
            ->withQueryString();

        return view('admin.featured', compact('featured', 'services'));
    }

    public function toggle(Service $service): RedirectResponse
    {
        $service->update(['is_featured' => ! $service->is_featured]);

        return back()->with('status', $service->is_featured
            ? "\"{$service->title}\" agora está em destaque."
            : "\"{$service->title}\" saiu dos destaques.");
    }
}
