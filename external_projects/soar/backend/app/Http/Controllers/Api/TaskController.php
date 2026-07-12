<?php

namespace App\Http\Controllers\Api;

use App\Http\Controllers\Concerns\AuthorizesPageAccess;
use App\Http\Controllers\Controller;
use App\Models\Page;
use App\Models\TaskItem;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;

class TaskController extends Controller
{
    use AuthorizesPageAccess;

    public function index(Request $request, Page $page): JsonResponse
    {
        $this->authorizeAccess($request, $page);
        $this->authorizeKind($page, 'tasks');

        return response()->json($page->taskItems()->with('assignee:id,name')->get());
    }

    public function store(Request $request, Page $page): JsonResponse
    {
        $this->authorizeAccess($request, $page);
        $this->authorizeKind($page, 'tasks');

        $data = $request->validate([
            'content' => ['required', 'string', 'max:1000'],
            'assigned_user_id' => ['nullable', 'integer', 'exists:users,id'],
            'due_date' => ['nullable', 'date'],
        ]);

        $data['position'] = ($page->taskItems()->max('position') ?? -1) + 1;
        $item = $page->taskItems()->create($data);

        return response()->json($item->load('assignee:id,name'), 201);
    }

    public function update(Request $request, Page $page, TaskItem $task): JsonResponse
    {
        $this->authorizeAccess($request, $page);
        abort_unless($task->page_id === $page->id, 404);

        $data = $request->validate([
            'content' => ['sometimes', 'required', 'string', 'max:1000'],
            'assigned_user_id' => ['sometimes', 'nullable', 'integer', 'exists:users,id'],
            'due_date' => ['sometimes', 'nullable', 'date'],
            'done' => ['sometimes', 'boolean'],
        ]);

        $task->update($data);

        return response()->json($task->fresh()->load('assignee:id,name'));
    }

    public function destroy(Request $request, Page $page, TaskItem $task): JsonResponse
    {
        $this->authorizeAccess($request, $page);
        abort_unless($task->page_id === $page->id, 404);

        $task->delete();

        return response()->json(['ok' => true]);
    }
}
