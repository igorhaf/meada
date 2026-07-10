<?php

namespace App\Http\Controllers\Professional;

use App\Http\Controllers\Controller;
use App\Models\AttendanceLocation;
use Illuminate\Http\RedirectResponse;
use Illuminate\Http\Request;
use Illuminate\View\View;

class LocationController extends Controller
{
    public function index(Request $request): View
    {
        $locations = $request->user()->attendanceLocations()->orderBy('position')->orderBy('name')->get();

        return view('professional.locations.index', compact('locations'));
    }

    public function create(): View
    {
        return view('professional.locations.create', ['location' => new AttendanceLocation(['is_active' => true])]);
    }

    public function store(Request $request): RedirectResponse
    {
        $data = $this->validated($request);
        $data['professional_id'] = $request->user()->id;
        AttendanceLocation::create($data);

        return redirect()->route('professional.locations.index')->with('status', 'Local criado.');
    }

    public function edit(Request $request, AttendanceLocation $location): View
    {
        $this->authorizeLocation($request, $location);

        return view('professional.locations.edit', compact('location'));
    }

    public function update(Request $request, AttendanceLocation $location): RedirectResponse
    {
        $this->authorizeLocation($request, $location);
        $location->update($this->validated($request));

        return redirect()->route('professional.locations.index')->with('status', 'Local atualizado.');
    }

    public function destroy(Request $request, AttendanceLocation $location): RedirectResponse
    {
        $this->authorizeLocation($request, $location);
        $location->delete();

        return redirect()->route('professional.locations.index')->with('status', 'Local removido.');
    }

    /** @return array<string,mixed> */
    private function validated(Request $request): array
    {
        $data = $request->validate([
            'name' => ['required', 'string', 'max:255'],
            'is_online' => ['nullable', 'boolean'],
            'address' => ['nullable', 'string', 'max:255'],
            'neighborhood' => ['nullable', 'string', 'max:120'],
            'city' => ['nullable', 'string', 'max:120'],
            'state' => ['nullable', 'string', 'max:60'],
            'zip' => ['nullable', 'string', 'max:20'],
            'complement' => ['nullable', 'string', 'max:255'],
            'map_url' => ['nullable', 'url', 'max:500'],
            'is_active' => ['nullable', 'boolean'],
            'position' => ['nullable', 'integer', 'min:0'],
        ]);
        $data['is_online'] = $request->boolean('is_online');
        $data['is_active'] = $request->boolean('is_active');
        $data['position'] = (int) ($data['position'] ?? 0);

        return $data;
    }

    private function authorizeLocation(Request $request, AttendanceLocation $location): void
    {
        abort_unless($location->professional_id === $request->user()->id || $request->user()->isRoot(), 403);
    }
}
