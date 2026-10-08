import { useMemo } from 'react'
import { useParams } from 'react-router-dom'
import { useDados } from '../state/contexts'

/**
 * Empresa do ambiente atual (/dashboard/empresas/:empresaId) e as notas dela.
 * Os dados vêm do DadosProvider, que só recebe do servidor o que o usuário pode ver:
 * um usuário de empresa nunca tem outra empresa na lista.
 */
export function useEmpresa() {
  const { empresaId } = useParams()
  const id = Number(empresaId)
  const { clientes, notas, status } = useDados()
  const empresa = clientes.find((c) => c.id === id)
  const notasDaEmpresa = useMemo(() => notas.filter((n) => n.clienteId === id), [notas, id])
  return { id, empresa, notas: notasDaEmpresa, carregando: status === 'carregando', status }
}
