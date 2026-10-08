import { useEffect, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { CircleCheck, FileSearch, FlaskConical, LoaderCircle, Plus, UserSearch } from 'lucide-react'
import {
  ETAPAS_PROCESSAMENTO, emAndamento, indicadores as buscarIndicadores, listarAnalises, servicoIndisponivel,
  type AnaliseResumo, type IndicadoresFiscais, type Pagina, type StatusAnalise,
} from '../../api/inteligenciaFiscal'
import { CabecalhoEmpresa, EstadoEmpresa } from '../../components/empresas/CabecalhoEmpresa'
import { BadgeStatus, ServicoIndisponivel } from '../../components/fiscal/Comum'
import { Card, Carregando, ErroEstado, KpiCard, Paginacao, Vazio } from '../../components/ui'
import { useEmpresa } from '../../hooks/useEmpresa'
import { fmtNcm, STATUS } from '../../lib/fiscal'
import { fmtDataHora, fmtNumero } from '../../lib/format'
import { rotaAnaliseFiscal } from '../../lib/rotas'
import { usePreferencias } from '../../state/contexts'

type Estado<T> = { tipo: 'carregando' } | { tipo: 'ok'; dados: T } | { tipo: 'indisponivel' } | { tipo: 'erro'; msg: string }

const STATUS_FILTRO: StatusAnalise[] = [...ETAPAS_PROCESSAMENTO, 'AGUARDANDO_REVISAO', 'INFORMACOES_INSUFICIENTES', 'FALHA']

/** Inteligência Fiscal da empresa: indicadores, histórico de análises e início de uma nova análise. */
export function InteligenciaFiscal() {
  const { id, empresa, carregando } = useEmpresa()
  const { prefs } = usePreferencias()
  const navigate = useNavigate()

  const [busca, setBusca] = useState('')
  const [q, setQ] = useState('')
  const [status, setStatus] = useState<StatusAnalise | ''>('')
  const [de, setDe] = useState('')
  const [ate, setAte] = useState('')
  const [pagina, setPagina] = useState(1)
  const [tentativa, setTentativa] = useState(0)

  const [kpis, setKpis] = useState<Estado<IndicadoresFiscais>>({ tipo: 'carregando' })
  const [lista, setLista] = useState<Estado<Pagina<AnaliseResumo>>>({ tipo: 'carregando' })

  // busca com atraso, para não chamar o servidor a cada tecla
  useEffect(() => {
    const t = window.setTimeout(() => {
      setQ(busca.trim())
      setPagina(1)
    }, 350)
    return () => window.clearTimeout(t)
  }, [busca])

  useEffect(() => {
    const ctrl = new AbortController()
    setKpis({ tipo: 'carregando' })
    buscarIndicadores(id, ctrl.signal)
      .then((dados) => setKpis({ tipo: 'ok', dados }))
      .catch((e) => {
        if (e instanceof DOMException && e.name === 'AbortError') return
        setKpis(servicoIndisponivel(e) ? { tipo: 'indisponivel' } : { tipo: 'erro', msg: e.message })
      })
    return () => ctrl.abort()
  }, [id, tentativa])

  useEffect(() => {
    const ctrl = new AbortController()
    let timer: number | undefined
    const carregar = (silencioso = false) => {
      if (!silencioso) setLista({ tipo: 'carregando' })
      listarAnalises(id, { q, status, de, ate, pagina: pagina - 1, tamanho: prefs.itensPorPagina }, ctrl.signal)
        .then((dados) => {
          setLista({ tipo: 'ok', dados })
          // enquanto houver análise em andamento, atualiza a lista periodicamente
          if (dados.itens.some((a) => emAndamento(a.status))) timer = window.setTimeout(() => carregar(true), 8000)
        })
        .catch((e) => {
          if (e instanceof DOMException && e.name === 'AbortError') return
          setLista(servicoIndisponivel(e) ? { tipo: 'indisponivel' } : { tipo: 'erro', msg: e.message })
        })
    }
    carregar()
    return () => {
      ctrl.abort()
      window.clearTimeout(timer)
    }
  }, [id, q, status, de, ate, pagina, prefs.itensPorPagina, tentativa])

  if (!empresa) return <EstadoEmpresa carregando={carregando} />

  const indisponivel = kpis.tipo === 'indisponivel' || lista.tipo === 'indisponivel'
  const k = kpis.tipo === 'ok' ? kpis.dados : null
  const filtrando = !!(q || status || de || ate)

  return (
    <>
      <CabecalhoEmpresa
        empresa={empresa}
        secao="Inteligência Fiscal"
        sub="Analise mercadorias, encontre classificações NCM e consulte validações fiscais em um único lugar."
        acoes={
          <Link
            to={rotaAnaliseFiscal(id, 'nova')}
            className={`btn btn--verde${empresa.ativo ? '' : ' is-desabilitado'}`}
            aria-disabled={!empresa.ativo}
            onClick={(e) => !empresa.ativo && e.preventDefault()}
            title={empresa.ativo ? undefined : 'Empresa desativada'}
          >
            <Plus size={19} strokeWidth={2.2} /> Nova análise
          </Link>
        }
      />

      {indisponivel && (
        <div className="mb-gap">
          <ServicoIndisponivel>
            <p className="cell-sub" style={{ marginTop: 8 }}>
              Você já pode conhecer o formulário de <Link to={rotaAnaliseFiscal(id, 'nova')}>nova análise</Link>.
            </p>
          </ServicoIndisponivel>
        </div>
      )}
      {import.meta.env.DEV && indisponivel && (
        <div className="aviso-dev mb-gap">
          <FlaskConical size={16} />
          <span>
            Ambiente de desenvolvimento: <Link to={rotaAnaliseFiscal(id, 'exemplo')}>ver a tela de resultado com dados fictícios de exemplo</Link>.
          </span>
        </div>
      )}

      <div className="grid-kpi">
        <KpiCard
          rotulo="Total de Análises"
          valor={k ? fmtNumero(k.total) : '—'}
          indisponivel={!k}
          dica={k ? 'classificações solicitadas' : indisponivel ? 'Aguardando o serviço de análise' : undefined}
          carregando={kpis.tipo === 'carregando'}
          arte={<FileSearch size={28} strokeWidth={1.6} />}
        />
        <KpiCard
          rotulo="Análises Concluídas"
          valor={k ? fmtNumero(k.concluidas) : '—'}
          indisponivel={!k}
          dica={k ? 'com resultado disponível' : undefined}
          carregando={kpis.tipo === 'carregando'}
          arte={<CircleCheck size={28} strokeWidth={1.6} color={k?.concluidas ? 'var(--green-600)' : undefined} />}
        />
        <KpiCard
          rotulo="Em Processamento"
          valor={k ? fmtNumero(k.emProcessamento) : '—'}
          indisponivel={!k}
          dica={k ? (k.emProcessamento ? 'atualizando automaticamente' : 'nenhuma em andamento') : undefined}
          carregando={kpis.tipo === 'carregando'}
          arte={<LoaderCircle size={28} strokeWidth={1.6} className={k?.emProcessamento ? 'spin' : undefined} color={k?.emProcessamento ? 'var(--green-600)' : undefined} />}
        />
        <KpiCard
          rotulo="Aguardando Revisão"
          valor={k ? fmtNumero(k.aguardandoRevisao) : '—'}
          indisponivel={!k}
          dica={k ? (k.aguardandoRevisao ? 'precisam de verificação humana' : 'nada pendente') : undefined}
          carregando={kpis.tipo === 'carregando'}
          arte={<UserSearch size={28} strokeWidth={1.6} color={k?.aguardandoRevisao ? 'var(--warning)' : undefined} />}
        />
      </div>

      <Card titulo="Histórico de análises" sub="Classificações fiscais solicitadas para esta empresa." corpo="nenhum">
        <div className="filters" style={{ marginTop: 8 }}>
          <div className="field field--grow">
            <label className="field__label" htmlFor="if-busca">Buscar mercadoria</label>
            <input id="if-busca" className="input" type="search" placeholder="Nome ou NCM" value={busca} onChange={(e) => setBusca(e.target.value)} disabled={lista.tipo === 'indisponivel'} />
          </div>
          <div className="field">
            <label className="field__label" htmlFor="if-status">Status</label>
            <select
              id="if-status"
              className="select"
              value={status}
              onChange={(e) => {
                setStatus(e.target.value as StatusAnalise | '')
                setPagina(1)
              }}
              disabled={lista.tipo === 'indisponivel'}
            >
              <option value="">Todos</option>
              {STATUS_FILTRO.map((s) => <option key={s} value={s}>{STATUS[s].rotulo}</option>)}
            </select>
          </div>
          <div className="field" style={{ width: 160 }}>
            <label className="field__label" htmlFor="if-de">De</label>
            <input id="if-de" className="input" type="date" value={de} max={ate || undefined} onChange={(e) => { setDe(e.target.value); setPagina(1) }} disabled={lista.tipo === 'indisponivel'} />
          </div>
          <div className="field" style={{ width: 160 }}>
            <label className="field__label" htmlFor="if-ate">Até</label>
            <input id="if-ate" className="input" type="date" value={ate} min={de || undefined} onChange={(e) => { setAte(e.target.value); setPagina(1) }} disabled={lista.tipo === 'indisponivel'} />
          </div>
        </div>

        {lista.tipo === 'carregando' ? (
          <Carregando linhas={5} />
        ) : lista.tipo === 'indisponivel' ? (
          <Vazio titulo="Nenhuma análise para exibir" icone={<FileSearch size={20} />}>
            O histórico aparece aqui quando o serviço de análise fiscal estiver disponível.
          </Vazio>
        ) : lista.tipo === 'erro' ? (
          <ErroEstado mensagem={lista.msg} onTentar={() => setTentativa((t) => t + 1)} />
        ) : lista.dados.itens.length === 0 ? (
          <Vazio titulo={filtrando ? 'Nenhuma análise encontrada' : 'Nenhuma análise ainda'} icone={<FileSearch size={20} />}>
            {filtrando ? 'Ajuste a busca ou os filtros.' : 'Use “Nova análise” para classificar a primeira mercadoria.'}
          </Vazio>
        ) : (
          <>
            <div className="table-wrap">
              <table className="table table--compact">
                <thead>
                  <tr>
                    <th>Mercadoria</th>
                    <th>NCM sugerida</th>
                    <th>Data</th>
                    <th>Status</th>
                    <th className="right">Ações</th>
                  </tr>
                </thead>
                <tbody>
                  {lista.dados.itens.map((a) => (
                    <tr key={a.id} className="is-link" onClick={() => navigate(rotaAnaliseFiscal(id, a.id))}>
                      <td className="cell-main">{a.mercadoria}</td>
                      <td className="num nowrap">{a.ncmSugerida ? <strong>{fmtNcm(a.ncmSugerida)}</strong> : <span className="muted">—</span>}</td>
                      <td className="num">{fmtDataHora(a.criadaEm)}</td>
                      <td><BadgeStatus status={a.status} sm /></td>
                      <td className="right" onClick={(e) => e.stopPropagation()}>
                        <div className="acoes-linha">
                          {a.relatorioDisponivel && (
                            <Link to={`${rotaAnaliseFiscal(id, a.id)}#relatorio`} className="btn btn--ghost btn--sm">Ver relatório</Link>
                          )}
                          <Link to={rotaAnaliseFiscal(id, a.id)} className="btn btn--secondary btn--sm">
                            {emAndamento(a.status) ? 'Acompanhar' : a.status === 'INFORMACOES_INSUFICIENTES' || a.status === 'AGUARDANDO_REVISAO' ? 'Retomar' : 'Abrir'}
                          </Link>
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            <Paginacao pagina={pagina} total={lista.dados.total} porPagina={prefs.itensPorPagina} onChange={setPagina} />
          </>
        )}
      </Card>
    </>
  )
}
