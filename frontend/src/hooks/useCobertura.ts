import { useMemo } from 'react'
import type { NotaResumo } from '../api/types'
import { itemClassificado } from '../lib/aggregate'
import { useDetalhes } from './useDetalhes'

/** Itens das notas (com e sem classificação tributária) — carrega o detalhe das notas, com cache. */
export function useCobertura(notas: NotaResumo[]) {
  const ids = useMemo(() => notas.map((n) => n.id), [notas])
  const { detalhes, completo, prontos } = useDetalhes(ids)
  const porEmpresa = useMemo(() => {
    const m = new Map<number, { total: number; classificados: number }>()
    for (const n of notas) {
      const d = detalhes.get(n.id)
      if (!d) continue
      const acc = m.get(n.clienteId) ?? { total: 0, classificados: 0 }
      acc.total += d.itens.length
      acc.classificados += d.itens.filter(itemClassificado).length
      m.set(n.clienteId, acc)
    }
    return m
  }, [notas, detalhes])
  const total = [...porEmpresa.values()].reduce((s, v) => s + v.total, 0)
  const classificados = [...porEmpresa.values()].reduce((s, v) => s + v.classificados, 0)
  return { total, classificados, pendentes: total - classificados, porEmpresa, completo, prontos, de: ids.length }
}
