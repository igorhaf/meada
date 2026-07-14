<?php

namespace App\Http\Controllers;

use App\Models\User;
use Illuminate\Support\Facades\Schema;
use Illuminate\View\View;

class ProfessionalController extends Controller
{
    /** Public therapist landing page: /terapeuta/{slug}. */
    public function show(User $user): View
    {
        abort_unless($user->is_professional && $user->is_active, 404);

        $user->load('specialties');

        $services = $user->services()->active()->with('images')
            ->orderByDesc('is_featured')->orderByDesc('bookings_count')->get();
        $events = $user->instructedEvents()->published()->upcoming()->with('instructors')->orderBy('starts_at')->get();

        // Locais de atendimento chegam na P5 — guardamos enquanto a tabela não existe.
        $locations = collect();
        if (Schema::hasTable('attendance_locations')) {
            $locations = $user->attendanceLocations()->where('is_active', true)->orderBy('position')->get();
        }

        return view('professionals.show', compact('user', 'services', 'locations', 'events'));
    }
}
