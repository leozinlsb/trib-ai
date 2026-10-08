import { useMemo } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { Building2, ChevronRight, Cpu, Plus, Upload } from 'lucide-react'
import { Badge, Card, Carregando, ErroEstado, KpiCard, Vazio } from '../../components/ui'
import { Sparkline } from '../../components/charts/Sparkline'
import { AnelProcessamento, CardRecentes, CardRede, CardVolume } from '../../components/painel/Blocos'
import { useCobertura } from '../../hooks/useCobertura'
import { serieTemporal } from '../../lib/aggregate'
import { fmtCnpj, fmtData, fmtMoeda, fmtNumero, nomeCliente, REGIME_LABEL } from '../../lib/format'
import { rotaEmpresa } from '../../lib/rotas'
import { useAtividades, useAuth, useDados } from '../../state/contexts'

/** Painel do administrador: todas as empresas do escritório. */
export function VisaoGeral() {
  const { clientes, notas, status, erro, recarregar } = useDados()
  const { usuario } = useAuth()
  const { abrirUpload, emProcessamento } = useAtividades()
  const navigate = useNavigate()
  const carregando = status === 'carregando'
  const cobertura = useCobertura(notas)

  const ativas = clientes.filter((c) => c.ativo)
  const acumulado = useMemo(() => {
    let soma = 0
    return serieTemporal(notas, 'semanal').map((p) => (soma += p.qtdEntradas + p.qtdSaidas))
  }, [notas])

  const porEmpresa = useMemo(
    () =>
      clientes
        .map((c) => {
          const ns = notas.filter((n) => n.clienteId === c.id)
          const ultima = ns.reduce<string | null>((m, n) => (m && m > n.dataEmissao ? m : n.dataEmissao), null)
          return { c, notas: ns.length, valor: ns.reduce((s, n) => s + n.valorTotal, 0), ultima, cob: cobertura.porEmpresa.get(c.id) }
        })
        .sort((a, b) => Number(b.c.ativo) - Number(a.c.ativo) || nomeCliente(a.c).localeCompare(nomeCliente(b.c))),
    [clientes, notas, cobertura.porEmpresa],
  )

  return (
    <>
      <div className="page-head">
        <div>
          <h1>Olá, {usuario?.nome.split(' ')[0]}</h1>
          <p>Visão geral de todas as empresas do escritório</p>
        </div>
        <div className="page-head__actions">
          <Link to="/dashboard/empresas?nova=1" className="btn btn--secondary">
            <Plus size={18} /> Nova empresa
          </Link>
          <button className="btn btn--primary" onClick={() => abrirUpload()} disabled={ativas.length === 0}>
            <Upload size={18} strokeWidth={2.1} /> Enviar notas
          </button>
        </div>
      </div>

      {status === 'erro' && (
        <div className="card mb-gap">
          <ErroEstado mensagem={erro ?? ''} onTentar={recarregar} />
        </div>
      )}

      <div className="grid-kpi">
        <KpiCard
          rotulo="Empresas Ativas"
          valor={fmtNumero(ativas.length)}
          dica={clientes.length > ativas.length ? `${clientes.length - ativas.length} desativada(s)` : 'todas ativas'}
          carregando={carregando}
          arte={<Building2 size={30} strokeWidth={1.6} />}
        />
        <KpiCard
          rotulo="Documentos Analisados"
          valor={fmtNumero(notas.length)}
          dica="NF-e lidas e validadas"
          carregando={carregando}
          arte={<Sparkline valores={acumulado} rotulo="Total acumulado de notas por semana" />}
        />
        <KpiCard
          rotulo="Itens Classificados"
          valor={cobertura.total ? `${fmtNumero((cobertura.classificados / cobertura.total) * 100, 1)}%` : '—'}
          dica={cobertura.total ? `${fmtNumero(cobertura.classificados)} de ${fmtNumero(cobertura.total)} itens` : 'Nenhum item'}
          carregando={carregando || !cobertura.completo}
          arte={<Cpu size={30} strokeWidth={1.6} />}
        />
        <KpiCard
          rotulo="Em Processamento"
          valor={fmtNumero(emProcessamento)}
          dica={emProcessamento ? 'arquivo(s) em envio agora' : 'Nenhum envio em andamento'}
          arte={<AnelProcessamento ativo={emProcessamento > 0} />}
        />
      </div>

      <Card
        titulo="Empresas"
        sub="Selecione uma empresa para acessar documentos, análises e relatórios."
        corpo="flush"
        className="mb-gap"
        acoes={<Link to="/dashboard/empresas" style={{ fontSize: 13, fontWeight: 500 }}>Gerenciar</Link>}
      >
        {carregando ? (
          <Carregando linhas={3} />
        ) : porEmpresa.length === 0 ? (
          <Vazio titulo="Nenhuma empresa cadastrada">
            <Link to="/dashboard/empresas?nova=1">Cadastre a primeira empresa</Link>
          </Vazio>
        ) : (
          <div className="table-wrap">
            <table className="table table--compact">
              <thead>
                <tr>
                  <th>Empresa</th>
                  <th>Regime</th>
                  <th className="right">Notas</th>
                  <th className="right">Valor</th>
                  <th>Classificação</th>
                  <th>Última nota</th>
                  <th aria-label="Abrir" />
                </tr>
              </thead>
              <tbody>
                {porEmpresa.map(({ c, notas: qtd, valor, ultima, cob }) => (
                  <tr key={c.id} className="is-link" onClick={() => navigate(rotaEmpresa(c.id))}>
                    <td>
                      <Link to={rotaEmpresa(c.id)} className="cell-main" style={{ color: 'inherit' }} onClick={(e) => e.stopPropagation()}>
                        {nomeCliente(c)}
                      </Link>{' '}
                      {!c.ativo && <Badge cor="gray" sm>Desativada</Badge>}
                      <div className="cell-sub num">{fmtCnpj(c.cnpj)}</div>
                    </td>
                    <td>{REGIME_LABEL[c.regime]}</td>
                    <td className="right num">{fmtNumero(qtd)}</td>
                    <td className="right num">{fmtMoeda(valor)}</td>
                    <td>
                      {!cob ? (
                        <span className="cell-sub">{qtd ? '…' : '—'}</span>
                      ) : cob.total === cob.classificados ? (
                        <Badge cor="green" sm>Completa</Badge>
                      ) : (
                        <Badge cor="gray" sm>{fmtNumero(cob.total - cob.classificados)} pendente(s)</Badge>
                      )}
                    </td>
                    <td className="num">{fmtData(ultima)}</td>
                    <td className="right"><ChevronRight size={16} color="var(--muted)" /></td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </Card>

      <div className="grid-charts">
        <CardVolume notas={notas} carregando={carregando} />
        <CardRede clientes={ativas} notas={notas.filter((n) => ativas.some((c) => c.id === n.clienteId))} carregando={carregando} destino="/dashboard/empresas" />
      </div>

      <CardRecentes notas={notas} carregando={carregando} verTodos="/dashboard/documentos" mostrarEmpresa />
    </>
  )
}
