import { useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import type { NotaResumo } from '../../api/types'
import { useDetalhes } from '../../hooks/useDetalhes'
import { itemClassificado } from '../../lib/aggregate'
import { capitalizar, fmtMoeda, fmtNumero, TIPO_LABEL } from '../../lib/format'
import { rotaNota } from '../../lib/rotas'
import { usePreferencias } from '../../state/contexts'
import { Aviso, Badge, Card, Carregando, KpiCard, Paginacao, Segmented, Vazio } from '../ui'

type Filtro = 'pendentes' | 'classificados' | 'todos'

/** Situação da classificação tributária (CST + cClassTrib) dos itens das notas de uma empresa. */
export function ClassificacaoItens({ notas }: { notas: NotaResumo[] }) {
  const { prefs } = usePreferencias()
  const ids = useMemo(() => notas.map((n) => n.id), [notas])
  const { detalhes, errosDetalhe, completo, prontos } = useDetalhes(ids)
  const [filtro, setFiltro] = useState<Filtro>('pendentes')
  const [pagina, setPagina] = useState(1)

  const linhas = useMemo(() => {
    const out = []
    for (const n of notas) {
      const d = detalhes.get(n.id)
      if (!d) continue
      for (const i of d.itens) out.push({ nota: n, item: i, ok: itemClassificado(i) })
    }
    return out
  }, [notas, detalhes])

  const pendentesPorNcm = useMemo(() => {
    const m = new Map<string, { ncm: string; itens: number; valor: number; exemplo: string }>()
    for (const l of linhas) {
      if (l.ok) continue
      const ncm = l.item.ncm ?? 'Sem NCM'
      const g = m.get(ncm) ?? { ncm, itens: 0, valor: 0, exemplo: capitalizar(l.item.descricao) }
      g.itens += 1
      g.valor += l.item.valorTotal ?? 0
      m.set(ncm, g)
    }
    return [...m.values()].sort((a, b) => b.itens - a.itens || b.valor - a.valor).slice(0, 6)
  }, [linhas])

  const total = linhas.length
  const ok = linhas.filter((l) => l.ok).length
  const filtradas = linhas.filter((l) => filtro === 'todos' || (filtro === 'pendentes' ? !l.ok : l.ok))
  const pp = prefs.itensPorPagina
  const visiveis = filtradas.slice((pagina - 1) * pp, pagina * pp)
  const carregando = !completo

  if (notas.length === 0) {
    return <div className="card"><Vazio titulo="Sem itens para analisar">Envie notas fiscais para ver a classificação dos itens.</Vazio></div>
  }

  return (
    <>
      <div className="grid-kpi">
        <KpiCard rotulo="Itens analisados" valor={fmtNumero(total)} dica={`em ${fmtNumero(notas.length)} notas`} carregando={carregando} />
        <KpiCard rotulo="Itens classificados" valor={fmtNumero(ok)} dica="com CST e cClassTrib" carregando={carregando} />
        <KpiCard rotulo="Itens pendentes" valor={fmtNumero(total - ok)} dica="ainda sem classificação" carregando={carregando} />
        <KpiCard
          rotulo="Cobertura"
          valor={total ? `${fmtNumero((ok / total) * 100, 1)}%` : '—'}
          dica={`${fmtNumero(new Set(linhas.map((l) => l.item.ncm).filter(Boolean)).size)} NCMs distintos`}
          carregando={carregando}
        />
      </div>

      <div className="grid-side">
        <Card
          titulo="Itens por situação"
          corpo="nenhum"
          acoes={
            <Segmented<Filtro>
              rotulo="Situação"
              valor={filtro}
              onChange={(v) => {
                setFiltro(v)
                setPagina(1)
              }}
              opcoes={[
                { valor: 'pendentes', rotulo: 'Pendentes' },
                { valor: 'classificados', rotulo: 'Classificados' },
                { valor: 'todos', rotulo: 'Todos' },
              ]}
            />
          }
        >
          <div style={{ height: 12 }} />
          {carregando ? (
            <>
              <p className="cell-sub" style={{ padding: '0 18px' }}>Lendo itens das notas ({prontos}/{ids.length})...</p>
              <Carregando linhas={6} />
            </>
          ) : filtradas.length === 0 ? (
            <Vazio titulo="Nenhum item nesta situação" />
          ) : (
            <>
              <div className="table-wrap">
                <table className="table table--compact">
                  <thead>
                    <tr>
                      <th>Item</th>
                      <th>Nota</th>
                      <th>NCM</th>
                      <th>CFOP</th>
                      <th className="center">CST PIS/Cofins</th>
                      <th className="right">Valor</th>
                      <th>Classificação</th>
                    </tr>
                  </thead>
                  <tbody>
                    {visiveis.map(({ nota, item, ok }) => (
                      <tr key={item.id}>
                        <td><div className="cell-main">{capitalizar(item.descricao)}</div></td>
                        <td className="nowrap">
                          <Link to={rotaNota(nota.clienteId, nota.id)}>NF-e {nota.numero}</Link>
                          <div className="cell-sub">{TIPO_LABEL[nota.tipo]} · item {item.nItem}</div>
                        </td>
                        <td className="mono">{item.ncm ?? '—'}</td>
                        <td className="mono">{item.cfop ?? '—'}</td>
                        <td className="center mono">{item.cstPisCofins ?? '—'}</td>
                        <td className="right num">{fmtMoeda(item.valorTotal)}</td>
                        <td>
                          {ok ? (
                            <Badge cor="green" sm>CST {item.ibsCbsDestacado!.cst} · {item.ibsCbsDestacado!.cClassTrib}</Badge>
                          ) : (
                            <Badge cor="gray" sm>Pendente</Badge>
                          )}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
              <Paginacao pagina={pagina} total={filtradas.length} porPagina={pp} onChange={setPagina} />
            </>
          )}
          {errosDetalhe.size > 0 && (
            <div style={{ padding: '0 18px 16px' }}>
              <Aviso tipo="error">{errosDetalhe.size} nota(s) não puderam ser lidas: {[...errosDetalhe.values()][0]}</Aviso>
            </div>
          )}
        </Card>

        <Card titulo="Pendências por NCM" sub="Produtos com mais itens aguardando classificação">
          {carregando ? (
            <Carregando linhas={4} />
          ) : pendentesPorNcm.length === 0 ? (
            <p style={{ fontSize: 13.5, color: 'var(--text-3)' }}>Nenhum item pendente.</p>
          ) : (
            <ul style={{ listStyle: 'none', margin: 0, padding: 0, display: 'flex', flexDirection: 'column', gap: 12 }}>
              {pendentesPorNcm.map((g) => (
                <li key={g.ncm}>
                  <div style={{ display: 'flex', justifyContent: 'space-between', gap: 8, fontSize: 13.5 }}>
                    <span className="mono">{g.ncm}</span>
                    <span className="num">{g.itens} item(ns) · {fmtMoeda(g.valor)}</span>
                  </div>
                  <div className="cell-sub" style={{ whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }} title={g.exemplo}>
                    {g.exemplo}
                  </div>
                </li>
              ))}
            </ul>
          )}
        </Card>
      </div>
    </>
  )
}
