import { useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import type { Cliente, NotaResumo } from '../../api/types'
import { GrafoRede } from '../charts/GrafoRede'
import { BadgeTipo } from '../notas'
import { Card, KpiCard, Segmented, Vazio } from '../ui'
import { maisRecentes } from '../../lib/aggregate'
import { montarRede, papelNo, type No } from '../../lib/rede'
import { fmtCnpj, fmtData, fmtMoeda, fmtNumero } from '../../lib/format'
import { rotaNota } from '../../lib/rotas'

type Filtro = 'todas' | 'ENTRADA' | 'SAIDA'

/** Fornecedores e clientes finais da empresa, formados pelas notas. */
export function Relacionamentos({ empresa, notas }: { empresa: Cliente; notas: NotaResumo[] }) {
  const [tipo, setTipo] = useState<Filtro>('todas')
  const [sel, setSel] = useState<No | null>(null)

  const filtradas = useMemo(() => notas.filter((n) => tipo === 'todas' || n.tipo === tipo), [notas, tipo])
  const rede = useMemo(() => montarRede([empresa], filtradas), [empresa, filtradas])
  const fornecedores = rede.nos.filter((n) => n.papel === 'contraparte' && n.fornecedor).length
  const compradores = rede.nos.filter((n) => n.papel === 'contraparte' && n.comprador).length

  const notasSel = useMemo(() => {
    if (!sel) return []
    return maisRecentes(
      filtradas.filter((n) => (sel.papel === 'cliente' ? true : !!sel.documento && n.contraparteCnpj === sel.documento)),
    )
  }, [sel, filtradas])

  if (notas.length === 0) {
    return <div className="card"><Vazio titulo="Sem relacionamentos">As ligações aparecem quando há notas importadas.</Vazio></div>
  }

  return (
    <>
      <div className="grid-kpi">
        <KpiCard rotulo="Fornecedores" valor={fmtNumero(fornecedores)} dica="em notas de compra" />
        <KpiCard rotulo="Clientes finais" valor={fmtNumero(compradores)} dica="em notas de venda" />
        <KpiCard rotulo="Ligações" valor={fmtNumero(rede.arestas.length)} dica={`${fmtNumero(filtradas.length)} notas`} />
        <KpiCard rotulo="Valor movimentado" valor={fmtMoeda(filtradas.reduce((s, n) => s + n.valorTotal, 0))} />
      </div>

      <div className="grid-side">
        <Card
          titulo="Mapa de relacionamentos"
          sub="Passe o mouse para destacar; clique para ver as notas. Espessura = valor movimentado."
          acoes={
            <Segmented<Filtro>
              rotulo="Tipo de nota"
              valor={tipo}
              onChange={(v) => {
                setTipo(v)
                setSel(null)
              }}
              opcoes={[
                { valor: 'todas', rotulo: 'Todas' },
                { valor: 'ENTRADA', rotulo: 'Compras' },
                { valor: 'SAIDA', rotulo: 'Vendas' },
              ]}
            />
          }
        >
          {rede.arestas.length === 0 ? (
            <Vazio titulo="Sem relacionamentos para este filtro" />
          ) : (
            <>
              <GrafoRede rede={rede} altura={460} interativo rotulos selecionado={sel?.id ?? null} onSelecionar={setSel} />
              <div className="legend" style={{ marginTop: 10, justifyContent: 'center' }}>
                <span className="legend__item"><span className="legend__swatch" style={{ background: '#2c4f96' }} /> Predominam vendas</span>
                <span className="legend__item"><span className="legend__swatch" style={{ background: '#16a06e' }} /> Predominam compras</span>
              </div>
            </>
          )}
        </Card>

        <Card titulo={sel ? sel.nome : 'Detalhes'} sub={sel ? papelNo(sel) : 'Selecione um nó no mapa'}>
          {!sel ? (
            <p style={{ color: 'var(--text-3)', fontSize: 13.5 }}>
              Cada ligação vem de notas fiscais da empresa. Clique em um fornecedor ou cliente para ver as notas.
            </p>
          ) : (
            <div className="stack" style={{ gap: 14 }}>
              <dl className="dl" style={{ gridTemplateColumns: '1fr 1fr' }}>
                <div><dt>CNPJ/CPF</dt><dd className="num">{fmtCnpj(sel.documento)}</dd></div>
                <div><dt>Notas</dt><dd>{fmtNumero(sel.notas)}</dd></div>
                <div style={{ gridColumn: '1 / -1' }}><dt>Valor movimentado</dt><dd>{fmtMoeda(sel.valor)}</dd></div>
              </dl>
              <ul style={{ listStyle: 'none', margin: 0, padding: 0, display: 'flex', flexDirection: 'column', gap: 8 }}>
                {notasSel.slice(0, 12).map((n) => (
                  <li key={n.id} style={{ display: 'flex', alignItems: 'center', gap: 8, fontSize: 13.5 }}>
                    <BadgeTipo tipo={n.tipo} sm />
                    <Link to={rotaNota(n.clienteId, n.id)} className="nowrap">NF-e {n.numero}</Link>
                    <span className="muted num">{fmtData(n.dataEmissao)}</span>
                    <span className="num" style={{ marginLeft: 'auto' }}>{fmtMoeda(n.valorTotal)}</span>
                  </li>
                ))}
              </ul>
              {notasSel.length > 12 && <p className="cell-sub">+{notasSel.length - 12} notas</p>}
            </div>
          )}
        </Card>
      </div>
    </>
  )
}
