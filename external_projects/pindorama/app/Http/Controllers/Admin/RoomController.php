<?php

namespace App\Http\Controllers\Admin;

use App\Http\Controllers\Controller;
use App\Models\Room;
use Illuminate\Http\RedirectResponse;
use Illuminate\Http\Request;
use Illuminate\View\View;

class RoomController extends Controller
{
    public function index(): View
    {
        return view('admin.rooms', ['rooms' => Room::withCount('attendanceLocations')->orderBy('position')->orderBy('name')->get()]);
    }

    public function store(Request $request): RedirectResponse
    {
        Room::create($this->validated($request));

        return back()->with('status', 'Sala criada.');
    }

    public function update(Request $request, Room $room): RedirectResponse
    {
        $room->update($this->validated($request));

        return back()->with('status', 'Sala atualizada.');
    }

    public function destroy(Room $room): RedirectResponse
    {
        $room->delete();

        return back()->with('status', 'Sala removida.');
    }

    /** @return array<string,mixed> */
    private function validated(Request $request): array
    {
        $data = $request->validate([
            'name' => ['required', 'string', 'max:255'],
            'description' => ['nullable', 'string', 'max:255'],
            'position' => ['nullable', 'integer', 'min:0'],
            'is_active' => ['nullable', 'boolean'],
        ]);
        $data['is_active'] = $request->boolean('is_active');
        $data['position'] = (int) ($data['position'] ?? 0);

        return $data;
    }
}
