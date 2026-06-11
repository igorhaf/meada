'use client'

import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { z } from 'zod'

import { Button } from '@/components/ui/button'
import { Modal } from '@/components/ui/modal'
import { createService } from '@/lib/supabase/services'

// name obrigatório; description opcional; preço em REAIS (campo amigável), convertido para
// price_cents na submissão. Preço vazio → null (serviço sem preço). Preço presente deve
// ser número >= 0.
const createServiceSchema = z.object({
  name: z.string().min(1, 'Informe o nome do serviço'),
  description: z.string().optional(),
  priceReais: z
    .string()
    .optional()
    .refine(
      (v) => !v || (!Number.isNaN(Number(v.replace(',', '.'))) && Number(v.replace(',', '.')) >= 0),
      'Preço inválido (use número, ex.: 99.90)',
    ),
})

type CreateServiceForm = z.infer<typeof createServiceSchema>

/** Converte "99,90" / "99.90" → 9990 centavos; vazio → null. */
function reaisToCents(v: string | undefined): number | null {
  if (!v || v.trim() === '') return null
  return Math.round(Number(v.replace(',', '.')) * 100)
}

export function CreateServiceDialog({
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
  } = useForm<CreateServiceForm>({ resolver: zodResolver(createServiceSchema) })

  const mutation = useMutation({
    mutationFn: createService,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['my-services'] })
      handleClose()
    },
    onError: (err) => {
      console.error('createService failed:', err)
      setServerError('Erro ao criar serviço. Tente novamente.')
    },
  })

  function handleClose() {
    reset()
    setServerError(null)
    onClose()
  }

  function onSubmit(values: CreateServiceForm) {
    setServerError(null)
    mutation.mutate({
      companyId,
      name: values.name,
      description: values.description?.trim() ? values.description.trim() : null,
      priceCents: reaisToCents(values.priceReais),
    })
  }

  return (
    <Modal open={open} onClose={handleClose} title="Novo serviço">
      <form onSubmit={handleSubmit(onSubmit)} className="space-y-4">
        <div>
          <label htmlFor="name" className="mb-1 block text-sm font-medium">
            Nome
          </label>
          <input
            id="name"
            type="text"
            className="w-full rounded-md border border-border px-3 py-2 text-sm"
            {...register('name')}
          />
          {errors.name && <p className="mt-1 text-sm text-destructive">{errors.name.message}</p>}
        </div>

        <div>
          <label htmlFor="description" className="mb-1 block text-sm font-medium">
            Descrição <span className="text-muted-foreground">(opcional)</span>
          </label>
          <textarea
            id="description"
            rows={3}
            placeholder="O que esse serviço entrega?"
            className="w-full rounded-md border border-border px-3 py-2 text-sm"
            {...register('description')}
          />
        </div>

        <div>
          <label htmlFor="priceReais" className="mb-1 block text-sm font-medium">
            Preço (R$) <span className="text-muted-foreground">(opcional)</span>
          </label>
          <input
            id="priceReais"
            type="text"
            inputMode="decimal"
            placeholder="99.90"
            className="w-full rounded-md border border-border px-3 py-2 text-sm"
            {...register('priceReais')}
          />
          {errors.priceReais && (
            <p className="mt-1 text-sm text-destructive">{errors.priceReais.message}</p>
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
