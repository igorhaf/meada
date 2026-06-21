'use client'

import { useState } from 'react'

import { cn } from '@/lib/utils'
import { blockSchema } from '@/lib/cms/cms-block-schemas'
import { blockTypeLabel, type CmsRow } from '@/lib/cms/cms-block-type'

/**
 * Painel "explorer" do page builder (árvore root → linhas → colunas → blocos). Substitui a lista flat
 * de blocos da SM-N. Cada nó tem ↑↓✕ (reordenar/excluir) — drag-drop é camada por cima (Fase 4), os
 * botões são o caminho à prova de furo (a11y + fallback). A seleção (linha/coluna/bloco) controla o
 * painel direito de propriedades (3 modos) lá no editor; aqui só emitimos os callbacks.
 */

export type Selection =
  | { kind: 'row'; rowId: string }
  | { kind: 'column'; rowId: string; colId: string }
  | { kind: 'block'; rowId: string; colId: string; blockId: string }
  | null

export type TreePanelProps = {
  tree: CmsRow[]
  selection: Selection
  expanded: Set<string>
  onSelect: (sel: Selection) => void
  onToggle: (rowId: string) => void
  // linhas
  onAddRow: () => void
  onMoveRow: (rowId: string, dir: -1 | 1) => void
  onRemoveRow: (rowId: string) => void
  // colunas
  onAddColumn: (rowId: string) => void
  onMoveColumn: (rowId: string, colId: string, dir: -1 | 1) => void
  onRemoveColumn: (rowId: string, colId: string) => void
  // blocos
  onAddBlock: (rowId: string, colId: string) => void
  onMoveBlock: (rowId: string, colId: string, blockId: string, dir: -1 | 1) => void
  onRemoveBlock: (rowId: string, colId: string, blockId: string) => void
  // drag-drop (Fase 4) — reordenar linhas/colunas, mover bloco entre colunas. Os ↑↓✕ continuam como fallback.
  onReorderRow: (dragId: string, targetId: string) => void
  onReorderColumn: (rowId: string, dragColId: string, targetColId: string) => void
  onMoveBlockAcross: (from: { rowId: string; colId: string; blockId: string }, to: { rowId: string; colId: string }) => void
}

/** Descritor do arrasto em curso (HTML5 nativo). Só um por vez. */
type Drag =
  | { kind: 'row'; rowId: string }
  | { kind: 'column'; rowId: string; colId: string }
  | { kind: 'block'; rowId: string; colId: string; blockId: string }
  | null

function NodeButtons({ onUp, onDown, onRemove, upDisabled, downDisabled, removeLabel }: {
  onUp: () => void; onDown: () => void; onRemove: () => void
  upDisabled: boolean; downDisabled: boolean; removeLabel: string
}) {
  return (
    <span className="flex shrink-0 items-center gap-0.5 opacity-0 transition-opacity group-hover:opacity-100">
      <button type="button" onClick={(e) => { e.stopPropagation(); onUp() }} disabled={upDisabled}
        className="rounded px-1 text-xs hover:bg-muted disabled:opacity-30" aria-label="Subir">↑</button>
      <button type="button" onClick={(e) => { e.stopPropagation(); onDown() }} disabled={downDisabled}
        className="rounded px-1 text-xs hover:bg-muted disabled:opacity-30" aria-label="Descer">↓</button>
      <button type="button" onClick={(e) => { e.stopPropagation(); onRemove() }}
        className="rounded px-1 text-xs text-destructive hover:bg-destructive/10" aria-label={removeLabel}>✕</button>
    </span>
  )
}

export function TreePanel(p: TreePanelProps) {
  const sel = p.selection
  const [drag, setDrag] = useState<Drag>(null)
  const [over, setOver] = useState<string | null>(null) // id do nó sob o cursor (highlight)

  // só permite soltar onde o tipo arrastado faz sentido; devolve o handler de drop ou null.
  function dropOnRow(rowId: string) {
    if (drag?.kind !== 'row') return null
    return () => { if (drag.rowId !== rowId) p.onReorderRow(drag.rowId, rowId); setDrag(null); setOver(null) }
  }
  function dropOnColumn(rowId: string, colId: string) {
    if (drag?.kind === 'column' && drag.rowId === rowId) {
      return () => { if (drag.colId !== colId) p.onReorderColumn(rowId, drag.colId, colId); setDrag(null); setOver(null) }
    }
    if (drag?.kind === 'block') {
      // soltar um bloco SOBRE uma coluna = mover o bloco pro fim daquela coluna (entre colunas ou na mesma).
      return () => {
        if (drag.colId !== colId) p.onMoveBlockAcross({ rowId: drag.rowId, colId: drag.colId, blockId: drag.blockId }, { rowId, colId })
        setDrag(null); setOver(null)
      }
    }
    return null
  }
  function endDrag() { setDrag(null); setOver(null) }

  return (
    <div className="flex h-full flex-col">
      <div className="border-b border-border p-3">
        <button type="button" onClick={p.onAddRow}
          className="w-full rounded-md bg-primary px-3 py-2 text-sm font-medium text-primary-foreground hover:bg-primary/90">
          + Linha
        </button>
      </div>
      <div className="flex-1 overflow-y-auto p-2">
        {/* raiz */}
        <div className="mb-1 flex items-center gap-1 px-1 text-xs font-semibold uppercase tracking-wide text-muted-foreground">
          <span aria-hidden>📁</span> root
        </div>
        {p.tree.length === 0 && (
          <p className="rounded-lg border border-dashed border-border p-4 text-center text-xs text-muted-foreground">
            Nenhuma linha ainda. Clique em “+ Linha”.
          </p>
        )}
        <ul className="space-y-1">
          {p.tree.map((row, ri) => {
            const open = p.expanded.has(row.id)
            const rowSel = sel?.kind === 'row' && sel.rowId === row.id
            return (
              <li key={row.id}>
                {/* nó LINHA */}
                <div
                  draggable
                  onDragStart={(e) => { setDrag({ kind: 'row', rowId: row.id }); e.dataTransfer.effectAllowed = 'move' }}
                  onDragEnd={endDrag}
                  onDragOver={(e) => { if (dropOnRow(row.id)) { e.preventDefault(); setOver(row.id) } }}
                  onDragLeave={() => setOver((o) => (o === row.id ? null : o))}
                  onDrop={(e) => { const fn = dropOnRow(row.id); if (fn) { e.preventDefault(); fn() } }}
                  className={cn('group flex items-center gap-1 rounded-md border px-2 py-1.5 text-sm',
                    rowSel ? 'border-primary bg-primary/5' : 'border-border hover:bg-muted/50',
                    over === row.id && 'ring-2 ring-primary')}>
                  <span aria-hidden className="shrink-0 cursor-grab text-muted-foreground" title="Arraste para reordenar">⋮⋮</span>
                  <button type="button" onClick={() => p.onToggle(row.id)} aria-label={open ? 'Recolher' : 'Expandir'}
                    className="shrink-0 rounded px-0.5 text-muted-foreground hover:bg-muted">{open ? '▾' : '▸'}</button>
                  <button type="button" onClick={() => p.onSelect({ kind: 'row', rowId: row.id })}
                    className="flex min-w-0 flex-1 items-center gap-2 text-left">
                    <span aria-hidden>📂</span>
                    <span className="truncate">Linha {ri + 1}</span>
                    <span className="shrink-0 text-xs text-muted-foreground">({row.columns.length} col)</span>
                  </button>
                  <NodeButtons onUp={() => p.onMoveRow(row.id, -1)} onDown={() => p.onMoveRow(row.id, 1)}
                    onRemove={() => p.onRemoveRow(row.id)} upDisabled={ri === 0} downDisabled={ri === p.tree.length - 1}
                    removeLabel="Excluir linha" />
                </div>

                {/* colunas da linha */}
                {open && (
                  <div className="ml-3 mt-1 space-y-1 border-l border-border pl-2">
                    {row.columns.map((col, ci) => {
                      const colSel = sel?.kind === 'column' && sel.colId === col.id
                      return (
                        <div key={col.id}>
                          {/* nó COLUNA */}
                          <div
                            draggable
                            onDragStart={(e) => { e.stopPropagation(); setDrag({ kind: 'column', rowId: row.id, colId: col.id }); e.dataTransfer.effectAllowed = 'move' }}
                            onDragEnd={endDrag}
                            onDragOver={(e) => { if (dropOnColumn(row.id, col.id)) { e.preventDefault(); setOver(col.id) } }}
                            onDragLeave={() => setOver((o) => (o === col.id ? null : o))}
                            onDrop={(e) => { const fn = dropOnColumn(row.id, col.id); if (fn) { e.preventDefault(); e.stopPropagation(); fn() } }}
                            className={cn('group flex items-center gap-1 rounded-md border px-2 py-1.5 text-sm',
                              colSel ? 'border-primary bg-primary/5' : 'border-border hover:bg-muted/50',
                              over === col.id && 'ring-2 ring-primary')}>
                            <span aria-hidden className="shrink-0 cursor-grab text-muted-foreground" title="Arraste para reordenar">⋮⋮</span>
                            <button type="button" onClick={() => p.onSelect({ kind: 'column', rowId: row.id, colId: col.id })}
                              className="flex min-w-0 flex-1 items-center gap-2 text-left">
                              <span aria-hidden>▏</span>
                              <span className="truncate">Coluna {ci + 1}</span>
                              <span className="shrink-0 text-xs text-muted-foreground">(w{typeof col.width === 'number' ? col.width : 'auto'})</span>
                            </button>
                            <NodeButtons onUp={() => p.onMoveColumn(row.id, col.id, -1)} onDown={() => p.onMoveColumn(row.id, col.id, 1)}
                              onRemove={() => p.onRemoveColumn(row.id, col.id)} upDisabled={ci === 0} downDisabled={ci === row.columns.length - 1}
                              removeLabel="Excluir coluna" />
                          </div>

                          {/* blocos da coluna */}
                          <div className="ml-3 mt-1 space-y-1 border-l border-border pl-2">
                            {col.blocks.map((b, bi) => {
                              const s = blockSchema(b.type)
                              const bSel = sel?.kind === 'block' && sel.blockId === b.id
                              return (
                                <div key={b.id}
                                  draggable
                                  onDragStart={(e) => { e.stopPropagation(); setDrag({ kind: 'block', rowId: row.id, colId: col.id, blockId: b.id }); e.dataTransfer.effectAllowed = 'move' }}
                                  onDragEnd={endDrag}
                                  className={cn('group flex items-center gap-1 rounded-md border px-2 py-1.5 text-sm',
                                    bSel ? 'border-primary bg-primary/5' : 'border-border hover:bg-muted/50')}>
                                  <span aria-hidden className="shrink-0 cursor-grab text-muted-foreground" title="Arraste para outra coluna">⋮⋮</span>
                                  <button type="button" onClick={() => p.onSelect({ kind: 'block', rowId: row.id, colId: col.id, blockId: b.id })}
                                    className="flex min-w-0 flex-1 items-center gap-2 text-left">
                                    <span aria-hidden>{s?.emoji ?? '▫️'}</span>
                                    <span className="truncate">{s?.label ?? blockTypeLabel(b.type)}</span>
                                  </button>
                                  <NodeButtons onUp={() => p.onMoveBlock(row.id, col.id, b.id, -1)} onDown={() => p.onMoveBlock(row.id, col.id, b.id, 1)}
                                    onRemove={() => p.onRemoveBlock(row.id, col.id, b.id)} upDisabled={bi === 0} downDisabled={bi === col.blocks.length - 1}
                                    removeLabel="Excluir bloco" />
                                </div>
                              )
                            })}
                            <button type="button" onClick={() => p.onAddBlock(row.id, col.id)}
                              className="w-full rounded-md border border-dashed border-border px-2 py-1.5 text-left text-xs text-muted-foreground hover:border-primary hover:text-foreground">
                              + bloco nesta coluna
                            </button>
                          </div>
                        </div>
                      )
                    })}
                    <button type="button" onClick={() => p.onAddColumn(row.id)}
                      className="w-full rounded-md border border-dashed border-border px-2 py-1.5 text-left text-xs text-muted-foreground hover:border-primary hover:text-foreground">
                      + coluna nesta linha
                    </button>
                  </div>
                )}
              </li>
            )
          })}
        </ul>
      </div>
    </div>
  )
}
