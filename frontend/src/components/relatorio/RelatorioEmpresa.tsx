import { useEffect, useMemo, useState, type ReactNode } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { CircleAlert, Info, Printer } from 'lucide-react'
import { gerarRelatorio } from '../../api/tribia'
import type { NotaResumo, Relatorio } from '../../api/types'
import { competencias } from '../../lib/aggregate'
import { capitalizar, fmtCnpj, fmtCompetencia, fmtData, fmtDataHora, fmtMoeda, fmtNumero, REGIME_LABEL, tituloNota } from '../../lib/format'
import { rotaNota } from '../../lib/rotas'
import { BadgeTipo } from '../notas'
import { Badge, Card, Carregando, ErroEstado, Vazio } from '../ui'

const SITUACAO: Record<Relatorio['situacao'], { cor: 'green' | 'amber' | 'gray'; rotulo: string; dica: string }> = {
  COMPLETO: { cor: 'green', rotulo: 'Completo', dica: 'Todos os itens do período têm classificação tributária.' },
  PARCIAL: { cor: 'amber', rotulo: 'Parcial', dica: 'Há itens sem classificação tributária no período.' },
  SEM_DADOS: { cor: 'gray', rotulo: 'Sem dados', dica: 'Não há notas no período escolhido.' },
}

/** Etiqueta de origem do dado: fato da nota fiscal ou cálculo das regras do sistema. */
function Fonte({ tipo }: { tipo: 'nota' | 'calculo' | 'verificacao' }) {
  const t = {
    nota: { cls: '', txt: 'Extraído das notas' },
    calculo: { cls: 'fonte--calc', txt: 'Calculado pelas regras de apuração' },
    verificacao: { cls: 'fonte--verif', txt: 'Verificação automática' },
  }[tipo]
  return <span className={`fonte ${t.cls}`}>{t.txt}</span>
}

function Secao({ titulo, fonte, children, sub }: { titulo: string; fonte?: 'nota' | 'calculo' | 'verificacao'; sub?: string; children: ReactNode }) {
  return (
    <Card titulo={<>{titulo} {fonte && <Fonte tipo={fonte} />}</>} sub={sub} className="relatorio__secao">
      {children}
    </Card>
  )
}

/**
 * Relatório individual da empresa (GET /api/clientes/{id}/relatorio). Período por competência, escolhido
 * pela URL (?de=AAAA-MM&ate=AAAA-MM). Sem conteúdo de IA e sem recomendações: só fatos e cálculos.
 */
export function RelatorioEmpresa({ empresaId, notas }: { empresaId: number; notas: NotaResumo[] }) {
  const [params, setParams] = useSearchParams()
  const comps = useMemo(() => competencias(notas).slice().sort(), [notas]) // mais antiga primeiro
  const de = params.get('de') ?? ''
  const ate = params.get('ate') ?? ''
  const [rel, setRel] = useState<Relatorio | null>(null)
  const [erro, setErro] = useState<string | null>(null)
  const [tentativa, setTentativa] = useState(0)

  useEffect(() => {
    const ctrl = new AbortController()
    setRel(null)
    setErro(null)
    gerarRelatorio(empresaId, { de: de || undefined, ate: ate || undefined }, ctrl.signal)
      .then(setRel)
      .catch((e) => {
        if (e instanceof DOMException && e.name === 'AbortError') return
        setErro(e instanceof Error ? e.message : 'Não foi possível gerar o relatório.')
      })
    return () => ctrl.abort()
    // notas.length: um novo envio atualiza o relatório
  }, [empresaId, de, ate, tentativa, notas.length])

  const periodo = (k: 'de' | 'ate', v: string) => {
    const p = new URLSearchParams(params)
    if (v) p.set(k, v)
    else p.delete(k)
    setParams(p, { replace: true })
  }

  return (
    <div className="relatorio">
      <div className="relatorio__barra no-print">
        <div className="field">
          <label className="field__label" htmlFor="rel-de">De</label>
          <select id="rel-de" className="select" value={de} onChange={(e) => periodo('de', e.target.value)}>
            <option value="">Início</option>
            {comps.map((c) => <option key={c} value={c} disabled={!!ate && c > ate}>{fmtCompetencia(c, true)}</option>)}
          </select>
        </div>
        <div className="field">
          <label className="field__label" htmlFor="rel-ate">Até</label>
          <select id="rel-ate" className="select" value={ate} onChange={(e) => periodo('ate', e.target.value)}>
            <option value="">Mais recente</option>
            {comps.map((c) => <option key={c} value={c} disabled={!!de && c < de}>{fmtCompetencia(c, true)}</option>)}
          </select>
        </div>
        <button className="btn btn--secondary" onClick={() => window.print()} disabled={!rel} style={{ marginLeft: 'auto', alignSelf: 'flex-end' }}>
          <Printer size={16} /> Imprimir / salvar PDF
        </button>
      </div>

      {erro ? (
        <div className="card"><ErroEstado mensagem={erro} onTentar={() => setTentativa((t) => t + 1)} /></div>
      ) : !rel ? (
        <div className="card"><Carregando linhas={8} /></div>
      ) : (
        <Conteudo rel={rel} />
      )}
    </div>
  )
}

function Conteudo({ rel }: { rel: Relatorio }) {
  const s = SITUACAO[rel.situacao]
  const r = rel.resumo
  const ap = rel.apuracaoPisCofins
  const periodo = rel.periodoDe
    ? rel.periodoDe === rel.periodoAte
      ? fmtCompetencia(rel.periodoDe, true)
      : `${fmtCompetencia(rel.periodoDe, true)} a ${fmtCompetencia(rel.periodoAte!, true)}`
    : 'Sem notas'
  const atencao = rel.observacoes.filter((o) => o.nivel === 'ATENCAO')
  const maior = rel.contrapartes[0]
  const pct = r.itens ? (r.itensClassificados / r.itens) * 100 : 0
  const resultado = ap.saldoCredor > 0 ? `saldo credor de ${fmtMoeda(ap.saldoCredor)}` : `${fmtMoeda(ap.aPagar)} a pagar`

  return (
    <>
      {/* 1. Informações gerais */}
      <Card className="relatorio__secao">
        <div className="relatorio__topo">
          <div>
            <span className="eyebrow">Relatório da empresa</span>
            <h2 className="relatorio__titulo">{rel.empresa.razaoSocial}</h2>
            <p className="cell-sub">CNPJ {fmtCnpj(rel.empresa.cnpj)} · {REGIME_LABEL[rel.empresa.regime]}</p>
          </div>
          <Badge cor={s.cor} title={s.dica}>{s.rotulo}</Badge>
        </div>
        <dl className="dl" style={{ marginTop: 16 }}>
          <div><dt>Identificação</dt><dd className="mono">{rel.identificacao}</dd></div>
          <div><dt>Período analisado</dt><dd>{periodo}</dd></div>
          <div><dt>Gerado em</dt><dd>{fmtDataHora(rel.geradoEm)}</dd></div>
          <div><dt>Situação</dt><dd>{s.dica}</dd></div>
        </dl>
        <p className="relatorio__nota">
          <Info size={14} /> Relatório gerado automaticamente a partir das notas fiscais importadas. Os números são dados
          das notas ou cálculos das regras de apuração do TribIA; o relatório não traz recomendações.
        </p>
      </Card>

      {rel.situacao === 'SEM_DADOS' ? (
        <div className="card"><Vazio titulo="Sem notas no período">Escolha outro período ou envie notas fiscais.</Vazio></div>
      ) : (
        <>
          {/* 2. Resumo executivo */}
          <Secao titulo="Resumo">
            <div className="relatorio__numeros">
              <Numero rotulo="Notas fiscais" valor={fmtNumero(r.notas)} detalhe={`${r.entradas} compras · ${r.saidas} vendas`} fonte="nota" />
              <Numero rotulo="Compras" valor={fmtMoeda(r.valorEntradas)} fonte="nota" />
              <Numero rotulo="Vendas" valor={fmtMoeda(r.valorSaidas)} fonte="nota" />
              <Numero rotulo="Itens classificados" valor={`${fmtNumero(pct, 1)}%`} detalhe={`${r.itensClassificados} de ${r.itens} itens`} fonte="nota" />
            </div>
            <div className="apuracao">
              <div className="apuracao__cab">
                <strong>Apuração de PIS/Cofins (regras atuais)</strong> <Fonte tipo="calculo" />
              </div>
              <div className="apuracao__conta">
                <div><span>Débito das vendas</span><b>{fmtMoeda(ap.debito)}</b></div>
                <span className="apuracao__op">−</span>
                <div><span>Crédito das compras</span><b>{fmtMoeda(ap.credito)}</b></div>
                <span className="apuracao__op">=</span>
                <div className="apuracao__resultado">
                  <span>{ap.saldoCredor > 0 ? 'Saldo credor' : 'A pagar'}</span>
                  <b>{fmtMoeda(ap.saldoCredor > 0 ? ap.saldoCredor : ap.aPagar)}</b>
                </div>
              </div>
            </div>
          </Secao>

          {/* 3. Por competência */}
          <Secao titulo="Por competência" fonte="calculo">
            <div className="table-wrap">
              <table className="table table--compact table--inset">
                <thead>
                  <tr>
                    <th>Competência</th>
                    <th className="right">Notas</th>
                    <th className="right">Compras</th>
                    <th className="right">Vendas</th>
                    <th className="right">Débito</th>
                    <th className="right">Crédito</th>
                    <th className="right">Resultado</th>
                  </tr>
                </thead>
                <tbody>
                  {rel.competencias.map((c) => (
                    <tr key={c.competencia}>
                      <td>{fmtCompetencia(c.competencia, true)}</td>
                      <td className="right num">{c.notas}</td>
                      <td className="right num">{fmtMoeda(c.valorEntradas)}</td>
                      <td className="right num">{fmtMoeda(c.valorSaidas)}</td>
                      <td className="right num">{fmtMoeda(c.apuracao.debito)}</td>
                      <td className="right num">{fmtMoeda(c.apuracao.credito)}</td>
                      <td className="right num">
                        {c.apuracao.saldoCredor > 0 ? <span title="Saldo credor">({fmtMoeda(c.apuracao.saldoCredor)})</span> : fmtMoeda(c.apuracao.aPagar)}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            <p className="cell-sub" style={{ marginTop: 8 }}>Valores entre parênteses indicam saldo credor.</p>
          </Secao>

          {/* 4. Documentos utilizados */}
          <Secao titulo="Documentos utilizados" fonte="nota" sub="Resultado por documento. PIS/Cofins apurado: débito nas vendas, crédito nas compras.">
            <div className="table-wrap">
              <table className="table table--compact table--inset">
                <thead>
                  <tr>
                    <th>Documento</th>
                    <th>Tipo</th>
                    <th>Emissão</th>
                    <th>Contraparte</th>
                    <th className="right">Valor</th>
                    <th className="right">Itens classif.</th>
                    <th className="right">PIS/Cofins na nota</th>
                    <th className="right">Apurado</th>
                  </tr>
                </thead>
                <tbody>
                  {rel.documentos.map((d) => (
                    <tr key={d.id}>
                      <td className="nowrap"><Link to={rotaNota(rel.empresa.id, d.id)}>{tituloNota(d)}</Link></td>
                      <td><BadgeTipo tipo={d.tipo} sm /></td>
                      <td className="num">{fmtData(d.dataEmissao)}</td>
                      <td>{capitalizar(d.contraparteNome)}</td>
                      <td className="right num">{fmtMoeda(d.valorTotal)}</td>
                      <td className="right num">{d.itensClassificados}/{d.itens}</td>
                      <td className="right num">{fmtMoeda(d.pisCofinsDestacado)}</td>
                      <td className="right num">{fmtMoeda(d.pisCofinsApurado)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </Secao>

          {/* 5. Relacionamentos */}
          <Secao titulo="Relacionamentos identificados" fonte="nota">
            <div className="table-wrap">
              <table className="table table--compact table--inset">
                <thead>
                  <tr>
                    <th>Contraparte</th>
                    <th>Relação</th>
                    <th className="right">Notas</th>
                    <th className="right">Valor</th>
                  </tr>
                </thead>
                <tbody>
                  {rel.contrapartes.map((c, i) => (
                    <tr key={`${c.documento}-${i}`}>
                      <td>
                        <div className="cell-main">{capitalizar(c.nome)}</div>
                        <div className="cell-sub num">{fmtCnpj(c.documento)}</div>
                      </td>
                      <td>{c.fornecedor && c.clienteFinal ? 'Fornecedor e cliente' : c.fornecedor ? 'Fornecedor' : 'Cliente final'}</td>
                      <td className="right num">{c.notas}</td>
                      <td className="right num">{fmtMoeda(c.valor)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </Secao>

          {/* 6. Observações e evidências */}
          <Secao titulo="Observações e pontos de atenção" fonte="verificacao" sub="Verificações objetivas sobre os dados das notas, com os documentos de origem.">
            {rel.observacoes.length === 0 ? (
              <p style={{ fontSize: 14, color: 'var(--text-3)' }}>Nenhuma observação para o período.</p>
            ) : (
              <ul className="observacoes">
                {rel.observacoes.map((o) => (
                  <Observacao key={o.codigo} o={o} empresaId={rel.empresa.id} />
                ))}
              </ul>
            )}
          </Secao>

          {/* 7. Conclusão */}
          <Secao titulo="Conclusão" sub="Síntese dos dados acima.">
            <p className="relatorio__sintese">
              No período de {periodo.toLowerCase()}, a empresa teve {fmtNumero(r.notas)} notas fiscais: {r.entradas} de compra
              ({fmtMoeda(r.valorEntradas)}) e {r.saidas} de venda ({fmtMoeda(r.valorSaidas)}). Pelas regras de apuração atuais,
              o PIS/Cofins resulta em {resultado}. {fmtNumero(r.itensClassificados)} de {fmtNumero(r.itens)} itens têm
              classificação tributária da reforma ({fmtNumero(pct, 1)}%).
              {maior && ` A maior relação comercial foi com ${capitalizar(maior.nome)} (${fmtMoeda(maior.valor)} em ${maior.notas} nota(s)).`}
            </p>
            {atencao.length > 0 && (
              <>
                <div className="field__label" style={{ marginTop: 14 }}>Pontos de atenção</div>
                <ul className="relatorio__lista">
                  {atencao.map((o) => <li key={o.codigo}>{o.mensagem}</li>)}
                </ul>
              </>
            )}
          </Secao>
        </>
      )}
    </>
  )
}

function Numero({ rotulo, valor, detalhe, fonte }: { rotulo: string; valor: string; detalhe?: string; fonte: 'nota' | 'calculo' }) {
  return (
    <div className="relatorio__numero" title={fonte === 'nota' ? 'Extraído das notas' : 'Calculado'}>
      <span>{rotulo}</span>
      <b>{valor}</b>
      {detalhe && <small>{detalhe}</small>}
    </div>
  )
}

const LIMITE_REFS = 8

function Observacao({ o, empresaId }: { o: Relatorio['observacoes'][number]; empresaId: number }) {
  const [aberta, setAberta] = useState(false)
  const refs = aberta ? o.referencias : o.referencias.slice(0, LIMITE_REFS)
  return (
    <li className={`observacao observacao--${o.nivel === 'ATENCAO' ? 'atencao' : 'info'}`}>
      {o.nivel === 'ATENCAO' ? <CircleAlert size={18} /> : <Info size={18} />}
      <div style={{ minWidth: 0, flex: 1 }}>
        <div className="observacao__msg">{o.mensagem}</div>
        {o.referencias.length > 0 && (
          <div className="observacao__refs">
            <span className="cell-sub">Origem:</span>
            {refs.map((r, i) => (
              <Link key={i} to={rotaNota(empresaId, r.notaId)} className="observacao__ref" title={r.descricao ?? undefined}>
                NF-e {r.numero}{r.item != null ? `, item ${r.item}` : ''}
              </Link>
            ))}
            {o.referencias.length > LIMITE_REFS && (
              <button className="link-btn no-print" style={{ fontSize: 12.5 }} onClick={() => setAberta((a) => !a)}>
                {aberta ? 'mostrar menos' : `+${o.referencias.length - LIMITE_REFS}`}
              </button>
            )}
            {o.total > o.referencias.length && (
              <span className="cell-sub">(mostrando {o.referencias.length} de {o.total})</span>
            )}
          </div>
        )}
      </div>
    </li>
  )
}
