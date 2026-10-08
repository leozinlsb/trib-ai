import { useEffect, useMemo } from 'react'
import { useDados } from '../state/contexts'

/** Carrega (com cache) os detalhes das notas e devolve o mapa. */
export function useDetalhes(ids: number[]) {
  const { carregarDetalhes, detalhes, errosDetalhe } = useDados()
  const chave = ids.join(',')
  useEffect(() => {
    if (ids.length) void carregarDetalhes(ids)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [chave, carregarDetalhes])
  const prontos = useMemo(() => ids.filter((id) => detalhes.has(id) || errosDetalhe.has(id)).length, [chave, detalhes, errosDetalhe]) // eslint-disable-line react-hooks/exhaustive-deps
  return { detalhes, errosDetalhe, completo: prontos === ids.length, prontos }
}
