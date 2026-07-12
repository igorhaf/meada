<?php

namespace App\Http\Controllers\Admin;

use App\Http\Controllers\Controller;
use App\Models\Course;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\RedirectResponse;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\DB;
use Illuminate\View\View;

class CourseController extends Controller
{
    public function index(): View
    {
        return view('admin.courses.index', [
            'courses' => Course::ordered()->get(),
        ]);
    }

    public function create(): View
    {
        return view('admin.courses.create');
    }

    public function store(Request $request): RedirectResponse
    {
        $data = $this->validated($request);
        $data['sort_order'] = (int) Course::max('sort_order') + 1;

        Course::create($data);

        return redirect()->route('admin.cursos.index')->with('status', 'Curso criado.');
    }

    public function edit(Course $course): View
    {
        return view('admin.courses.edit', compact('course'));
    }

    public function update(Request $request, Course $course): RedirectResponse
    {
        $course->update($this->validated($request));

        return redirect()->route('admin.cursos.index')->with('status', 'Curso atualizado.');
    }

    public function destroy(Request $request, Course $course): JsonResponse|RedirectResponse
    {
        $course->delete();

        if ($request->wantsJson()) {
            return response()->json(['ok' => true]);
        }

        return redirect()->route('admin.cursos.index')->with('status', 'Curso excluído.');
    }

    public function reorder(Request $request): JsonResponse
    {
        $data = $request->validate([
            'ids' => ['required', 'array'],
            'ids.*' => ['integer', 'exists:courses,id'],
        ]);

        DB::transaction(function () use ($data): void {
            foreach ($data['ids'] as $position => $id) {
                Course::whereKey($id)->update(['sort_order' => $position]);
            }
        });

        return response()->json(['ok' => true]);
    }

    public function toggle(Course $course): JsonResponse
    {
        $course->update(['is_active' => ! $course->is_active]);

        return response()->json(['ok' => true, 'is_active' => $course->is_active]);
    }

    private function validated(Request $request): array
    {
        $data = $request->validate([
            'tag_label' => ['required', 'string', 'max:255'],
            'tag_style' => ['required', 'in:accent,accent-2,neutral'],
            'title' => ['required', 'string', 'max:255'],
            'description' => ['required', 'string'],
            'meta_info' => ['nullable', 'string', 'max:255'],
        ]);

        $data['is_active'] = $request->boolean('is_active');

        return $data;
    }
}
