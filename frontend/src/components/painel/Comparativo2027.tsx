import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { ArrowRight } from 'lucide-react'
import { painelEmpresa } from '../../api/tribia'
import type { Painel } from '../../api/types'
import { capitalizar, fmtMoeda, fmtNumero } from '../../lib/format'
import { REGIME_TRIB_LABEL } from '../../lib/classificacao'
import { rotaEmpresa } from '../../lib/rotas'
import { useDados } from '../../state/contexts'
import { Aviso, Badge, Card, Carregando, ErroEstado } from '../ui'

/**
 * Comparativo hoje (PIS/Cofins) x 2027 (CBS/IBS/IS) da empresa: GET /api/clientes/{id}/dashboard.
 * O painel só lê o que já foi classificado e calculado; itens pendentes ficam fora (o backend avisa).
 */
export function Comparativo2027({ id }: { id: number }) {
  const { versaoFiscal } = useDados()
  const [painel, setPainel] = useState<Painel | null>(null)
  const [erro, setErro] = useState<string | null>(null)
  const [tentativa, setTentativa] = useState(0)

  useEffect(() => {
    const ctrl = new AbortController()
    setErro(null)
    painelEmpresa(id, {}, ctrl.signal)
      .then(setPainel)
      .catch((e) => {
        if (e instanceof DOMException && e.name === 'AbortError') return
        setErro(e instanceof Error ? e.message : 'Falha ao carregar o comparativo.')
      })
    return () => ctrl.abort()
  }, [id, tentativa, versaoFiscal])

  const titulo = 'Impacto da reforma: hoje × 2027'
  if (erro) {
    return (
      <Card titulo={titulo}>
        <ErroEstado mensagem={erro} onTentar={() => setTentativa((t) => t + 1)} />
      </Card>
    )
  }
  if (!painel) {
    return <Card titulo={titulo}><Carregando linhas={4} /></Card>
  }

  const ind = painel.indicadores
  const variacao = ind.variacaoPct
  const sobe = (ind.liquido2027 ?? 0) > (ind.liquidoHoje ?? 0)
  const corVariacao = variacao == null ? 'gray' : sobe ? 'red' : 'green'
  const top = painel.topItens.filter((t) => t.diferenca !== 0).slice(0, 5)

  return (
    <Card
      titulo={titulo}
      sub="Imposto líquido (débito − crédito) do período, com a classificação atual de cada item."
      acoes={
        ind.pendentesRevisao > 0 ? (
          <Link to={rotaEmpresa(id, 'revisao')} style={{ fontSize: 13, fontWeight: 500 }}>
            {fmtNumero(ind.pendentesRevisao)} item(ns) para revisar
          </Link>
        ) : undefined
      }
    >
      <div className="resumo-periodo">
        <div className="resumo-periodo__linha">
          <span>Hoje · PIS/Cofins</span>
          <b>{fmtMoeda(ind.liquidoHoje)}</b>
        </div>
        <div className="resumo-periodo__linha">
          <span>2027 · CBS + IBS + IS</span>
          <b>{fmtMoeda(ind.liquido2027)}</b>
        </div>
        <div className="resumo-periodo__linha">
          <span>Crédito de CBS/IBS das compras</span>
          <b>{fmtMoeda(ind.credito2027)}</b>
        </div>
        <div className="resumo-periodo__destaque">
          <span>{ind.saldoCredor ? 'Em 2027 a empresa fica com saldo credor' : 'Variação do imposto líquido'}</span>
          <b>
            {variacao == null ? '—' : (
              <Badge cor={corVariacao}>{variacao > 0 ? '+' : ''}{fmtNumero(variacao, 1)}%</Badge>
            )}
          </b>
        </div>
        <div className="cell-sub">
          Faturamento {fmtMoeda(ind.faturamento)} · compras {fmtMoeda(ind.compras)}.
          {variacao == null ? ' Variação não calculada: hoje não há PIS/Cofins líquido a pagar.' : ''}
        </div>
      </div>

      {top.length > 0 && (
        <div className="table-wrap" style={{ marginTop: 14 }}>
          <table className="table table--compact">
            <thead>
              <tr>
                <th>Produtos que mais mudam</th>
                <th>Regra</th>
                <th className="right">Diferença</th>
              </tr>
            </thead>
            <tbody>
              {top.map((t, k) => (
                <tr key={`${t.ncm}-${t.descricao}-${k}`}>
                  <td>
                    <div className="cell-main">{capitalizar(t.descricao)}</div>
                    <div className="cell-sub mono">NCM {t.ncm ?? '—'} · {t.cClassTrib ?? 'sem classificação'}</div>
                  </td>
                  <td className="cell-sub">{t.regime ? REGIME_TRIB_LABEL[t.regime] : '—'}</td>
                  <td className="right num" style={{ color: t.diferenca > 0 ? 'var(--danger)' : 'var(--green-600)' }}>
                    {t.diferenca > 0 ? '+' : ''}{fmtMoeda(t.diferenca)}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {painel.avisos.length > 0 && (
        <Aviso tipo="warn" style={{ marginTop: 14 }}>
          <ul style={{ margin: 0, paddingLeft: 16 }}>
            {painel.avisos.map((a) => <li key={a}>{a}</li>)}
          </ul>
        </Aviso>
      )}

      {ind.pendentesRevisao > 0 && (
        <div style={{ marginTop: 12 }}>
          <Link to={rotaEmpresa(id, 'revisao')} className="btn btn--secondary btn--sm">
            Revisar classificações <ArrowRight size={14} />
          </Link>
        </div>
      )}
    </Card>
  )
}
