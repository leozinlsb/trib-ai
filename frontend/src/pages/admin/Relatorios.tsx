import { useMemo } from 'react'
import { Link } from 'react-router-dom'
import { ArrowRight } from 'lucide-react'
import { Badge, Card, Carregando, ErroEstado, Vazio } from '../../components/ui'
import { competencias } from '../../lib/aggregate'
import { fmtCnpj, fmtCompetencia, fmtNumero, nomeCliente } from '../../lib/format'
import { rotaEmpresa } from '../../lib/rotas'
import { useDados } from '../../state/contexts'

/** Ponto de entrada dos relatórios: um por empresa, gerado a partir das notas do período escolhido. */
export function Relatorios() {
  const { clientes, notas, status, erro, recarregar } = useDados()

  const linhas = useMemo(
    () =>
      clientes
        .map((c) => {
          const comps = competencias(notas.filter((n) => n.clienteId === c.id)).slice().sort()
          return { c, comps, notas: notas.filter((n) => n.clienteId === c.id).length }
        })
        .sort((a, b) => Number(b.c.ativo) - Number(a.c.ativo) || nomeCliente(a.c).localeCompare(nomeCliente(b.c))),
    [clientes, notas],
  )

  return (
    <>
      <div className="page-head">
        <div>
          <h1>Relatórios</h1>
          <p>Relatório de cada empresa (notas fiscais e apuração) e relatórios das análises fiscais de mercadorias</p>
        </div>
      </div>

      <Card corpo="flush" titulo="Relatórios por empresa" sub="Os relatórios são gerados na hora, a partir das notas do período escolhido.">
        {status === 'carregando' ? (
          <Carregando linhas={4} />
        ) : status === 'erro' ? (
          <ErroEstado mensagem={erro ?? ''} onTentar={recarregar} />
        ) : linhas.length === 0 ? (
          <Vazio titulo="Nenhuma empresa cadastrada" />
        ) : (
          <div className="table-wrap">
            <table className="table table--compact">
              <thead>
                <tr>
                  <th>Empresa</th>
                  <th>Período disponível</th>
                  <th className="right">Competências</th>
                  <th className="right">Notas</th>
                  <th aria-label="Abrir" />
                </tr>
              </thead>
              <tbody>
                {linhas.map(({ c, comps, notas: qtd }) => (
                  <tr key={c.id}>
                    <td>
                      <span className="cell-main">{nomeCliente(c)}</span>{' '}
                      {!c.ativo && <Badge cor="gray" sm>Desativada</Badge>}
                      <div className="cell-sub num">{fmtCnpj(c.cnpj)}</div>
                    </td>
                    <td>
                      {comps.length
                        ? `${fmtCompetencia(comps[0]!, true)} a ${fmtCompetencia(comps[comps.length - 1]!, true)}`
                        : <span className="muted">Sem notas</span>}
                    </td>
                    <td className="right num">{fmtNumero(comps.length)}</td>
                    <td className="right num">{fmtNumero(qtd)}</td>
                    <td className="right">
                      <div className="acoes-linha">
                        <Link to={rotaEmpresa(c.id, 'inteligencia-fiscal')} className="btn btn--ghost btn--sm">
                          Análises fiscais
                        </Link>
                        <Link to={rotaEmpresa(c.id, 'analises')} className="btn btn--secondary btn--sm">
                          Abrir relatório <ArrowRight size={14} />
                        </Link>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </Card>
    </>
  )
}
