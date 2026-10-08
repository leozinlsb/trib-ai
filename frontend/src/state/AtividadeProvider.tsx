import { useCallback, useMemo, useState, type ReactNode } from 'react'
import { ApiError } from '../api/client'
import { enviarNotas } from '../api/tribia'
import type { Rejeicao } from '../api/types'
import { UploadModal } from '../components/upload/UploadModal'
import { nomeCliente } from '../lib/format'
import { gravarLocal, lerLocal } from '../lib/storage'
import { AtividadeContext, useAuth, useDados, useToast, type Atividade, type AtividadeCtx } from './contexts'

/** Por usuário: quem usar o mesmo navegador não vê os envios de outra pessoa. */
const chave = (usuarioId: number | undefined) => `tribia.atividades.${usuarioId ?? 'anonimo'}`
const MAX = 100

/**
 * Envia XMLs ao backend e registra o resultado de cada envio neste navegador
 * (a API não tem endpoint de histórico de uploads).
 */
export function AtividadeProvider({ children }: { children: ReactNode }) {
  const { clientes, recarregar } = useDados()
  const { usuario } = useAuth()
  const CHAVE = chave(usuario?.id)
  const { mostrar } = useToast()
  const [atividades, setAtividades] = useState<Atividade[]>(() => lerLocal<Atividade[]>(CHAVE, []))
  const [emProcessamento, setEmProcessamento] = useState(0)
  const [modal, setModal] = useState<{ aberto: boolean; clienteId?: number; travado?: boolean }>({ aberto: false })

  const salvar = useCallback((fn: (a: Atividade[]) => Atividade[]) => {
    setAtividades((atual) => {
      const novo = fn(atual).slice(0, MAX)
      gravarLocal(CHAVE, novo)
      return novo
    })
  }, [CHAVE])

  const enviar = useCallback<AtividadeCtx['enviar']>(
    async (clienteId, arquivos) => {
      const cliente = clientes.find((c) => c.id === clienteId)
      const base: Atividade = {
        id: `${Date.now()}-${Math.random().toString(36).slice(2, 7)}`,
        quando: new Date().toISOString(),
        clienteId,
        clienteNome: nomeCliente(cliente),
        arquivos: arquivos.map((f) => f.name),
        importadas: [],
        rejeitadas: [],
        lida: false,
      }
      setEmProcessamento((n) => n + arquivos.length)
      let atividade: Atividade
      try {
        const r = await enviarNotas(clienteId, arquivos)
        atividade = {
          ...base,
          importadas: r.importadas.map((n) => ({ id: n.id, numero: n.numero, tipo: n.tipo })),
          rejeitadas: r.rejeitadas,
        }
      } catch (e) {
        if (e instanceof ApiError && !e.semConexao) {
          // 1 arquivo: o erro é o do próprio arquivo; vários: lista "rejeitadas" no ProblemDetail
          const rejeitadas: Rejeicao[] =
            e.problem?.rejeitadas ??
            (arquivos.length === 1 ? [{ arquivo: arquivos[0]!.name, status: e.status, motivo: e.message }] : [])
          atividade = { ...base, rejeitadas, erro: rejeitadas.length ? undefined : e.message }
        } else {
          atividade = { ...base, erro: e instanceof Error ? e.message : 'Falha no envio.' }
        }
      } finally {
        setEmProcessamento((n) => Math.max(0, n - arquivos.length))
      }

      salvar((a) => [atividade, ...a])
      const ok = atividade.importadas.length > 0
      if (ok) {
        await recarregar()
        mostrar({
          tipo: 'sucesso',
          titulo: `${atividade.importadas.length} nota(s) importada(s)`,
          texto: atividade.rejeitadas.length
            ? `${atividade.rejeitadas.length} arquivo(s) recusado(s).`
            : `Cliente: ${atividade.clienteNome}`,
        })
      } else {
        mostrar({
          tipo: 'erro',
          titulo: 'Nenhuma nota importada',
          texto: atividade.erro ?? atividade.rejeitadas[0]?.motivo,
        })
      }
      return { ok, atividade }
    },
    [clientes, recarregar, mostrar, salvar],
  )

  const marcarLidas = useCallback(() => salvar((a) => a.map((x) => (x.lida ? x : { ...x, lida: true }))), [salvar])
  const limpar = useCallback(() => salvar(() => []), [salvar])
  const abrirUpload = useCallback(
    (clienteId?: number, travado = false) => setModal({ aberto: true, clienteId, travado }),
    [],
  )

  const valor = useMemo<AtividadeCtx>(
    () => ({
      atividades,
      emProcessamento,
      naoLidas: atividades.filter((a) => !a.lida).length,
      marcarLidas,
      limpar,
      enviar,
      abrirUpload,
    }),
    [atividades, emProcessamento, marcarLidas, limpar, enviar, abrirUpload],
  )

  return (
    <AtividadeContext value={valor}>
      {children}
      {modal.aberto && (
        <UploadModal
          clienteInicial={modal.clienteId}
          travado={modal.travado}
          onFechar={() => setModal({ aberto: false })}
        />
      )}
    </AtividadeContext>
  )
}
