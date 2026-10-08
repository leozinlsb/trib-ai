import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { ArrowRight } from 'lucide-react'
import { indicadores, servicoIndisponivel, type IndicadoresFiscais } from '../../api/inteligenciaFiscal'
import { ServicoIndisponivel } from '../../components/fiscal/Comum'
import { Badge, Card, Carregando, Vazio } from '../../components/ui'
import { fmtCnpj, fmtNumero, nomeCliente } from '../../lib/format'
import { rotaAnaliseFiscal } from '../../lib/rotas'
import { useDados } from '../../state/contexts'

type Linha = IndicadoresFiscais | 'indisponivel' | 'erro' | undefined

/** Visão do administrador: escolher a empresa cujas análises fiscais serão consultadas. */
export function InteligenciaFiscalAdmin() {
  const { clientes, status } = useDados()
  const ativas = clientes.filter((c) => c.ativo)
  const [porEmpresa, setPorEmpresa] = useState<Record<number, Linha>>({})

  useEffect(() => {
    if (status !== 'pronto') return
    const ctrl = new AbortController()
    ativas.forEach((c) =>
      indicadores(c.id, ctrl.signal)
        .then((k) => setPorEmpresa((m) => ({ ...m, [c.id]: k })))
        .catch((e) => {
          if (e instanceof DOMException && e.name === 'AbortError') return
          setPorEmpresa((m) => ({ ...m, [c.id]: servicoIndisponivel(e) ? 'indisponivel' : 'erro' }))
        }),
    )
    return () => ctrl.abort()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [status, clientes])

  const indisponivel = Object.values(porEmpresa).some((v) => v === 'indisponivel')

  return (
    <>
      <div className="page-head">
        <div>
          <h1>Inteligência Fiscal</h1>
          <p>Analise mercadorias, encontre classificações NCM e consulte validações fiscais em um único lugar.</p>
        </div>
      </div>

      {indisponivel && <div className="mb-gap"><ServicoIndisponivel /></div>}

      <Card titulo="Selecione uma empresa" sub="As análises fiscais são feitas e consultadas no ambiente de cada empresa." corpo="flush">
        {status === 'carregando' ? (
          <Carregando linhas={3} />
        ) : ativas.length === 0 ? (
          <Vazio titulo="Nenhuma empresa ativa"><Link to="/dashboard/empresas">Gerenciar empresas</Link></Vazio>
        ) : (
          <div className="table-wrap">
            <table className="table table--compact">
              <thead>
                <tr>
                  <th>Empresa</th>
                  <th className="right">Análises</th>
                  <th className="right">Em processamento</th>
                  <th className="right">Aguardando revisão</th>
                  <th aria-label="Abrir" />
                </tr>
              </thead>
              <tbody>
                {ativas.map((c) => {
                  const k = porEmpresa[c.id]
                  const valor = (f: keyof IndicadoresFiscais) =>
                    k === undefined ? <span className="skeleton" style={{ display: 'inline-block', width: 24, height: 14 }} /> : typeof k === 'object' ? fmtNumero(k[f]) : <span className="muted">—</span>
                  return (
                    <tr key={c.id}>
                      <td>
                        <span className="cell-main">{nomeCliente(c)}</span>
                        <div className="cell-sub num">{fmtCnpj(c.cnpj)}</div>
                      </td>
                      <td className="right num">{valor('total')}</td>
                      <td className="right num">{valor('emProcessamento')}</td>
                      <td className="right num">
                        {typeof k === 'object' && k.aguardandoRevisao > 0 ? <Badge cor="amber" sm>{fmtNumero(k.aguardandoRevisao)}</Badge> : valor('aguardandoRevisao')}
                      </td>
                      <td className="right">
                        <Link to={rotaAnaliseFiscal(c.id)} className="btn btn--secondary btn--sm">
                          Abrir <ArrowRight size={14} />
                        </Link>
                      </td>
                    </tr>
                  )
                })}
              </tbody>
            </table>
          </div>
        )}
      </Card>
    </>
  )
}
