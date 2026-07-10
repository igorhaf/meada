<?php

namespace App\Http\Controllers\Professional;

use App\Http\Controllers\Controller;
use App\Models\Event;
use Carbon\CarbonImmutable;
use Illuminate\Http\RedirectResponse;
use Illuminate\Http\Request;
use Illuminate\Support\Str;
use Illuminate\View\View;

class EventController extends Controller
{
    public function index(Request $request): View
    {
        $events = $request->user()->events()->withCount('activeRegistrations as taken')->latest('starts_at')->get();

        return view('professional.events.index', compact('events'));
    }

    public function create(): View
    {
        return view('professional.events.form', ['event' => new Event(['status' => 'draft', 'reminder_hours' => 24, 'modality' => 'presencial', 'type' => 'roda'])]);
    }

    public function store(Request $request): RedirectResponse
    {
        $data = $this->validated($request);
        $data['professional_id'] = $request->user()->id;
        $data['slug'] = $this->uniqueSlug($data['title']);
        $data['timezone'] = $request->user()->timezone ?: config('pindorama.timezone');
        Event::create($data);

        return redirect()->route('professional.events.index')->with('status', 'Evento criado.');
    }

    public function edit(Request $request, Event $event): View
    {
        $this->authorizeEvent($request, $event);

        return view('professional.events.form', compact('event'));
    }

    public function update(Request $request, Event $event): RedirectResponse
    {
        $this->authorizeEvent($request, $event);
        $event->update($this->validated($request));

        return redirect()->route('professional.events.index')->with('status', 'Evento atualizado.');
    }

    public function destroy(Request $request, Event $event): RedirectResponse
    {
        $this->authorizeEvent($request, $event);
        $event->delete();

        return redirect()->route('professional.events.index')->with('status', 'Evento removido.');
    }

    public function registrations(Request $request, Event $event): View
    {
        $this->authorizeEvent($request, $event);
        $registrations = $event->registrations()->latest()->get();

        return view('professional.events.registrations', compact('event', 'registrations'));
    }

    /** @return array<string,mixed> */
    private function validated(Request $request): array
    {
        $data = $request->validate([
            'title' => ['required', 'string', 'max:255'],
            'description' => ['nullable', 'string'],
            'type' => ['required', 'in:roda,curso,certificacao'],
            'modality' => ['required', 'in:presencial,online'],
            'location_label' => ['nullable', 'string', 'max:255'],
            'starts_at' => ['required', 'date'],
            'ends_at' => ['nullable', 'date', 'after:starts_at'],
            'capacity' => ['nullable', 'integer', 'min:0', 'max:100000'],
            'price' => ['nullable', 'numeric', 'min:0'],
            'is_free' => ['nullable', 'boolean'],
            'allow_discount' => ['nullable', 'boolean'],
            'discount_percent' => ['nullable', 'numeric', 'min:0', 'max:100'],
            'status' => ['required', 'in:draft,published,cancelled'],
            'reminder_hours' => ['nullable', 'integer', 'min:0', 'max:168'],
        ]);
        $data['is_free'] = $request->boolean('is_free');
        $data['allow_discount'] = $request->boolean('allow_discount');
        $data['capacity'] = (int) ($data['capacity'] ?? 0);
        $data['price'] = $data['is_free'] ? 0 : (float) ($data['price'] ?? 0);
        $data['discount_percent'] = (float) ($data['discount_percent'] ?? 0);
        $data['reminder_hours'] = (int) ($data['reminder_hours'] ?? 24);

        return $data;
    }

    private function uniqueSlug(string $title): string
    {
        $base = Str::slug($title) ?: 'evento';
        $slug = $base;
        $i = 2;
        while (Event::where('slug', $slug)->exists()) {
            $slug = $base . '-' . $i++;
        }

        return $slug;
    }

    private function authorizeEvent(Request $request, Event $event): void
    {
        abort_unless($event->professional_id === $request->user()->id || $request->user()->isRoot(), 403);
    }
}
