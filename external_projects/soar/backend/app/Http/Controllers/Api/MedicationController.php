<?php

namespace App\Http\Controllers\Api;

use App\Http\Controllers\Concerns\AuthorizesPageAccess;
use App\Http\Controllers\Controller;
use App\Models\Medication;
use App\Models\Page;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;

class MedicationController extends Controller
{
    use AuthorizesPageAccess;

    public function index(Request $request, Page $page): JsonResponse
    {
        $this->authorizeAccess($request, $page);
        $this->authorizeKind($page, 'meds');

        return response()->json(
            $page->medications()->with(['logs' => fn ($q) => $q->limit(5)->with('takenBy:id,name')])->get(),
        );
    }

    public function store(Request $request, Page $page): JsonResponse
    {
        $this->authorizeAccess($request, $page);
        $this->authorizeKind($page, 'meds');

        return response()->json($page->medications()->create($this->validated($request)), 201);
    }

    public function update(Request $request, Page $page, Medication $medication): JsonResponse
    {
        $this->authorizeAccess($request, $page);
        abort_unless($medication->page_id === $page->id, 404);

        $medication->update($this->validated($request, partial: true));

        return response()->json($medication->fresh());
    }

    public function destroy(Request $request, Page $page, Medication $medication): JsonResponse
    {
        $this->authorizeAccess($request, $page);
        abort_unless($medication->page_id === $page->id, 404);

        $medication->delete();

        return response()->json(['ok' => true]);
    }

    /** Registra uma tomada agora (ou em taken_at) e decrementa estoque, se controlado. */
    public function log(Request $request, Page $page, Medication $medication): JsonResponse
    {
        $this->authorizeAccess($request, $page);
        abort_unless($medication->page_id === $page->id, 404);

        $data = $request->validate(['taken_at' => ['nullable', 'date']]);

        $log = $medication->logs()->create([
            'taken_at' => $data['taken_at'] ?? now(),
            'taken_by' => $request->user()->id,
        ]);

        if ($medication->stock !== null && $medication->stock > 0) {
            $medication->decrement('stock');
        }

        return response()->json([
            'log' => $log,
            'medication' => $medication->fresh(),
        ], 201);
    }

    /** @return array<string, mixed> */
    private function validated(Request $request, bool $partial = false): array
    {
        $req = $partial ? 'sometimes' : 'required';

        return $request->validate([
            'person' => [$req, 'string', 'max:100'],
            'name' => [$req, 'string', 'max:255'],
            'dose' => ['nullable', 'string', 'max:255'],
            'schedule_times' => [$req, 'array'],
            'schedule_times.*' => ['string', 'regex:/^\d{2}:\d{2}$/'],
            'controlled' => ['sometimes', 'boolean'],
            'prescription_until' => ['nullable', 'date'],
            'stock' => ['nullable', 'integer', 'min:0'],
            'low_stock_threshold' => ['nullable', 'integer', 'min:0'],
            'notes' => ['nullable', 'string'],
            'active' => ['sometimes', 'boolean'],
        ]);
    }
}
