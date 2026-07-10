<?php

namespace App\Http\Controllers\Professional;

use App\Http\Controllers\Controller;
use App\Models\AvailabilityBlock;
use App\Models\ProfessionalAvailability;
use Carbon\CarbonImmutable;
use Illuminate\Http\RedirectResponse;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\DB;
use Illuminate\View\View;

class AvailabilityController extends Controller
{
    public function edit(Request $request): View
    {
        $user = $request->user();
        $locations = $user->attendanceLocations()->where('is_active', true)->orderBy('name')->get();

        return view('professional.availability.edit', [
            'locations' => $locations,
            'availabilities' => $user->availabilities()->orderBy('weekday')->orderBy('start_time')->get(),
            'blocks' => $user->availabilityBlocks()->with('location')->where('ends_at', '>=', now())->orderBy('starts_at')->get(),
            'weekdays' => ProfessionalAvailability::WEEKDAYS,
        ]);
    }

    public function update(Request $request): RedirectResponse
    {
        $user = $request->user();
        $ownedLocationIds = $user->attendanceLocations()->pluck('id')->all();

        $validated = $request->validate([
            'rows' => ['array'],
            'rows.*.attendance_location_id' => ['required', 'integer'],
            'rows.*.weekday' => ['required', 'integer', 'between:0,6'],
            'rows.*.start_time' => ['required', 'date_format:H:i'],
            'rows.*.end_time' => ['required', 'date_format:H:i', 'after:rows.*.start_time'],
        ]);

        DB::transaction(function () use ($user, $validated, $ownedLocationIds) {
            $user->availabilities()->delete();

            foreach ($validated['rows'] ?? [] as $row) {
                if (! in_array((int) $row['attendance_location_id'], $ownedLocationIds, true)) {
                    continue; // ignora local que não é do profissional
                }
                if ($row['end_time'] <= $row['start_time']) {
                    continue;
                }
                ProfessionalAvailability::create([
                    'professional_id' => $user->id,
                    'attendance_location_id' => (int) $row['attendance_location_id'],
                    'weekday' => (int) $row['weekday'],
                    'start_time' => $row['start_time'],
                    'end_time' => $row['end_time'],
                    'is_active' => true,
                ]);
            }
        });

        return redirect()->route('professional.availability.edit')->with('status', 'Disponibilidade atualizada.');
    }

    public function storeBlock(Request $request): RedirectResponse
    {
        $user = $request->user();
        $tz = $user->timezone ?: config('pindorama.timezone');

        $data = $request->validate([
            'attendance_location_id' => ['nullable', 'integer'],
            'date' => ['required', 'date'],
            'all_day' => ['nullable', 'boolean'],
            'start_time' => ['nullable', 'date_format:H:i', 'required_if:all_day,0', 'required_without:all_day'],
            'end_time' => ['nullable', 'date_format:H:i', 'after:start_time'],
            'reason' => ['nullable', 'string', 'max:255'],
        ]);

        $allDay = $request->boolean('all_day');
        $date = CarbonImmutable::parse($data['date'], $tz);

        if ($allDay) {
            $start = $date->startOfDay();
            $end = $date->endOfDay();
        } else {
            $start = $date->setTimeFromTimeString($data['start_time']);
            $end = $date->setTimeFromTimeString($data['end_time'] ?? '23:59');
        }

        // Só aceita local que é do profissional; senão bloqueio geral (todos os locais).
        $locationId = $data['attendance_location_id'] ?? null;
        if ($locationId && ! $user->attendanceLocations()->whereKey($locationId)->exists()) {
            $locationId = null;
        }

        AvailabilityBlock::create([
            'professional_id' => $user->id,
            'attendance_location_id' => $locationId,
            'starts_at' => $start,
            'ends_at' => $end,
            'all_day' => $allDay,
            'reason' => $data['reason'] ?? null,
        ]);

        return redirect()->route('professional.availability.edit')->with('status', 'Bloqueio adicionado.');
    }

    public function destroyBlock(Request $request, AvailabilityBlock $block): RedirectResponse
    {
        abort_unless($block->professional_id === $request->user()->id || $request->user()->isRoot(), 403);
        $block->delete();

        return redirect()->route('professional.availability.edit')->with('status', 'Bloqueio removido.');
    }
}
