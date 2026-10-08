import { useEffect, useMemo, useState } from 'react'
import { Link, useNavigate, useParams, useSearchParams } from 'react-router-dom'
import { ChevronRight, Upload } from 'lucide-react'
import { listarNotas } from '../api/tribia'
import type { NotaResumo, TipoNota } from '../api/types'
import { Card, Carregando, ErroEstado, Paginacao, Segmented, Vazio } from '../components/ui'
import { BadgeTipo } from '../components/notas'
import { EnviosRecentes } from '../components/EnviosRecentes'
import { competencias, maisRecentes } from '../lib/aggregate'
import { capitalizar, fmtCnpj, fmtCompetencia, fmtData, fmtMoeda, nomeCliente, normalizar, tituloNota } from '../lib/format'
import { rotaNota } from '../lib/rotas'
import { useAtividades, useDados, usePreferencias } from '../state/contexts'

type FiltroTipo = 'TODOS' | TipoNota

/**
 * Lista de notas. Dentro de uma empresa (/empresas/:id/documentos) mostra só as dela e os envios recentes;
 * na visão do administrador (/dashboard/documentos), todas as empresas com filtro.
 */
export function Documentos() {
  const { empresaId } = useParams()
  const naEmpresa = empresaId != null
  const { clientes, notas: todas, status: statusGlobal } = useDados()
  const { abrirUpload } = useAtividades()
  const { prefs } = usePreferencias()
  const navigate = useNavigate()
  const [params, setParams] = useSearchParams()

  const clienteId = naEmpresa ? Number(empresaId) : params.get('empresa') ? Number(params.get('empresa')) : null
  const tipo = (params.get('tipo') as FiltroTipo | null) ?? 'TODOS'
  const competencia = params.get('competencia') ?? ''
  const [busca, setBusca] = useState(params.get('q') ?? '')
  const [pagina, setPagina] = useState(1)

  const [notas, setNotas] = useState<NotaResumo[] | null>(null)
  const [erro, setErro] = useState<string | null>(null)
  const [tentativa, setTentativa] = useState(0)

  const empresa = clientes.find((c) => c.id === clienteId)

  const atualizar = (k: string, v: string | null) => {
    const p = new URLSearchParams(params)
    if (v) p.set(k, v)
    else p.delete(k)
    setParams(p, { replace: true })
    setPagina(1)
  }

  // Filtros de tipo e competência vão para o servidor
  useEffect(() => {
    if (statusGlobal !== 'pronto') return
    const ctrl = new AbortController()
    const alvos = clienteId ? clientes.filter((c) => c.id === clienteId) : clientes
    setNotas(null)
    setErro(null)
    Promise.all(
      alvos.map((c) =>
        listarNotas(c.id, { tipo: tipo === 'TODOS' ? undefined : tipo, competencia: competencia || undefined }, ctrl.signal),
      ),
    )
      .then((ls) => setNotas(maisRecentes(ls.flat())))
      .catch((e) => {
        if (e instanceof DOMException && e.name === 'AbortError') return
        setErro(e instanceof Error ? e.message : 'Falha ao carregar notas.')
      })
    return () => ctrl.abort()
    // `todas` muda após um upload: recarrega a lista filtrada
  }, [clienteId, tipo, competencia, clientes, todas, statusGlobal, tentativa])

  const porId = useMemo(() => new Map(clientes.map((c) => [c.id, c])), [clientes])
  const comps = useMemo(
    () => competencias(clienteId ? todas.filter((n) => n.clienteId === clienteId) : todas),
    [todas, clienteId],
  )

  const filtradas = useMemo(() => {
    if (!notas) return []
    const q = normalizar(busca.trim())
    if (!q) return notas
    return notas.filter((n) =>
      [n.numero, n.chave, n.contraparteNome, n.contraparteCnpj].some((c) => c != null && normalizar(String(c)).includes(q)),
    )
  }, [notas, busca])

  const pp = prefs.itensPorPagina
  const visiveis = filtradas.slice((pagina - 1) * pp, pagina * pp)
  const total = filtradas.reduce((s, n) => s + n.valorTotal, 0)
  const podeEnviar = naEmpresa ? !!empresa?.ativo : clientes.some((c) => c.ativo)

  return (
    <>
      <div className="page-head">
        <div>
          <h1>Documentos</h1>
          <p>
            {naEmpresa
              ? 'Envie as notas fiscais da empresa e acompanhe o processamento'
              : 'Notas fiscais (NF-e) de todas as empresas do escritório'}
          </p>
        </div>
        <div className="page-head__actions">
          <button
            className="btn btn--primary"
            onClick={() => abrirUpload(clienteId ?? undefined, naEmpresa)}
            disabled={!podeEnviar}
            title={!podeEnviar && naEmpresa ? 'Empresa desativada: reative-a para enviar notas' : undefined}
          >
            <Upload size={18} strokeWidth={2.1} /> Enviar notas
          </button>
        </div>
      </div>

      {naEmpresa && clienteId && (
        <Card titulo="Envios recentes" sub="Resultado de cada envio de XML, arquivo por arquivo." className="mb-gap">
          <EnviosRecentes clienteId={clienteId} limite={3} />
        </Card>
      )}

      <Card corpo="nenhum" titulo={naEmpresa ? 'Notas fiscais' : undefined}>
        <div className="filters">
          {!naEmpresa && (
            <div className="field">
              <label className="field__label" htmlFor="f-empresa">Empresa</label>
              <select id="f-empresa" className="select" value={clienteId ?? ''} onChange={(e) => atualizar('empresa', e.target.value || null)}>
                <option value="">Todas as empresas</option>
                {clientes.map((c) => (
                  <option key={c.id} value={c.id}>{nomeCliente(c)}{c.ativo ? '' : ' (desativada)'}</option>
                ))}
              </select>
            </div>
          )}
          <div className="field" style={{ width: 'auto' }}>
            <span className="field__label">Tipo</span>
            <Segmented<FiltroTipo>
              rotulo="Tipo da nota"
              valor={tipo}
              onChange={(v) => atualizar('tipo', v === 'TODOS' ? null : v)}
              opcoes={[
                { valor: 'TODOS', rotulo: 'Todas' },
                { valor: 'ENTRADA', rotulo: 'Compras' },
                { valor: 'SAIDA', rotulo: 'Vendas' },
              ]}
            />
          </div>
          <div className="field">
            <label className="field__label" htmlFor="f-comp">Competência</label>
            <select id="f-comp" className="select" value={competencia} onChange={(e) => atualizar('competencia', e.target.value || null)}>
              <option value="">Todas</option>
              {comps.map((c) => (
                <option key={c} value={c}>{fmtCompetencia(c, true)}</option>
              ))}
            </select>
          </div>
          <div className="field field--grow">
            <label className="field__label" htmlFor="f-busca">Buscar</label>
            <input
              id="f-busca"
              className="input"
              type="search"
              placeholder="Número, chave, contraparte ou CNPJ"
              value={busca}
              onChange={(e) => {
                setBusca(e.target.value)
                setPagina(1)
              }}
            />
          </div>
        </div>

        {statusGlobal === 'carregando' || (notas === null && !erro) ? (
          <Carregando linhas={6} />
        ) : erro ? (
          <ErroEstado mensagem={erro} onTentar={() => setTentativa((t) => t + 1)} />
        ) : filtradas.length === 0 ? (
          <Vazio titulo="Nenhuma nota encontrada">
            {notas && notas.length > 0
              ? 'Ajuste a busca para ver resultados.'
              : naEmpresa
                ? 'Esta empresa ainda não tem notas para os filtros escolhidos.'
                : 'Não há notas para os filtros escolhidos.'}
          </Vazio>
        ) : (
          <>
            <div className="table-wrap">
              <table className="table table--compact">
                <thead>
                  <tr>
                    <th>Documento</th>
                    {!clienteId && <th>Empresa</th>}
                    <th>Tipo</th>
                    <th>Contraparte</th>
                    <th>Emissão</th>
                    <th className="right">Itens</th>
                    <th className="right">Valor</th>
                    <th aria-label="Abrir" />
                  </tr>
                </thead>
                <tbody>
                  {visiveis.map((n) => (
                    <tr key={n.id} className="is-link" onClick={() => navigate(rotaNota(n.clienteId, n.id))}>
                      <td>
                        <Link to={rotaNota(n.clienteId, n.id)} className="cell-main nowrap" style={{ color: 'inherit' }} onClick={(e) => e.stopPropagation()}>
                          {tituloNota(n)}
                        </Link>
                        <div className="cell-sub">{fmtCompetencia(n.competencia)}</div>
                      </td>
                      {!clienteId && <td>{nomeCliente(porId.get(n.clienteId))}</td>}
                      <td><BadgeTipo tipo={n.tipo} sm /></td>
                      <td>
                        <div>{capitalizar(n.contraparteNome)}</div>
                        <div className="cell-sub num">{fmtCnpj(n.contraparteCnpj)}</div>
                      </td>
                      <td className="num">{fmtData(n.dataEmissao)}</td>
                      <td className="right num">{n.quantidadeItens}</td>
                      <td className="right num">{fmtMoeda(n.valorTotal)}</td>
                      <td className="right"><ChevronRight size={16} color="var(--muted)" /></td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            <Paginacao
              pagina={pagina}
              total={filtradas.length}
              porPagina={pp}
              onChange={setPagina}
              resumo={<> nota(s) · total {fmtMoeda(total)}</>}
            />
          </>
        )}
      </Card>
    </>
  )
}
