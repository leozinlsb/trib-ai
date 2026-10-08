import { useEffect, useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { ArrowRight, CalendarDays, CircleCheck, Cpu, FileText, FolderKanban, ListChecks, Upload } from 'lucide-react'
import { gerarRelatorio } from '../../api/tribia'
import type { Relatorio } from '../../api/types'
import { CabecalhoEmpresa, EstadoEmpresa } from '../../components/empresas/CabecalhoEmpresa'
import { EnviosRecentes } from '../../components/EnviosRecentes'
import { CardRecentes, CardVolume } from '../../components/painel/Blocos'
import { Card, Carregando, ErroEstado, KpiCard } from '../../components/ui'
import { useCobertura } from '../../hooks/useCobertura'
import { useEmpresa } from '../../hooks/useEmpresa'
import { competencias, maisRecentes } from '../../lib/aggregate'
import { capitalizar, fmtCompetencia, fmtData, fmtMoeda, fmtNumero } from '../../lib/format'
import { rotaEmpresa } from '../../lib/rotas'
import { useAtividades, useAuth } from '../../state/contexts'

/** Dashboard individual da empresa: estado dos documentos, resultado das análises e o próximo passo. */
export function InicioEmpresa() {
  const { id, empresa, notas, carregando } = useEmpresa()
  const { abrirUpload } = useAtividades()
  const { admin } = useAuth()
  const cobertura = useCobertura(notas)
  const comps = useMemo(() => competencias(notas), [notas]) // mais recente primeiro
  const ultima = maisRecentes(notas)[0]

  if (!empresa) return <EstadoEmpresa carregando={carregando} />

  const entradas = notas.filter((n) => n.tipo === 'ENTRADA').length
  const pct = cobertura.total ? (cobertura.classificados / cobertura.total) * 100 : 0

  return (
    <>
      <CabecalhoEmpresa
        empresa={empresa}
        acoes={
          <>
            <Link to={rotaEmpresa(id, 'analises')} className="btn btn--secondary">
              <FolderKanban size={17} /> Ver relatório
            </Link>
            <button className="btn btn--primary" onClick={() => abrirUpload(id, true)} disabled={!empresa.ativo}>
              <Upload size={18} strokeWidth={2.1} /> Enviar notas
            </button>
          </>
        }
      />

      <ProximoPasso
        id={id}
        ativa={empresa.ativo}
        admin={admin}
        semNotas={!carregando && notas.length === 0}
        pendentes={cobertura.completo ? cobertura.pendentes : null}
        onEnviar={() => abrirUpload(id, true)}
      />

      <div className="grid-kpi">
        <KpiCard
          rotulo="Documentos Enviados"
          valor={fmtNumero(notas.length)}
          dica={`${fmtNumero(entradas)} compras · ${fmtNumero(notas.length - entradas)} vendas`}
          carregando={carregando}
          arte={<FileText size={28} strokeWidth={1.6} />}
        />
        <KpiCard
          rotulo="Itens Classificados"
          valor={cobertura.total ? `${fmtNumero(pct, 1)}%` : '—'}
          dica={cobertura.total ? `${fmtNumero(cobertura.classificados)} de ${fmtNumero(cobertura.total)} itens` : 'Nenhum item'}
          carregando={carregando || !cobertura.completo}
          arte={<Cpu size={28} strokeWidth={1.6} />}
        />
        <KpiCard
          rotulo="Relatórios Disponíveis"
          valor={fmtNumero(comps.length)}
          dica={comps.length ? `um por competência · último: ${fmtCompetencia(comps[0]!)}` : 'Envie notas para gerar'}
          carregando={carregando}
          arte={<FolderKanban size={28} strokeWidth={1.6} />}
        />
        <KpiCard
          rotulo="Último Documento"
          valor={ultima ? fmtData(ultima.dataEmissao) : '—'}
          dica={ultima ? capitalizar(ultima.contraparteNome) : 'Nenhuma nota ainda'}
          carregando={carregando}
          arte={<CalendarDays size={28} strokeWidth={1.6} />}
        />
      </div>

      <div className="grid-charts">
        <CardVolume notas={notas} carregando={carregando} />
        <ResumoUltimoPeriodo id={id} competencia={comps[0]} />
      </div>

      <div className="grid-charts">
        <CardRecentes notas={notas} carregando={carregando} verTodos={rotaEmpresa(id, 'documentos')} />
        <Card titulo="Envios recentes" acoes={<Link to={rotaEmpresa(id, 'documentos')} style={{ fontSize: 13, fontWeight: 500 }}>Documentos</Link>}>
          <EnviosRecentes clienteId={id} limite={4} compacto />
        </Card>
      </div>
    </>
  )
}

function ProximoPasso({ id, ativa, admin, semNotas, pendentes, onEnviar }: {
  id: number
  ativa: boolean
  admin: boolean
  semNotas: boolean
  pendentes: number | null
  onEnviar: () => void
}) {
  let icone = <ListChecks size={20} />
  let titulo: string
  let texto: string
  let acao: React.ReactNode = null

  if (!ativa) {
    titulo = 'Empresa desativada'
    texto = 'Os documentos continuam disponíveis para consulta, mas a empresa não recebe novas notas.'
    if (admin) acao = <Link to={rotaEmpresa(id, 'configuracoes')} className="btn btn--secondary btn--sm">Reativar empresa</Link>
  } else if (semNotas) {
    icone = <Upload size={20} />
    titulo = 'Comece enviando as notas fiscais'
    texto = 'Envie os XMLs das NF-e de compra e de venda. O TribIA organiza tudo por competência e gera o relatório.'
    acao = <button className="btn btn--primary btn--sm" onClick={onEnviar}>Enviar notas</button>
  } else if (pendentes == null) {
    return null
  } else if (pendentes > 0) {
    titulo = `${fmtNumero(pendentes)} itens aguardam classificação tributária`
    texto = 'Veja quais produtos ainda não têm CST e cClassTrib e acompanhe a evolução da classificação.'
    acao = (
      <Link to={`${rotaEmpresa(id, 'analises')}?aba=classificacao`} className="btn btn--secondary btn--sm">
        Ver itens pendentes <ArrowRight size={14} />
      </Link>
    )
  } else {
    icone = <CircleCheck size={20} />
    titulo = 'Todos os itens estão classificados'
    texto = 'Consulte o relatório da empresa para ver a apuração e os detalhes do período.'
    acao = <Link to={rotaEmpresa(id, 'analises')} className="btn btn--secondary btn--sm">Abrir relatório</Link>
  }

  return (
    <div className="proximo-passo">
      <span className="proximo-passo__icone">{icone}</span>
      {/* base de 240px: em telas estreitas o botão desce para a linha de baixo */}
      <div style={{ flex: '1 1 240px', minWidth: 0 }}>
        <span className="eyebrow">Próximo passo</span>
        <div className="proximo-passo__titulo">{titulo}</div>
        <div className="proximo-passo__texto">{texto}</div>
      </div>
      {acao}
    </div>
  )
}

/** Resumo do relatório da competência mais recente (gerado pelo servidor). */
function ResumoUltimoPeriodo({ id, competencia }: { id: number; competencia?: string }) {
  const [rel, setRel] = useState<Relatorio | null>(null)
  const [erro, setErro] = useState<string | null>(null)
  const [tentativa, setTentativa] = useState(0)

  useEffect(() => {
    if (!competencia) return
    const ctrl = new AbortController()
    setRel(null)
    setErro(null)
    gerarRelatorio(id, { de: competencia, ate: competencia }, ctrl.signal)
      .then(setRel)
      .catch((e) => {
        if (e instanceof DOMException && e.name === 'AbortError') return
        setErro(e instanceof Error ? e.message : 'Falha ao gerar o resumo.')
      })
    return () => ctrl.abort()
  }, [id, competencia, tentativa])

  const link = competencia ? `${rotaEmpresa(id, 'analises')}?de=${competencia}&ate=${competencia}` : rotaEmpresa(id, 'analises')

  return (
    <Card
      titulo="Último período"
      sub={competencia ? fmtCompetencia(competencia, true) : undefined}
      acoes={<Link to={link} style={{ fontSize: 13, fontWeight: 500 }}>Relatório completo</Link>}
    >
      {!competencia ? (
        <p style={{ fontSize: 13.5, color: 'var(--text-3)' }}>O resumo aparece depois do primeiro envio de notas.</p>
      ) : erro ? (
        <ErroEstado mensagem={erro} onTentar={() => setTentativa((t) => t + 1)} />
      ) : !rel ? (
        <Carregando linhas={4} />
      ) : (
        <div className="resumo-periodo">
          <div className="resumo-periodo__linha">
            <span>Notas</span>
            <b>{fmtNumero(rel.resumo.notas)} <small>({rel.resumo.entradas} compras · {rel.resumo.saidas} vendas)</small></b>
          </div>
          <div className="resumo-periodo__linha"><span>Compras</span><b>{fmtMoeda(rel.resumo.valorEntradas)}</b></div>
          <div className="resumo-periodo__linha"><span>Vendas</span><b>{fmtMoeda(rel.resumo.valorSaidas)}</b></div>
          <div className="resumo-periodo__destaque">
            <span>{rel.apuracaoPisCofins.saldoCredor > 0 ? 'Saldo credor de PIS/Cofins' : 'PIS/Cofins a pagar'}</span>
            <b>{fmtMoeda(rel.apuracaoPisCofins.saldoCredor > 0 ? rel.apuracaoPisCofins.saldoCredor : rel.apuracaoPisCofins.aPagar)}</b>
          </div>
          <div className="cell-sub">
            Débito {fmtMoeda(rel.apuracaoPisCofins.debito)} − crédito {fmtMoeda(rel.apuracaoPisCofins.credito)} ·
            calculado pelas regras de apuração a partir das notas.
          </div>
          {rel.observacoes.some((o) => o.nivel === 'ATENCAO') && (
            <div className="cell-sub" style={{ color: 'var(--badge-amber-fg)' }}>
              {rel.observacoes.filter((o) => o.nivel === 'ATENCAO').length} ponto(s) de atenção no relatório.
            </div>
          )}
        </div>
      )}
    </Card>
  )
}
