import { useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { BadgeDollarSign, Check, CircleCheck, ListChecks, LoaderCircle, Percent, ShieldAlert, TriangleAlert, Wrench, X } from 'lucide-react'
import { revisarItem } from '../../api/tribia'
import { CabecalhoEmpresa, EstadoEmpresa } from '../../components/empresas/CabecalhoEmpresa'
import { Aviso, Badge, Card, Carregando, ErroEstado, KpiCard, Segmented, Vazio, type CorBadge } from '../../components/ui'
import { useAlertas } from '../../hooks/useAlertas'
import { useEmpresa } from '../../hooks/useEmpresa'
import { CATEGORIAS, TIPOS_ALERTA, type Alerta, type CategoriaAlerta, type Severidade } from '../../lib/alertas'
import { REGIME_TRIB_LABEL } from '../../lib/classificacao'
import { capitalizar, fmtMoeda, fmtNumero } from '../../lib/format'
import { rotaEmpresa, rotaNota } from '../../lib/rotas'
import { useDados, useToast } from '../../state/contexts'

type Filtro = 'todos' | CategoriaAlerta

const COR_CATEGORIA: Record<CategoriaAlerta, CorBadge> = { oportunidade: 'green', risco: 'red', conformidade: 'blue' }
const COR_SEVERIDADE: Record<Severidade, CorBadge> = { alta: 'red', media: 'amber', baixa: 'gray' }
const COR_VALOR: Record<CategoriaAlerta, string | undefined> = {
  oportunidade: 'var(--green-600)',
  risco: 'var(--danger)',
  conformidade: undefined,
}
const ROTULO_SEVERIDADE: Record<Severidade, string> = { alta: 'Alta', media: 'Média', baixa: 'Baixa' }

/**
 * Alertas fiscais (Etapa 3): onde a empresa pode estar pagando a mais, perdendo crédito ou correndo risco,
 * conferindo o cClassTrib das notas contra a tabela oficial e a lista de NCMs de cada benefício.
 */
export function Alertas() {
  const { id, empresa, notas, carregando } = useEmpresa()
  const { resultado, erro, tentarNovamente, notasComErro, progresso } = useAlertas(empresa, notas)
  const [filtro, setFiltro] = useState<Filtro>('todos')
  const [corrigindo, setCorrigindo] = useState<string | null>(null)

  const visiveis = useMemo(
    () => (resultado?.alertas ?? []).filter((a) => filtro === 'todos' || a.categoria === filtro),
    [resultado, filtro],
  )

  if (!empresa) return <EstadoEmpresa carregando={carregando} />

  const r = resultado?.resumo
  const semNotas = !carregando && notas.length === 0

  return (
    <>
      <CabecalhoEmpresa
        empresa={empresa}
        secao="Alertas fiscais"
        sub="O TribIA confere o código de cada item contra a tabela oficial e a lista de NCMs dos benefícios da LC 214/2025."
        acoes={
          <Link to={rotaEmpresa(id, 'revisao')} className="btn btn--secondary">
            <ListChecks size={17} /> Revisão
          </Link>
        }
      />

      <div className="grid-kpi">
        <KpiCard
          rotulo="Oportunidades"
          valor={r ? fmtMoeda(r.oportunidade) : '—'}
          dica={r ? `${fmtNumero(r.porCategoria.oportunidade)} alerta(s) · imposto ou crédito a recuperar` : undefined}
          carregando={!resultado && !erro && !semNotas}
          arte={<BadgeDollarSign size={28} strokeWidth={1.6} />}
        />
        <KpiCard
          rotulo="Riscos fiscais"
          valor={r ? fmtMoeda(r.risco) : '—'}
          dica={r ? `${fmtNumero(r.porCategoria.risco)} alerta(s) · exposição estimada` : undefined}
          carregando={!resultado && !erro && !semNotas}
          arte={<ShieldAlert size={28} strokeWidth={1.6} />}
        />
        <KpiCard
          rotulo="Pendências"
          valor={r ? fmtNumero(r.porCategoria.conformidade) : '—'}
          dica="Cadastro, leiaute e revisão"
          carregando={!resultado && !erro && !semNotas}
          arte={<TriangleAlert size={28} strokeWidth={1.6} />}
        />
        <KpiCard
          rotulo="Alíquota de referência 2027"
          valor={resultado ? `${fmtNumero(resultado.aliquotaReferencia * 100, 2)}%` : '—'}
          dica={
            resultado?.origemAliquota === 'calculos'
              ? 'CBS + IBS, mediana dos cálculos da empresa'
              : 'CBS + IBS, estimativa padrão (sem cálculos integrais)'
          }
          carregando={!resultado && !erro && !semNotas}
          arte={<Percent size={28} strokeWidth={1.6} />}
        />
      </div>

      <Aviso style={{ marginBottom: 'var(--gap)' }}>
        Valores em jogo são <strong>estimativas</strong>: base do item sem ICMS, PIS e Cofins × alíquota de referência ×
        diferença de carga entre os códigos. Servem para priorizar a conferência, não substituem a análise do contador.
        Em compras, o valor aparece como efeito no preço e fica fora do total de oportunidades: o crédito de 2027 usa o
        menor valor entre o que o fornecedor destacou e a correção.
      </Aviso>

      {notasComErro > 0 && (
        <Aviso tipo="warn" style={{ marginBottom: 'var(--gap)' }}>
          {fmtNumero(notasComErro)} nota(s) não carregaram e ficaram fora da análise.
        </Aviso>
      )}

      <Card
        titulo={resultado ? `${fmtNumero(visiveis.length)} alerta(s)` : 'Alertas'}
        sub={!resultado && !erro && !semNotas ? `Lendo as notas: ${progresso.prontos} de ${progresso.de}` : undefined}
        acoes={
          <Segmented<Filtro>
            rotulo="Filtrar alertas"
            valor={filtro}
            onChange={setFiltro}
            opcoes={[
              { valor: 'todos', rotulo: 'Todos' },
              { valor: 'oportunidade', rotulo: CATEGORIAS.oportunidade.rotulo },
              { valor: 'risco', rotulo: CATEGORIAS.risco.rotulo },
              { valor: 'conformidade', rotulo: CATEGORIAS.conformidade.rotulo },
            ]}
          />
        }
        corpo="flush"
      >
        {semNotas ? (
          <Vazio titulo="Nenhuma nota enviada">Envie XMLs de compra e venda para o TribIA conferir.</Vazio>
        ) : erro ? (
          <ErroEstado mensagem={erro} onTentar={tentarNovamente} />
        ) : !resultado ? (
          <Carregando linhas={6} />
        ) : visiveis.length === 0 ? (
          <Vazio titulo="Nenhum alerta" icone={<CircleCheck size={20} />}>
            {filtro === 'todos' ? 'As notas conferidas não têm divergências conhecidas.' : 'Nada nesta categoria.'}
          </Vazio>
        ) : (
          <div className="table-wrap">
            <table className="table table--compact">
              <thead>
                <tr>
                  <th>Alerta</th>
                  <th>Onde</th>
                  <th className="right">Valor em jogo</th>
                  <th className="right">Ação</th>
                </tr>
              </thead>
              <tbody>
                {visiveis.map((a) => (
                  <LinhaAlerta
                    key={a.id}
                    empresaId={id}
                    alerta={a}
                    corrigindo={corrigindo === a.id}
                    onCorrigir={(v) => setCorrigindo(v ? a.id : null)}
                  />
                ))}
              </tbody>
            </table>
          </div>
        )}
      </Card>
    </>
  )
}

function LinhaAlerta({ empresaId, alerta: a, corrigindo, onCorrigir }: {
  empresaId: number
  alerta: Alerta
  corrigindo: boolean
  onCorrigir: (v: boolean) => void
}) {
  const info = TIPOS_ALERTA[a.tipo]
  const podeCorrigir = a.itemId != null && (a.candidatas?.length ?? 0) > 0
  return (
    <>
      <tr>
        <td style={{ maxWidth: 460 }}>
          <div style={{ display: 'flex', gap: 6, flexWrap: 'wrap', marginBottom: 4 }}>
            <Badge cor={COR_CATEGORIA[a.categoria]} sm>{info.rotulo}</Badge>
            <Badge cor={COR_SEVERIDADE[a.severidade]} sm>{ROTULO_SEVERIDADE[a.severidade]}</Badge>
          </div>
          <div className="cell-main" style={{ whiteSpace: 'normal' }}>{a.titulo}</div>
          <div className="cell-sub" style={{ whiteSpace: 'normal' }}>{a.recomendacao}</div>
        </td>
        <td>
          {a.notaId != null ? (
            <Link to={rotaNota(empresaId, a.notaId)}>NF-e {a.notaNumero ?? 's/n'}</Link>
          ) : a.quantidade != null ? (
            <span>{fmtNumero(a.quantidade)} {a.tipo === 'REVISAO_PENDENTE' || a.tipo === 'FORNECEDOR_SIMPLES' ? 'item(ns)' : 'nota(s)'}</span>
          ) : (
            '—'
          )}
          {a.produto && <div className="cell-sub">{capitalizar(a.produto)}</div>}
          {a.ncm && <div className="cell-sub mono">NCM {a.ncm}{a.codigoNota ? ` · nota: ${a.codigoNota}` : ''}</div>}
          {!a.produto && a.contraparte && <div className="cell-sub">{capitalizar(a.contraparte)}</div>}
        </td>
        <td className="right num">
          {a.impacto != null && a.impacto > 0 ? (
            <b style={{ color: COR_VALOR[a.categoria] }}>{fmtMoeda(a.impacto)}</b>
          ) : a.efeitoNoPreco != null && a.efeitoNoPreco > 0 ? (
            <span className="muted" title="Imposto a mais embutido no preço pelo fornecedor; não é imposto recuperável">
              ~{fmtMoeda(a.efeitoNoPreco)} no preço
            </span>
          ) : (
            <span className="muted">—</span>
          )}
        </td>
        <td className="right">
          <div className="acoes-linha">
            {a.tipo === 'REVISAO_PENDENTE' ? (
              <Link to={rotaEmpresa(empresaId, 'revisao')} className="btn btn--secondary btn--sm">Revisar</Link>
            ) : podeCorrigir ? (
              <button className="btn btn--secondary btn--sm" onClick={() => onCorrigir(!corrigindo)}>
                {corrigindo ? <X size={14} /> : <Wrench size={14} />} {corrigindo ? 'Fechar' : 'Corrigir'}
              </button>
            ) : a.notaId != null ? (
              <Link to={rotaNota(empresaId, a.notaId)} className="btn btn--ghost btn--sm">Ver nota</Link>
            ) : null}
          </div>
        </td>
      </tr>
      {corrigindo && podeCorrigir && (
        <tr>
          <td colSpan={4} style={{ background: 'var(--surface-2)' }}>
            <CorrecaoAlerta alerta={a} onConcluir={() => onCorrigir(false)} />
          </td>
        </tr>
      )}
    </>
  )
}

/** Aplica no TribIA o código escolhido (PUT /api/itens/{id}/classificacao) e recalcula as notas afetadas. */
function CorrecaoAlerta({ alerta: a, onConcluir }: { alerta: Alerta; onConcluir: () => void }) {
  const { atualizarDetalhes, marcarAlteracaoFiscal } = useDados()
  const { mostrar } = useToast()
  const candidatas = a.candidatas ?? []
  const [codigo, setCodigo] = useState(a.sugestao?.cClassTrib ?? candidatas[0]?.cClassTrib ?? '')
  const [salvando, setSalvando] = useState(false)
  const escolhida = candidatas.find((o) => o.cClassTrib === codigo)

  const aplicar = async () => {
    if (a.itemId == null || !escolhida) return
    setSalvando(true)
    try {
      const r = await revisarItem(a.itemId, {
        cClassTrib: escolhida.cClassTrib,
        cst: escolhida.cst,
        justificativa: `Alerta TribIA (${TIPOS_ALERTA[a.tipo].rotulo}): nota com ${a.codigoNota ?? '—'}; lista oficial do NCM ${a.ncm ?? '—'}.`,
      })
      mostrar({
        tipo: 'sucesso',
        titulo: `Classificação corrigida para ${escolhida.cClassTrib}`,
        texto: [
          r.itensAtualizados > 1 ? `${r.itensAtualizados} itens idênticos atualizados.` : '',
          r.notasRecalculadas.length ? `${r.notasRecalculadas.length} nota(s) recalculada(s).` : '',
          'A divergência com a nota fica registrada para cobrar a correção na origem.',
          ...(r.avisos ?? []),
        ].filter(Boolean).join(' '),
      })
      await atualizarDetalhes(r.notasRecalculadas)
      marcarAlteracaoFiscal()
      onConcluir()
    } catch (e) {
      mostrar({ tipo: 'erro', titulo: 'Não foi possível corrigir', texto: e instanceof Error ? e.message : undefined })
    } finally {
      setSalvando(false)
    }
  }

  return (
    <div style={{ display: 'grid', gap: 10, padding: '6px 0' }}>
      <div className="field" style={{ margin: 0 }}>
        <label className="field__label" htmlFor={`al-${a.id}`}>Classificação a usar no TribIA</label>
        <select id={`al-${a.id}`} className="select" value={codigo} onChange={(e) => setCodigo(e.target.value)} disabled={salvando}>
          {candidatas.map((o) => (
            <option key={o.cClassTrib} value={o.cClassTrib}>
              {o.cClassTrib} · CST {o.cst} · {o.descricaoRegime ?? REGIME_TRIB_LABEL[o.regime]} — {o.nome}
            </option>
          ))}
        </select>
        <span className="cell-sub">
          Corrige o cálculo do TribIA para este item e os idênticos. A nota fiscal original não muda: a correção dela é com
          {a.notaPropria ? ' o emissor da empresa.' : ' o fornecedor.'}
        </span>
      </div>
      <div>
        <button className="btn btn--primary btn--sm" disabled={salvando || !escolhida} onClick={() => void aplicar()}>
          {salvando ? <LoaderCircle size={14} className="spin" /> : <Check size={14} />} Aplicar {codigo}
        </button>
      </div>
    </div>
  )
}
