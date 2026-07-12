import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'

import { apiFetch } from '../../api'
import type { TaskItem, User } from '../../types'
import { Btn, TextInput, dateBr, inputCls } from '../ui'

export default function TasksPanel({ pageId }: { pageId: number }) {
  const queryClient = useQueryClient()
  const [content, setContent] = useState('')
  const [dueDate, setDueDate] = useState('')
  const [assignee, setAssignee] = useState('')
  const [showDone, setShowDone] = useState(false)

  const { data: tasks = [] } = useQuery({
    queryKey: ['tasks', pageId],
    queryFn: () => apiFetch<TaskItem[]>(`/pages/${pageId}/tasks`),
  })
  const { data: users = [] } = useQuery({
    queryKey: ['users'],
    queryFn: () => apiFetch<User[]>('/users'),
  })

  const invalidate = () => queryClient.invalidateQueries({ queryKey: ['tasks', pageId] })

  const create = useMutation({
    mutationFn: () =>
      apiFetch(`/pages/${pageId}/tasks`, {
        method: 'POST',
        body: JSON.stringify({
          content,
          due_date: dueDate || null,
          assigned_user_id: assignee ? Number(assignee) : null,
        }),
      }),
    onSuccess: () => {
      setContent('')
      setDueDate('')
      invalidate()
    },
  })

  const toggle = useMutation({
    mutationFn: (task: TaskItem) =>
      apiFetch(`/pages/${pageId}/tasks/${task.id}`, {
        method: 'PUT',
        body: JSON.stringify({ done: !task.done }),
      }),
    onSuccess: invalidate,
  })

  const remove = useMutation({
    mutationFn: (id: number) => apiFetch(`/pages/${pageId}/tasks/${id}`, { method: 'DELETE' }),
    onSuccess: invalidate,
  })

  const pending = tasks.filter((t) => !t.done)
  const done = tasks.filter((t) => t.done)
  const today = new Date().toISOString().slice(0, 10)

  return (
    <div className="space-y-6">
      <form
        className="flex flex-wrap items-end gap-2"
        onSubmit={(e) => {
          e.preventDefault()
          if (content.trim()) create.mutate()
        }}
      >
        <TextInput
          value={content}
          onChange={(e) => setContent(e.target.value)}
          placeholder="Nova tarefa…"
          className="min-w-52 flex-1"
        />
        <input type="date" value={dueDate} onChange={(e) => setDueDate(e.target.value)} className={inputCls} />
        <select value={assignee} onChange={(e) => setAssignee(e.target.value)} className={inputCls}>
          <option value="">Ambos</option>
          {users.map((u) => (
            <option key={u.id} value={u.id}>
              {u.name}
            </option>
          ))}
        </select>
        <Btn disabled={!content.trim() || create.isPending}>Adicionar</Btn>
      </form>

      <ul className="space-y-1">
        {pending.map((task) => (
          <li key={task.id} className="group flex items-center gap-3 rounded-md px-2 py-1.5 hover:bg-[#f7f7f5]">
            <input
              type="checkbox"
              checked={false}
              onChange={() => toggle.mutate(task)}
              className="h-4 w-4 accent-[#2383e2]"
            />
            <span className="flex-1 text-sm">{task.content}</span>
            {task.due_date && (
              <span className={`text-xs ${task.due_date < today ? 'font-medium text-red-600' : 'text-[#9b9a97]'}`}>
                {dateBr(task.due_date)}
              </span>
            )}
            <span className="rounded bg-[#f1f1ef] px-1.5 py-0.5 text-xs text-[#787774]">
              {task.assignee?.name ?? 'ambos'}
            </span>
            <button
              onClick={() => remove.mutate(task.id)}
              className="invisible text-xs text-[#9b9a97] group-hover:visible hover:text-red-600"
            >
              ✕
            </button>
          </li>
        ))}
        {pending.length === 0 && <p className="px-2 text-sm text-[#9b9a97]">Nenhuma tarefa pendente. 🎉</p>}
      </ul>

      {done.length > 0 && (
        <div>
          <button onClick={() => setShowDone((v) => !v)} className="text-xs text-[#9b9a97] hover:text-[#37352f]">
            {showDone ? '▾' : '▸'} Concluídas ({done.length})
          </button>
          {showDone && (
            <ul className="mt-1 space-y-1">
              {done.map((task) => (
                <li key={task.id} className="group flex items-center gap-3 rounded-md px-2 py-1.5 hover:bg-[#f7f7f5]">
                  <input
                    type="checkbox"
                    checked
                    onChange={() => toggle.mutate(task)}
                    className="h-4 w-4 accent-[#2383e2]"
                  />
                  <span className="flex-1 text-sm text-[#9b9a97] line-through">{task.content}</span>
                  <button
                    onClick={() => remove.mutate(task.id)}
                    className="invisible text-xs text-[#9b9a97] group-hover:visible hover:text-red-600"
                  >
                    ✕
                  </button>
                </li>
              ))}
            </ul>
          )}
        </div>
      )}
    </div>
  )
}
