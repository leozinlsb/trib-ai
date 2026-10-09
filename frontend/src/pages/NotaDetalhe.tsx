import { useEffect, useState } from 'react'
import { Link, Navigate, useParams } from 'react-router-dom'
import { Check, ChevronRight, Copy, LoaderCircle, RefreshCw, Sparkles } from 'lucide-react'
import { ApiError } from '../api/client'
import { calcularNota, confirmarPagamento, detalharNota } from '../api/tribia'
import type { NotaDetalhe as Detalhe } from '../api/types'
import { Aviso, Badge, Card, Carregando, Confirmacao, ErroEstado, KpiCard, Vazio } from '../components/ui'
import { BadgeClassificacao, BadgeTipo } from '../components/notas'
import { CelulaClassificacao } from '../components/classificacao'
import { itemClassificado } from '../lib/aggregate'
import {
  capitalizar, fmtChave, fmtCnpj, fmtCompetencia, fmtData, fmtMoeda, fmtNumero, nomeCliente, REGIME_LABEL, tituloNota,
} from '../lib/format'
import { useAtividades, useDados, useToast } from '../state/contexts'
import { rotaEmpresa, rotaNota } from '../lib/rotas'

export function NotaDetalhe() {
  const { id, empresaId } = useParams()
  const { clientes, versaoFiscal, atualizarDetalhes, marcarAlteracaoFiscal } = useDados()
  const { processar, processando } = useAtividades()
  const { mostrar } = useToast()
  const [recalculando, setRecalculando] = useState(false)
  const [alterandoPagamento, setAlterandoPagamento] = useState(false)
  const [confirmarPagamentoAberto, setConfirmarPagamentoAberto] = useState(false)
  const [nota, setNota] = useState<Detalhe | null>(null)
  const [erro, setErro] = useState<{ msg: string; status: number } | null>(null)
  const [tentativa, setTentativa] = useState(0)
  const [copiado, setCopiado] = useState(false)
  const [processandoNota, setProcessandoNota] = useState(false)

  useEffect(() => {
    const ctrl = new AbortController()
    setErro(null)
    detalharNota(Number(id), ctrl.signal)
      .then(setNota)
      .catch((e) => {
        if (e instanceof DOMException && e.name === 'AbortError') return
        setErro({ msg: e instanceof Error ? e.message : 'Falha ao carregar.', status: e instanceof ApiError ? e.status : 0 })
      })
    return () => ctrl.abort()
    // versaoFiscal: recarrega depois de processar ou revisar (mantém a nota atual na tela enquanto isso)
  }, [id, tentativa, versaoFiscal])

  // outra nota no endereço: limpa a anterior para não mostrar dados trocados
  useEffect(() => setNota(null), [id])

  const processarNota = async () => {
    if (!nota) return
    setProcessandoNota(true)
    try {
      await processar([nota.id])
    } finally {
      setProcessandoNota(false)
    }
  }

  /** Mensagem curta do resultado do cálculo: o primeiro aviso do servidor, se houver. */
  const resumoCalculo = (avisos: string[]) => (avisos.length ? avisos[0] : undefined)

  const recalcular = async () => {
    if (!nota || recalculando) return
    setRecalculando(true)
    try {
      const r = await calcularNota(nota.id)
      await atualizarDetalhes([nota.id])
      marcarAlteracaoFiscal()
      mostrar({ tipo: 'sucesso', titulo: `Nota recalculada (${r.itensCalculados} itens)`, texto: resumoCalculo(r.avisos) })
    } catch (e) {
      mostrar({ tipo: 'erro', titulo: 'Não foi possível recalcular', texto: e instanceof Error ? e.message : undefined })
    } finally {
      setRecalculando(false)
    }
  }

  // erros sobem para a Confirmacao, que os mostra no próprio modal
  const alterarPagamento = async (confirmado: boolean) => {
    if (!nota) return
    setAlterandoPagamento(true)
    try {
      const r = await confirmarPagamento(nota.id, confirmado)
      await atualizarDetalhes([nota.id])
      marcarAlteracaoFiscal()
      mostrar({
        tipo: 'sucesso',
        titulo: confirmado ? 'Pagamento confirmado' : 'Compra marcada como não paga',
        texto: resumoCalculo(r.avisos.filter((a) => a.startsWith('Pagamento')).concat(r.avisos)),
      })
    } finally {
      setAlterandoPagamento(false)
    }
  }

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
  const calculados = nota.itens.filter((i) => i.calculo)
  const hoje = calculados.reduce((s, i) => s + (i.calculo!.impostoHoje ?? 0), 0)
  const em2027 = calculados.reduce((s, i) => s + (i.calculo!.imposto2027 ?? 0), 0)
  const simplificado = calculados.some((i) => i.calculo!.origemValores === 'SIMPLIFICADA')
  const pendentes = nota.itens.filter((i) => !i.classificacao).length
  const ocupado = processandoNota || processando > 0
  const lado = nota.tipo === 'SAIDA' ? 'débito' : 'crédito'

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
        {pendentes > 0 || calculados.length < nota.itens.length ? (
          <button
            className="btn btn--primary"
            onClick={processarNota}
            disabled={ocupado || recalculando}
            title="Classifica os itens pendentes (XML, cache e IA) e calcula 2027"
          >
            {ocupado ? <LoaderCircle size={17} className="spin" /> : <Sparkles size={17} />}
            {ocupado ? 'Processando...' : 'Processar nota'}
          </button>
        ) : (
          <button
            className="btn btn--secondary"
            onClick={recalcular}
            disabled={ocupado || recalculando || alterandoPagamento}
            title="Refaz o cálculo de 2027 com a classificação atual (ex.: depois de a calculadora oficial voltar)"
          >
            {recalculando ? <LoaderCircle size={17} className="spin" /> : <RefreshCw size={17} />}
            {recalculando ? 'Recalculando...' : 'Recalcular 2027'}
          </button>
        )}
      </div>

      <div className="grid-kpi">
        <KpiCard rotulo="Valor total" valor={fmtMoeda(nota.valorTotal)} dica={`Produtos: ${fmtMoeda(nota.valorProdutos)}`} />
        <KpiCard rotulo="Itens" valor={fmtNumero(nota.itens.length)} dica={`${classificados} com classificação tributária`} />
        <KpiCard
          rotulo={`PIS/Cofins hoje (${lado})`}
          valor={calculados.length ? fmtMoeda(hoje) : fmtMoeda(pisCofins)}
          dica={calculados.length ? `${calculados.length} itens calculados` : 'destacado na nota (soma dos itens)'}
        />
        <KpiCard
          rotulo={`CBS/IBS/IS 2027 (${lado})`}
          valor={calculados.length ? fmtMoeda(em2027) : '—'}
          dica={calculados.length ? (simplificado ? 'Simulação: cálculo simplificado' : 'Simulação: calculadora oficial') : 'Processe a nota para calcular'}
          indisponivel={!calculados.length}
        />
      </div>

      {calculados.length > 0 && (
        <Aviso tipo="warn" style={{ marginBottom: 'var(--gap)' }}>
          Valores de 2027 são projeção pendente de validação fiscal: alíquota da CBS estimada e base sem ICMS, PIS e
          Cofins da nota (hipótese ainda não confirmada por especialista). Dependem da classificação de cada item.
          ICMS destacado nesta nota: {fmtMoeda(icms)} (não muda em 2027, fica fora do comparativo).
        </Aviso>
      )}

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
          {nota.operacao === 'COMPRA' && nota.pagamentoConfirmado != null && (
            <div>
              <dt>Pagamento ao fornecedor</dt>
              <dd style={{ display: 'flex', alignItems: 'center', gap: 10, flexWrap: 'wrap' }}>
                {nota.pagamentoConfirmado
                  ? <Badge cor="green" sm>Confirmado</Badge>
                  : <Badge cor="amber" sm title="Sem pagamento confirmado, a compra não gera crédito de 2027">Não confirmado</Badge>}
                <button
                  className="btn btn--ghost btn--sm"
                  disabled={ocupado || alterandoPagamento}
                  onClick={() => setConfirmarPagamentoAberto(true)}
                >
                  {nota.pagamentoConfirmado ? 'Marcar como não pago' : 'Confirmar pagamento'}
                </button>
              </dd>
            </div>
          )}
        </dl>
      </Card>

      {confirmarPagamentoAberto && (
        <Confirmacao
          titulo={nota.pagamentoConfirmado ? 'Marcar a compra como não paga?' : 'Confirmar o pagamento da compra?'}
          rotulo={nota.pagamentoConfirmado ? 'Marcar como não pago' : 'Confirmar pagamento'}
          perigo={false}
          onFechar={() => setConfirmarPagamentoAberto(false)}
          onConfirmar={async () => {
            await alterarPagamento(!nota.pagamentoConfirmado)
            setConfirmarPagamentoAberto(false)
          }}
        >
          <p>
            Na reforma, o crédito de CBS/IBS da compra depende do pagamento ao fornecedor (LC 214/2025, art. 47).
            {nota.pagamentoConfirmado
              ? ' Marcada como não paga, esta compra deixa de gerar crédito de 2027 na simulação.'
              : ' Com o pagamento confirmado, esta compra volta a gerar crédito de 2027 na simulação.'}
          </p>
          <p style={{ marginTop: 10 }}>A nota é recalculada em seguida. Você pode desfazer a qualquer momento.</p>
        </Confirmacao>
      )}

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
                <th>Classificação (reforma)</th>
                <th className="right" title="PIS/Cofins pelas regras de hoje">Hoje</th>
                <th className="right" title="CBS + IBS + IS simulados para 2027">2027</th>
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
                  <td><CelulaClassificacao item={i} /></td>
                  <td className="right num">{i.calculo ? fmtMoeda(i.calculo.impostoHoje) : '—'}</td>
                  <td className="right num" title={i.calculo ? `CBS ${fmtMoeda(i.calculo.vCbs)} · IBS ${fmtMoeda((i.calculo.vIbsUf ?? 0) + (i.calculo.vIbsMun ?? 0))} · IS ${fmtMoeda(i.calculo.vIs)}` : undefined}>
                    {i.calculo ? fmtMoeda(i.calculo.imposto2027) : '—'}
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
