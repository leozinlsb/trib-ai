import { useEffect, useState } from 'react'
import { Link, Navigate, useParams } from 'react-router-dom'
import { Check, ChevronRight, Copy } from 'lucide-react'
import { ApiError } from '../api/client'
import { detalharNota } from '../api/tribia'
import type { NotaDetalhe as Detalhe } from '../api/types'
import { Badge, Card, Carregando, ErroEstado, KpiCard, Vazio } from '../components/ui'
import { BadgeClassificacao, BadgeTipo } from '../components/notas'
import { itemClassificado } from '../lib/aggregate'
import {
  capitalizar, fmtChave, fmtCnpj, fmtCompetencia, fmtData, fmtMoeda, fmtNumero, nomeCliente, REGIME_LABEL, tituloNota,
} from '../lib/format'
import { useDados } from '../state/contexts'
import { rotaEmpresa, rotaNota } from '../lib/rotas'

export function NotaDetalhe() {
  const { id, empresaId } = useParams()
  const { clientes } = useDados()
  const [nota, setNota] = useState<Detalhe | null>(null)
  const [erro, setErro] = useState<{ msg: string; status: number } | null>(null)
  const [tentativa, setTentativa] = useState(0)
  const [copiado, setCopiado] = useState(false)

  useEffect(() => {
    const ctrl = new AbortController()
    setNota(null)
    setErro(null)
    detalharNota(Number(id), ctrl.signal)
      .then(setNota)
      .catch((e) => {
        if (e instanceof DOMException && e.name === 'AbortError') return
        setErro({ msg: e instanceof Error ? e.message : 'Falha ao carregar.', status: e instanceof ApiError ? e.status : 0 })
      })
    return () => ctrl.abort()
  }, [id, tentativa])

  const cliente = nota ? clientes.find((c) => c.id === nota.clienteId) : undefined

  const copiar = async () => {
    if (!nota) return
    try {
      await navigator.clipboard.writeText(nota.chave)
      setCopiado(true)
      window.setTimeout(() => setCopiado(false), 1500)
    } catch {
      // clipboard indisponível (contexto inseguro): o usuário pode selecionar o texto
    }
  }

  // a nota pertence a outra empresa (endereço digitado): leva ao ambiente correto
  if (nota && nota.clienteId !== Number(empresaId)) {
    return <Navigate to={rotaNota(nota.clienteId, nota.id)} replace />
  }

  const trilha = (
    <nav className="breadcrumb" aria-label="Trilha">
      {cliente && (
        <>
          <Link to={rotaEmpresa(cliente.id)}>{nomeCliente(cliente)}</Link>
          <ChevronRight size={14} />
        </>
      )}
      <Link to={rotaEmpresa(Number(empresaId), 'documentos')}>Documentos</Link>
      <ChevronRight size={14} />
      <span>{nota ? tituloNota(nota) : `Nota ${id}`}</span>
    </nav>
  )

  if (erro) {
    return (
      <>
        {trilha}
        <div className="card">
          {erro.status === 404 ? (
            <Vazio titulo="Nota não encontrada">
              {erro.msg}. Ela pode ter sido removida ou o endereço está incorreto.
            </Vazio>
          ) : (
            <ErroEstado mensagem={erro.msg} onTentar={() => setTentativa((t) => t + 1)} />
          )}
        </div>
      </>
    )
  }

  if (!nota) {
    return (
      <>
        {trilha}
        <div className="card"><Carregando linhas={8} /></div>
      </>
    )
  }

  const somar = (f: (i: Detalhe['itens'][number]) => number | null) => nota.itens.reduce((s, i) => s + (f(i) ?? 0), 0)
  const pisCofins = somar((i) => (i.vPis ?? 0) + (i.vCofins ?? 0))
  const icms = somar((i) => i.vIcms)
  const classificados = nota.itens.filter(itemClassificado).length

  return (
    <>
      {trilha}
      <div className="page-head">
        <div>
          <h1 style={{ display: 'flex', alignItems: 'center', gap: 10, flexWrap: 'wrap' }}>
            {tituloNota(nota)} <BadgeTipo tipo={nota.tipo} /> <BadgeClassificacao detalhe={nota} />
          </h1>
          <p>
            {nota.tipo === 'SAIDA' ? 'Venda para ' : 'Compra de '}
            {capitalizar(nota.contraparteNome)} · emitida em {fmtData(nota.dataEmissao)}
          </p>
        </div>
      </div>

      <div className="grid-kpi">
        <KpiCard rotulo="Valor total" valor={fmtMoeda(nota.valorTotal)} dica={`Produtos: ${fmtMoeda(nota.valorProdutos)}`} />
        <KpiCard rotulo="Itens" valor={fmtNumero(nota.itens.length)} dica={`${classificados} com classificação tributária`} />
        <KpiCard rotulo="PIS + Cofins destacados" valor={fmtMoeda(pisCofins)} dica="soma dos itens" />
        <KpiCard rotulo="ICMS destacado" valor={fmtMoeda(icms)} dica="soma dos itens" />
      </div>

      <Card titulo="Dados da nota" className="" >
        <dl className="dl">
          <div style={{ gridColumn: 'span 2' }}>
            <dt>Chave de acesso</dt>
            <dd className="copy-row">
              <span className="mono">{fmtChave(nota.chave)}</span>
              <button className="icon-btn" style={{ width: 28, height: 28, flex: 'none' }} onClick={copiar} aria-label="Copiar chave de acesso">
                {copiado ? <Check size={15} color="var(--green-600)" /> : <Copy size={15} />}
              </button>
            </dd>
          </div>
          <div><dt>Competência</dt><dd>{fmtCompetencia(nota.competencia, true)}</dd></div>
          <div><dt>Número / Série</dt><dd>{nota.numero ?? '—'} / {nota.serie ?? '—'}</dd></div>
          <div><dt>Emitente</dt><dd>{capitalizar(nota.emitenteNome)}<div className="cell-sub num">{fmtCnpj(nota.emitenteCnpj)}</div></dd></div>
          <div><dt>Destinatário</dt><dd>{capitalizar(nota.destinatarioNome)}<div className="cell-sub num">{fmtCnpj(nota.destinatarioDocumento)}</div></dd></div>
          {cliente && (
            <div>
              <dt>Cliente do escritório</dt>
              <dd>
                <Link to={rotaEmpresa(cliente.id)}>{nomeCliente(cliente)}</Link>
                <div className="cell-sub">{REGIME_LABEL[cliente.regime]}</div>
              </dd>
            </div>
          )}
        </dl>
      </Card>

      <div style={{ height: 'var(--gap)' }} />

      <Card titulo="Itens da nota" sub="Produtos, valores e tributos de cada item da nota fiscal." corpo="flush">
        <div className="table-wrap">
          <table className="table table--compact">
            <thead>
              <tr>
                <th>#</th>
                <th>Descrição</th>
                <th>NCM</th>
                <th>CFOP</th>
                <th className="right">Qtd.</th>
                <th className="right">Vl. unit.</th>
                <th className="right">Vl. total</th>
                <th className="center">CST PIS/Cofins</th>
                <th className="right">PIS</th>
                <th className="right">Cofins</th>
                <th>Creditável</th>
                <th>Classificação</th>
              </tr>
            </thead>
            <tbody>
              {nota.itens.map((i) => (
                <tr key={i.id}>
                  <td className="num">{i.nItem}</td>
                  <td>
                    <div className="cell-main">{capitalizar(i.descricao)}</div>
                    <div className="cell-sub">Cód. {i.codigo ?? '—'}</div>
                  </td>
                  <td className="mono">{i.ncm ?? '—'}</td>
                  <td className="mono">{i.cfop ?? '—'}</td>
                  <td className="right num">
                    {fmtNumero(i.quantidade)} <span className="cell-sub">{i.unidade}</span>
                  </td>
                  <td className="right num">{fmtMoeda(i.valorUnitario)}</td>
                  <td className="right num">{fmtMoeda(i.valorTotal)}</td>
                  <td className="center mono">{i.cstPisCofins ?? '—'}</td>
                  <td className="right num">{fmtMoeda(i.vPis)}</td>
                  <td className="right num">{fmtMoeda(i.vCofins)}</td>
                  <td>{i.creditavel ? <Badge cor="green" sm>Sim</Badge> : <Badge cor="gray" sm>Não</Badge>}</td>
                  <td>
                    {i.ibsCbsDestacado && itemClassificado(i) ? (
                      <span title={`CBS ${fmtMoeda(i.ibsCbsDestacado.vCbs)} · IBS ${fmtMoeda(i.ibsCbsDestacado.vIbs)}`}>
                        <Badge cor="green" sm>CST {i.ibsCbsDestacado.cst} · {i.ibsCbsDestacado.cClassTrib}</Badge>
                      </span>
                    ) : (
                      <Badge cor="gray" sm title="Item ainda sem classificação tributária">Pendente</Badge>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </Card>
    </>
  )
}
