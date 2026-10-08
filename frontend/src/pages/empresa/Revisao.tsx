import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { Check, CircleCheck, LoaderCircle, Pencil, X } from 'lucide-react'
import { listarRevisao, opcoesClassificacao, revisarItem } from '../../api/tribia'
import type { ItemRevisao, MotivoRevisao, OpcaoClassificacao, Revisao as RevisaoDto, RevisarItem } from '../../api/types'
import { CabecalhoEmpresa, EstadoEmpresa } from '../../components/empresas/CabecalhoEmpresa'
import { Aviso, Badge, Card, Carregando, ErroEstado, Vazio } from '../../components/ui'
import { BadgeTipo } from '../../components/notas'
import { useEmpresa } from '../../hooks/useEmpresa'
import { corClassificacao, fmtConfianca, ORIGEM_LABEL, REGIME_TRIB_LABEL } from '../../lib/classificacao'
import { capitalizar, fmtCompetencia, fmtMoeda, fmtNumero } from '../../lib/format'
import { rotaNota } from '../../lib/rotas'
import { useAtividades, useDados, useToast } from '../../state/contexts'

const MOTIVO: Record<MotivoRevisao, { rotulo: string; cor: 'gray' | 'amber' | 'blue' }> = {
  SEM_CLASSIFICACAO: { rotulo: 'Sem classificação', cor: 'gray' },
  NAO_ACEITA: { rotulo: 'Não aceita', cor: 'blue' },
  CONFIANCA_BAIXA: { rotulo: 'Confiança baixa', cor: 'amber' },
}

/**
 * Revisão humana: o contador aceita a sugestão, corrige o cClassTrib ou marca a compra como uso e consumo.
 * GET /api/clientes/{id}/revisao · GET /api/classificacoes/opcoes · PUT /api/itens/{id}/classificacao.
 * A IA nunca decide sozinha: o que sai daqui fica com origem "Revisada" e vai para o cache da empresa.
 */
export function Revisao() {
  const { id, empresa, notas, carregando } = useEmpresa()
  const { versaoFiscal, atualizarDetalhes, marcarAlteracaoFiscal } = useDados()
  const { processar, processando } = useAtividades()
  const { mostrar } = useToast()
  const [dados, setDados] = useState<RevisaoDto | null>(null)
  const [erro, setErro] = useState<string | null>(null)
  const [tentativa, setTentativa] = useState(0)
  const [salvando, setSalvando] = useState<number | null>(null)
  const [editando, setEditando] = useState<number | null>(null)

  useEffect(() => {
    const ctrl = new AbortController()
    setErro(null)
    listarRevisao(id, ctrl.signal)
      .then(setDados)
      .catch((e) => {
        if (e instanceof DOMException && e.name === 'AbortError') return
        setErro(e instanceof Error ? e.message : 'Falha ao carregar a revisão.')
      })
    return () => ctrl.abort()
  }, [id, tentativa, versaoFiscal])

  if (!empresa) return <EstadoEmpresa carregando={carregando} />

  const salvar = async (item: ItemRevisao, corpo: RevisarItem, sucesso: string) => {
    setSalvando(item.itemId)
    try {
      const r = await revisarItem(item.itemId, corpo)
      setEditando(null)
      mostrar({
        tipo: 'sucesso',
        titulo: sucesso,
        texto: [
          r.itensAtualizados > 1 ? `${r.itensAtualizados} itens idênticos atualizados.` : '',
          r.notasRecalculadas.length ? `${r.notasRecalculadas.length} nota(s) recalculada(s).` : '',
          r.avisos[0] ?? '',
        ].filter(Boolean).join(' '),
      })
      await atualizarDetalhes(r.notasRecalculadas)
      marcarAlteracaoFiscal()
    } catch (e) {
      mostrar({ tipo: 'erro', titulo: 'Não foi possível salvar', texto: e instanceof Error ? e.message : undefined })
    } finally {
      setSalvando(null)
    }
  }

  const semClassificacao = dados?.itens.filter((i) => !i.classificacao).length ?? 0

  return (
    <>
      <CabecalhoEmpresa
        empresa={empresa}
        secao="Revisão das classificações"
        sub="Itens sem classificação, ainda não aceitos ou com confiança baixa. Aceite, corrija ou marque uso e consumo."
      />

      <Aviso style={{ marginBottom: 'var(--gap)' }}>
        A classificação automática (cache ou IA) só escolhe códigos da tabela oficial de cClassTrib, mas é uma sugestão.
        O que você aceita ou corrige aqui passa a valer para os itens idênticos desta empresa e recalcula as notas.
      </Aviso>

      {semClassificacao > 0 && (
        <Aviso tipo="warn" style={{ marginBottom: 'var(--gap)' }}>
          {fmtNumero(semClassificacao)} item(ns) ainda sem classificação.{' '}
          <button
            className="btn btn--secondary btn--sm"
            style={{ marginLeft: 6 }}
            disabled={processando > 0}
            onClick={() => void processar(notas.map((n) => n.id))}
          >
            {processando > 0 ? 'Processando...' : 'Tentar classificar automaticamente'}
          </button>
        </Aviso>
      )}

      <Card
        titulo={dados ? `${fmtNumero(dados.total)} item(ns) para revisar` : 'Itens para revisar'}
        sub={dados ? `Confiança mínima para dispensar a revisão: ${fmtConfianca(dados.confiancaMinima)}` : undefined}
        corpo="flush"
      >
        {erro ? (
          <ErroEstado mensagem={erro} onTentar={() => setTentativa((t) => t + 1)} />
        ) : !dados ? (
          <Carregando linhas={6} />
        ) : dados.itens.length === 0 ? (
          <Vazio titulo="Nada para revisar" icone={<CircleCheck size={20} />}>
            Todos os itens têm classificação aceita e confiança suficiente.
          </Vazio>
        ) : (
          <div className="table-wrap">
            <table className="table table--compact">
              <thead>
                <tr>
                  <th>Item</th>
                  <th>Nota</th>
                  <th className="right">Valor</th>
                  <th>Classificação atual</th>
                  <th>Motivo</th>
                  <th className="right">Ações</th>
                </tr>
              </thead>
              <tbody>
                {dados.itens.map((i) => (
                  <LinhaRevisao
                    key={i.itemId}
                    empresaId={id}
                    item={i}
                    salvando={salvando === i.itemId}
                    editando={editando === i.itemId}
                    onEditar={(v) => setEditando(v ? i.itemId : null)}
                    onSalvar={(corpo, msg) => void salvar(i, corpo, msg)}
                    bloqueado={salvando != null}
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

function LinhaRevisao({ empresaId, item: i, salvando, editando, bloqueado, onEditar, onSalvar }: {
  empresaId: number
  item: ItemRevisao
  salvando: boolean
  editando: boolean
  bloqueado: boolean
  onEditar: (v: boolean) => void
  onSalvar: (corpo: RevisarItem, mensagem: string) => void
}) {
  const c = i.classificacao
  return (
    <>
      <tr>
        <td>
          <div className="cell-main">{capitalizar(i.descricao)}</div>
          <div className="cell-sub mono">NCM {i.ncm ?? '—'} · item {i.nItem}</div>
        </td>
        <td>
          <Link to={rotaNota(empresaId, i.notaId)}>NF-e {i.notaNumero ?? 's/n'}</Link>{' '}
          <BadgeTipo tipo={i.tipo} sm />
          <div className="cell-sub">{fmtCompetencia(i.competencia)} · {capitalizar(i.contraparteNome)}</div>
        </td>
        <td className="right num">{fmtMoeda(i.valorTotal)}</td>
        <td>
          {c ? (
            <div style={{ display: 'flex', flexDirection: 'column', gap: 3 }}>
              <span title={[c.nomeCClassTrib, c.justificativa].filter(Boolean).join(' — ') || undefined}>
                <Badge cor={corClassificacao(c)} sm>CST {c.cst} · {c.cClassTrib}</Badge>
              </span>
              <span className="cell-sub" title={c.justificativa ?? undefined}>
                {c.descricaoRegime ?? REGIME_TRIB_LABEL[c.regime]} · {ORIGEM_LABEL[c.origem].rotulo} · {fmtConfianca(c.confianca)}
              </span>
            </div>
          ) : (
            <Badge cor="gray" sm>Sem classificação</Badge>
          )}
        </td>
        <td>
          <div style={{ display: 'flex', gap: 4, flexWrap: 'wrap' }}>
            {i.motivos.map((m) => <Badge key={m} cor={MOTIVO[m].cor} sm>{MOTIVO[m].rotulo}</Badge>)}
          </div>
        </td>
        <td className="right">
          <div className="acoes-linha">
            {salvando ? (
              <LoaderCircle size={17} className="spin" />
            ) : (
              <>
                {c && (
                  <button
                    className="btn btn--primary btn--sm"
                    disabled={bloqueado}
                    onClick={() => onSalvar({ aceitar: true }, 'Classificação aceita')}
                    title="Confirma a classificação sugerida"
                  >
                    <Check size={14} /> Aceitar
                  </button>
                )}
                <button
                  className="btn btn--secondary btn--sm"
                  disabled={bloqueado}
                  onClick={() => onEditar(!editando)}
                >
                  {editando ? <X size={14} /> : <Pencil size={14} />} {editando ? 'Fechar' : 'Corrigir'}
                </button>
                {i.tipo === 'ENTRADA' && i.creditavel && (
                  <button
                    className="btn btn--ghost btn--sm"
                    disabled={bloqueado}
                    onClick={() => onSalvar({ creditavel: false }, 'Marcado como uso e consumo')}
                    title="Compra para uso e consumo: não gera crédito"
                  >
                    Uso e consumo
                  </button>
                )}
              </>
            )}
          </div>
        </td>
      </tr>
      {editando && (
        <tr>
          <td colSpan={6} style={{ background: 'var(--surface-2)' }}>
            <Correcao item={i} bloqueado={bloqueado} onSalvar={onSalvar} />
          </td>
        </tr>
      )}
    </>
  )
}

/** Escolha do cClassTrib: primeiro as opções que a lista oficial associa ao NCM, depois a tabela completa. */
function Correcao({ item, bloqueado, onSalvar }: {
  item: ItemRevisao
  bloqueado: boolean
  onSalvar: (corpo: RevisarItem, mensagem: string) => void
}) {
  const [opcoes, setOpcoes] = useState<OpcaoClassificacao[]>(item.opcoesSugeridas)
  const [codigo, setCodigo] = useState(item.classificacao?.cClassTrib ?? item.opcoesSugeridas[0]?.cClassTrib ?? '')
  const [justificativa, setJustificativa] = useState('')
  const [erro, setErro] = useState<string | null>(null)

  useEffect(() => {
    const ctrl = new AbortController()
    opcoesClassificacao(item.ncm, ctrl.signal)
      .then((lista) => {
        setOpcoes(lista)
        setCodigo((atual) => atual || lista[0]?.cClassTrib || '')
      })
      .catch((e) => {
        if (e instanceof DOMException && e.name === 'AbortError') return
        setErro(e instanceof Error ? e.message : 'Falha ao carregar as opções.')
      })
    return () => ctrl.abort()
  }, [item.ncm])

  const escolhida = opcoes.find((o) => o.cClassTrib === codigo)
  const sugeridas = opcoes.filter((o) => o.sugeridaPeloNcm)
  const demais = opcoes.filter((o) => !o.sugeridaPeloNcm)
  const rotulo = (o: OpcaoClassificacao) =>
    `${o.cClassTrib} · CST ${o.cst} · ${o.descricaoRegime ?? REGIME_TRIB_LABEL[o.regime]} — ${o.nome}`

  return (
    <div style={{ display: 'grid', gap: 10, padding: '6px 0' }}>
      {erro && <Aviso tipo="error">{erro}</Aviso>}
      <div className="field" style={{ margin: 0 }}>
        <label className="field__label" htmlFor={`cc-${item.itemId}`}>Novo cClassTrib (tabela oficial)</label>
        <select
          id={`cc-${item.itemId}`}
          className="select"
          value={codigo}
          onChange={(e) => setCodigo(e.target.value)}
          disabled={bloqueado}
        >
          {sugeridas.length > 0 && (
            <optgroup label={`Associados ao NCM ${item.ncm ?? ''} pela lista oficial`}>
              {sugeridas.map((o) => <option key={o.cClassTrib} value={o.cClassTrib}>{rotulo(o)}</option>)}
            </optgroup>
          )}
          <optgroup label="Demais códigos">
            {demais.map((o) => <option key={o.cClassTrib} value={o.cClassTrib}>{rotulo(o)}</option>)}
          </optgroup>
        </select>
        {escolhida?.exigeNcmNaLista && !escolhida.sugeridaPeloNcm && (
          <span className="cell-sub" style={{ color: 'var(--badge-amber-fg)' }}>
            Este benefício só vale para NCMs da lista oficial, e o NCM deste item não está nela.
          </span>
        )}
      </div>
      <div className="field" style={{ margin: 0 }}>
        <label className="field__label" htmlFor={`jt-${item.itemId}`}>Justificativa (opcional)</label>
        <input
          id={`jt-${item.itemId}`}
          className="input"
          value={justificativa}
          maxLength={500}
          placeholder="Ex.: produto da cesta básica, Anexo I da LC 214/2025"
          onChange={(e) => setJustificativa(e.target.value)}
          disabled={bloqueado}
        />
      </div>
      <div>
        <button
          className="btn btn--primary btn--sm"
          disabled={bloqueado || !codigo}
          onClick={() =>
            onSalvar(
              { cClassTrib: codigo, cst: escolhida?.cst, justificativa: justificativa.trim() || undefined },
              `Classificação corrigida para ${codigo}`,
            )
          }
        >
          <Check size={14} /> Salvar correção
        </button>
      </div>
    </div>
  )
}
