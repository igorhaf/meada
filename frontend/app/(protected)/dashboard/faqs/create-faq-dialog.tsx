'use client'

import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { z } from 'zod'

import { Button } from '@/components/ui/button'
import { Modal } from '@/components/ui/modal'
import { createFaq } from '@/lib/supabase/faqs'

// question e answer obrigatórios (text NOT NULL no banco). Sem campos opcionais — mais
// simples que o form de service.
const createFaqSchema = z.object({
  question: z.string().min(1, 'Informe a pergunta'),
  answer: z.string().min(1, 'Informe a resposta'),
})

type CreateFaqForm = z.infer<typeof createFaqSchema>

export function CreateFaqDialog({
  open,
  onClose,
  companyId,
}: {
  open: boolean
  onClose: () => void
  companyId: string
}) {
  const queryClient = useQueryClient()
  const [serverError, setServerError] = useState<string | null>(null)

  const {
    register,
    handleSubmit,
    reset,
    formState: { errors, isSubmitting },
  } = useForm<CreateFaqForm>({ resolver: zodResolver(createFaqSchema) })

  const mutation = useMutation({
    mutationFn: createFaq,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['my-faqs'] })
      handleClose()
    },
    onError: (err) => {
      console.error('createFaq failed:', err)
      setServerError('Erro ao criar FAQ. Tente novamente.')
    },
  })

  function handleClose() {
    reset()
    setServerError(null)
    onClose()
  }

  function onSubmit(values: CreateFaqForm) {
    setServerError(null)
    mutation.mutate({
      companyId,
      question: values.question,
      answer: values.answer,
    })
  }

  return (
    <Modal open={open} onClose={handleClose} title="Nova FAQ">
      <form onSubmit={handleSubmit(onSubmit)} className="space-y-4">
        <div>
          <label htmlFor="question" className="mb-1 block text-sm font-medium">
            Pergunta
          </label>
          <input
            id="question"
            type="text"
            placeholder="Vocês atendem aos sábados?"
            className="w-full rounded-md border border-border px-3 py-2 text-sm"
            {...register('question')}
          />
          {errors.question && (
            <p className="mt-1 text-sm text-destructive">{errors.question.message}</p>
          )}
        </div>

        <div>
          <label htmlFor="answer" className="mb-1 block text-sm font-medium">
            Resposta
          </label>
          <textarea
            id="answer"
            rows={4}
            placeholder="Sim, das 9h às 13h."
            className="w-full rounded-md border border-border px-3 py-2 text-sm"
            {...register('answer')}
          />
          {errors.answer && (
            <p className="mt-1 text-sm text-destructive">{errors.answer.message}</p>
          )}
        </div>

        {serverError && <p className="text-sm text-destructive">{serverError}</p>}

        <div className="flex justify-end gap-2 pt-2">
          <Button type="button" variant="outline" onClick={handleClose}>
            Cancelar
          </Button>
          <Button type="submit" disabled={isSubmitting || mutation.isPending}>
            {mutation.isPending ? 'Criando…' : 'Criar'}
          </Button>
        </div>
      </form>
    </Modal>
  )
}
