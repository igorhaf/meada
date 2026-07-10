<?php

namespace App\Http\Controllers;

use App\Models\Service;
use Illuminate\Contracts\View\View;

class ServiceController extends Controller
{
    public function show(Service $service): View
    {
        abort_unless($service->is_active || auth()->user()?->isRoot(), 404);

        $service->load(['images', 'category', 'professional']);
        $service->incrementQuietly('views');

        $related = Service::active()
            ->where('service_category_id', $service->service_category_id)
            ->where('id', '!=', $service->id)
            ->with('images')
            ->orderByDesc('bookings_count')
            ->take(8)
            ->get();

        return view('services.show', [
            'service' => $service,
            'related' => $related,
        ]);
    }
}
