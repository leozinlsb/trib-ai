import { useMemo } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { ArrowUpRight } from 'lucide-react'
import type { Cliente, NotaResumo } from '../../api/types'
import { GraficoArea, LegendaSeries } from '../charts/GraficoArea'
import { GrafoRede } from '../charts/GrafoRede'
import { BadgeClassificacao, BadgeTipo } from '../notas'
import { Card, Carregando, Segmented, Vazio } from '../ui'
import { maisRecentes, serieTemporal, type Periodo } from '../../lib/aggregate'
import { montarRede } from '../../lib/rede'
import { capitalizar, fmtData, nomeCliente, tituloNota } from '../../lib/format'
import { rotaNota } from '../../lib/rotas'
import { useDados, usePreferencias } from '../../state/contexts'

/** Volume (R$) de compras e vendas por semana ou mês. */
export function CardVolume({ notas, carregando }: { notas: NotaResumo[]; carregando: boolean }) {
  const { prefs, definir } = usePreferencias()
  const pontos = useMemo(() => serieTemporal(notas, prefs.periodoGrafico), [notas, prefs.periodoGrafico])
  return (
    <Card
      titulo={prefs.periodoGrafico === 'semanal' ? 'Volume de Notas Semanais' : 'Volume de Notas Mensais'}
      acoes={
        <Segmented<Periodo>
          rotulo="Período do gráfico"
          valor={prefs.periodoGrafico}
          onChange={(v) => definir({ periodoGrafico: v })}
          opcoes={[
            { valor: 'semanal', rotulo: 'Semanal' },
            { valor: 'mensal', rotulo: 'Mensal' },
          ]}
        />
      }
    >
      {carregando ? (
        <Carregando linhas={5} altura={28} />
      ) : pontos.length === 0 ? (
        <Vazio titulo="Sem notas para exibir">Envie XMLs de NF-e para ver o volume por período.</Vazio>
      ) : (
        <>
          <div style={{ marginBottom: 6 }}>
            <LegendaSeries />
          </div>
          <GraficoArea pontos={pontos} />
        </>
      )}
    </Card>
  )
}

/** Rede de relacionamentos (versão compacta). */
export function CardRede({ clientes, notas, carregando, destino }: {
  clientes: Cliente[]
  notas: NotaResumo[]
  carregando: boolean
  /** página com a rede completa */
  destino: string
}) {
  const navigate = useNavigate()
  const rede = useMemo(() => montarRede(clientes, notas), [clientes, notas])
  const contrapartes = rede.nos.filter((n) => n.papel === 'contraparte').length
  return (
    <Card
      titulo="Rede de Relacionamentos"
      acoes={
        <Link to={destino} className="icon-btn" style={{ width: 28, height: 28 }} aria-label="Ver relacionamentos" title="Ver relacionamentos">
          <ArrowUpRight size={17} />
        </Link>
      }
    >
      {carregando ? (
        <Carregando linhas={5} altura={28} />
      ) : rede.arestas.length === 0 ? (
        <Vazio titulo="Sem relacionamentos">As ligações aparecem quando há notas importadas.</Vazio>
      ) : (
        <button
          type="button"
          onClick={() => navigate(destino)}
          style={{ all: 'unset', display: 'block', width: '100%', cursor: 'pointer' }}
          aria-label="Abrir relacionamentos"
        >
          <GrafoRede rede={rede} altura={222} />
          <p style={{ textAlign: 'center', fontSize: 14, color: 'var(--text-2)', marginTop: 8 }}>
            {contrapartes} fornecedores e clientes finais
          </p>
        </button>
      )}
    </Card>
  )
}

/** Últimas notas, com tipo e situação da classificação. */
export function CardRecentes({ notas, carregando, verTodos, mostrarEmpresa, limite = 5 }: {
  notas: NotaResumo[]
  carregando: boolean
  verTodos: string
  mostrarEmpresa?: boolean
  limite?: number
}) {
  const { clientes, detalhes, errosDetalhe } = useDados()
  const navigate = useNavigate()
  const recentes = useMemo(() => maisRecentes(notas).slice(0, limite), [notas, limite])
  return (
    <Card
      titulo="Documentos Recentes"
      corpo="flush"
      acoes={<Link to={verTodos} style={{ fontSize: 13, fontWeight: 500 }}>Ver todos</Link>}
    >
      {carregando ? (
        <Carregando linhas={5} />
      ) : recentes.length === 0 ? (
        <Vazio titulo="Nenhum documento ainda">Use “Enviar notas” para importar as primeiras notas fiscais.</Vazio>
      ) : (
        <div className="table-wrap" style={{ padding: '0 0 6px' }}>
          <table className="table">
            <thead>
              <tr>
                <th style={{ width: '46%' }}>Nome do Documento</th>
                <th>Tipo</th>
                <th>Status</th>
                <th>Data</th>
              </tr>
            </thead>
            <tbody>
              {recentes.map((n) => (
                <tr key={n.id} className="is-link" onClick={() => navigate(rotaNota(n.clienteId, n.id))}>
                  <td>
                    <Link to={rotaNota(n.clienteId, n.id)} className="cell-main" style={{ color: 'inherit' }} onClick={(e) => e.stopPropagation()}>
                      {tituloNota(n)} — {capitalizar(n.contraparteNome)}
                    </Link>
                    {mostrarEmpresa && <div className="cell-sub">{nomeCliente(clientes.find((c) => c.id === n.clienteId))}</div>}
                  </td>
                  <td><BadgeTipo tipo={n.tipo} /></td>
                  <td><BadgeClassificacao detalhe={detalhes.get(n.id)} erro={errosDetalhe.get(n.id)} /></td>
                  <td className="num">{fmtData(n.dataEmissao)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </Card>
  )
}

/** Anel do indicador "Em processamento": gira só quando há envio em andamento. */
export function AnelProcessamento({ ativo }: { ativo: boolean }) {
  return (
    <svg width="32" height="32" viewBox="0 0 32 32" className={ativo ? 'spin' : undefined} aria-hidden="true">
      <circle cx="16" cy="16" r="12" fill="none" stroke="#e3e6ec" strokeWidth="3" />
      <path d="M16 4a12 12 0 0 1 12 12" fill="none" stroke="var(--green-600)" strokeWidth="3" strokeLinecap="round" />
    </svg>
  )
}

/** Ícone de rede (nó central ligado a quatro nós), como o da referência. */
export function IconeConexoes() {
  return (
    <svg width="32" height="32" viewBox="0 0 32 32" fill="none" stroke="currentColor" strokeWidth="1.7" aria-hidden="true">
      <path d="M9.5 9.5 13.5 13.5M22.5 9.5l-4 4M9.5 22.5l4-4M22.5 22.5l-4-4" />
      <circle cx="16" cy="16" r="3.2" />
      <circle cx="7" cy="7" r="3.2" />
      <circle cx="25" cy="7" r="3.2" />
      <circle cx="7" cy="25" r="3.2" />
      <circle cx="25" cy="25" r="3.2" />
    </svg>
  )
}
