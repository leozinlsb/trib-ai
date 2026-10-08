import { useCallback, useEffect, useMemo, useRef, useState, type ReactNode } from 'react'
import { ApiError } from '../api/client'
import { detalharNota, listarClientes, listarNotas } from '../api/tribia'
import type { Cliente, NotaDetalhe, NotaResumo } from '../api/types'
import { DadosContext, type Conexao, type DadosCtx } from './contexts'

const INTERVALO_PING = 30_000
const PARALELO = 6

function mensagem(e: unknown) {
  return e instanceof Error ? e.message : 'Erro inesperado.'
}

/**
 * Carrega clientes e todas as notas (GET /api/clientes e GET /api/clientes/{id}/notas)
 * e mantém o estado da conexão com o backend.
 */
export function DadosProvider({ children }: { children: ReactNode }) {
  const [clientes, setClientes] = useState<Cliente[]>([])
  const [notas, setNotas] = useState<NotaResumo[]>([])
  const [status, setStatus] = useState<DadosCtx['status']>('carregando')
  const [erro, setErro] = useState<string | null>(null)
  const [carregadoEm, setCarregadoEm] = useState<Date | null>(null)

  const [conexao, setConexao] = useState<Conexao>('verificando')
  const [verificadoEm, setVerificadoEm] = useState<Date | null>(null)

  const [detalhes, setDetalhes] = useState<ReadonlyMap<number, NotaDetalhe>>(new Map())
  const [errosDetalhe, setErrosDetalhe] = useState<ReadonlyMap<number, string>>(new Map())
  const emVoo = useRef(new Set<number>())
  const carregados = useRef(new Set<number>())
  const conexaoRef = useRef(conexao)
  const statusRef = useRef(status)
  useEffect(() => {
    conexaoRef.current = conexao
    statusRef.current = status
  })

  const recarregar = useCallback(async () => {
    setStatus((s) => (s === 'pronto' ? s : 'carregando'))
    try {
      const cs = await listarClientes()
      const listas = await Promise.all(cs.map((c) => listarNotas(c.id)))
      setClientes(cs)
      setNotas(listas.flat())
      setStatus('pronto')
      setErro(null)
      setCarregadoEm(new Date())
      setConexao('online')
      setVerificadoEm(new Date())
    } catch (e) {
      setErro(mensagem(e))
      setStatus('erro')
      if (e instanceof ApiError && e.semConexao) setConexao('offline')
      setVerificadoEm(new Date())
    }
  }, [])

  const verificarConexao = useCallback(async () => {
    try {
      await listarClientes()
      // voltou do ar (ou a carga inicial falhou): recarrega os dados
      if (conexaoRef.current === 'offline' || statusRef.current === 'erro') void recarregar()
      setConexao('online')
    } catch {
      setConexao('offline')
    } finally {
      setVerificadoEm(new Date())
    }
  }, [recarregar])

  useEffect(() => {
    void recarregar()
    const t = window.setInterval(() => void verificarConexao(), INTERVALO_PING)
    return () => window.clearInterval(t)
  }, [recarregar, verificarConexao])

  const carregarDetalhes = useCallback(async (ids: number[]) => {
    const faltam = ids.filter((id) => !carregados.current.has(id) && !emVoo.current.has(id))
    if (faltam.length === 0) return
    faltam.forEach((id) => emVoo.current.add(id))

    const fila = [...faltam]
    const trabalhador = async () => {
      for (let id = fila.shift(); id != null; id = fila.shift()) {
        const atual = id
        try {
          const d = await detalharNota(atual)
          carregados.current.add(atual)
          setDetalhes((m) => new Map(m).set(atual, d))
          setErrosDetalhe((m) => {
            if (!m.has(atual)) return m
            const n = new Map(m)
            n.delete(atual)
            return n
          })
        } catch (e) {
          setErrosDetalhe((m) => new Map(m).set(atual, mensagem(e)))
        } finally {
          emVoo.current.delete(atual)
        }
      }
    }
    await Promise.all(Array.from({ length: Math.min(PARALELO, faltam.length) }, trabalhador))
  }, [])

  // Mantém o detalhe antigo na tela até o novo chegar (sem piscar para "carregando").
  const atualizarDetalhes = useCallback(async (ids: number[]) => {
    ids.forEach((id) => carregados.current.delete(id))
    await carregarDetalhes(ids)
  }, [carregarDetalhes])

  const [versaoFiscal, setVersaoFiscal] = useState(0)
  const marcarAlteracaoFiscal = useCallback(() => setVersaoFiscal((v) => v + 1), [])

  const valor = useMemo<DadosCtx>(
    () => ({
      clientes, notas, status, erro, carregadoEm, recarregar,
      conexao, verificadoEm, verificarConexao,
      carregarDetalhes, atualizarDetalhes, detalhes, errosDetalhe,
      versaoFiscal, marcarAlteracaoFiscal,
    }),
    [clientes, notas, status, erro, carregadoEm, recarregar, conexao, verificadoEm,
      verificarConexao, carregarDetalhes, atualizarDetalhes, detalhes, errosDetalhe, versaoFiscal, marcarAlteracaoFiscal],
  )

  return <DadosContext value={valor}>{children}</DadosContext>
}
