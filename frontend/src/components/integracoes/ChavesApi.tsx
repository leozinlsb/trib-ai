import { useEffect, useMemo, useState, type FormEvent } from 'react'
import { Ban, Check, Copy, KeyRound, LoaderCircle, Plus } from 'lucide-react'
import { ApiError } from '../../api/client'
import { emitirChave, listarChaves, revogarChave } from '../../api/chavesApi'
import type { Cliente } from '../../api/types'
import {
  COR_SITUACAO, ESCOPOS, FORM_CHAVE_VAZIO, ROTAS_PUBLICAS, ROTULO_SITUACAO, exemploDeUso, resumoEscopos,
  validarFormChave, type ChaveCriada, type ChaveResumo, type EscopoApi, type FormChave,
} from '../../lib/chavesApi'
import { fmtDataHora, fmtNumero } from '../../lib/format'
import { useDados, useToast } from '../../state/contexts'
import { Aviso, Badge, Card, Carregando, Confirmacao, ErroEstado, Modal, Vazio } from '../ui'

/**
 * Integrações (API pública): o administrador emite, acompanha e revoga as chaves que sistemas externos (ERPs) usam.
 * A chave completa aparece uma única vez, na emissão, e o servidor nunca a devolve de novo.
 */
export function ChavesApi() {
  const { clientes } = useDados()
  const { mostrar } = useToast()
  const [chaves, setChaves] = useState<ChaveResumo[] | null>(null)
  const [erro, setErro] = useState<string | null>(null)
  const [tentativa, setTentativa] = useState(0)
  const [emitindo, setEmitindo] = useState(false)
  const [criada, setCriada] = useState<ChaveCriada | null>(null)
  const [revogando, setRevogando] = useState<ChaveResumo | null>(null)

  useEffect(() => {
    const ctrl = new AbortController()
    setErro(null)
    listarChaves(ctrl.signal)
      .then(setChaves)
      .catch((e) => {
        if (e instanceof DOMException && e.name === 'AbortError') return
        setErro(e instanceof Error ? e.message : 'Falha ao carregar as chaves.')
      })
    return () => ctrl.abort()
  }, [tentativa])

  const ativas = chaves?.filter((c) => c.situacao === 'ATIVA').length ?? 0

  return (
    <>
      <Card
        titulo="Integrações (API pública)"
        sub="Chaves que permitem a ERPs e outros sistemas enviar notas, classificar produtos e simular o cálculo de 2027. Cada chave vale para uma única empresa."
        acoes={
          <button className="btn btn--secondary btn--sm" onClick={() => setEmitindo(true)}>
            <Plus size={15} /> Nova chave
          </button>
        }
      >
        {erro ? (
          <ErroEstado mensagem={erro} onTentar={() => setTentativa((t) => t + 1)} />
        ) : !chaves ? (
          <Carregando linhas={3} />
        ) : chaves.length === 0 ? (
          <Vazio titulo="Nenhuma chave emitida" icone={<KeyRound size={20} />}>
            Emita uma chave para a empresa que vai integrar o próprio sistema ao TribIA.
          </Vazio>
        ) : (
          <>
            <p className="cell-sub" style={{ marginBottom: 8 }}>
              {ativas} ativa(s) de {chaves.length}. Use "Revogar" assim que uma chave deixar de ser necessária ou for exposta.
            </p>
            <div className="table-wrap">
              <table className="table table--compact">
                <thead>
                  <tr>
                    <th>Integrador</th>
                    <th>Empresa</th>
                    <th>Chave</th>
                    <th>Situação</th>
                    <th>Uso hoje</th>
                    <th>Último uso</th>
                    <th aria-label="Ações" />
                  </tr>
                </thead>
                <tbody>
                  {chaves.map((c) => (
                    <tr key={c.id}>
                      <td>
                        <div className="cell-main">{c.nomeIntegrador}</div>
                        <div className="cell-sub" title={c.escopos.join(', ')}>{resumoEscopos(c.escopos)}</div>
                      </td>
                      <td>{c.empresa}</td>
                      <td className="num nowrap" title="Só o prefixo é guardado em texto: o segredo não pode ser recuperado">
                        tribia_{c.prefixo}…
                      </td>
                      <td>
                        <Badge cor={COR_SITUACAO[c.situacao]} sm>{ROTULO_SITUACAO[c.situacao]}</Badge>
                        <div className="cell-sub">
                          {c.situacao === 'REVOGADA' && c.revogadaEm
                            ? `em ${fmtDataHora(c.revogadaEm)}`
                            : c.expiraEm ? `vale até ${fmtDataHora(c.expiraEm)}` : 'sem validade'}
                        </div>
                      </td>
                      <td className="num">
                        <div>{fmtNumero(c.itensIaHoje)} itens p/ IA</div>
                        <div className="cell-sub">{fmtNumero(c.analisesHoje)} análises de NCM</div>
                      </td>
                      <td className="nowrap">{c.ultimoUsoEm ? fmtDataHora(c.ultimoUsoEm) : <span className="muted">nunca usada</span>}</td>
                      <td className="right">
                        {c.situacao !== 'REVOGADA' && (
                          <button className="btn btn--secondary btn--sm" onClick={() => setRevogando(c)}>
                            <Ban size={14} /> Revogar
                          </button>
                        )}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </>
        )}
        <GuiaDeUso />
      </Card>

      {emitindo && (
        <NovaChave
          empresas={clientes.filter((c) => c.ativo)}
          onFechar={() => setEmitindo(false)}
          onCriada={(c) => {
            setEmitindo(false)
            setCriada(c)
            setChaves((l) => [c.dados, ...(l ?? [])])
          }}
        />
      )}

      {criada && <ChaveEmitida criada={criada} onFechar={() => setCriada(null)} />}

      {revogando && (
        <Confirmacao
          titulo={`Revogar a chave de ${revogando.nomeIntegrador}?`}
          rotulo="Revogar chave"
          onFechar={() => setRevogando(null)}
          onConfirmar={async () => {
            const nova = await revogarChave(revogando.id)
            setChaves((l) => (l ?? []).map((c) => (c.id === nova.id ? nova : c)))
            mostrar({ tipo: 'sucesso', titulo: 'Chave revogada', texto: `${nova.nomeIntegrador} não consegue mais usar a API.` })
            setRevogando(null)
          }}
        >
          <p>
            O sistema <strong>{revogando.nomeIntegrador}</strong> ({revogando.empresa}) perde o acesso na hora e a chave
            não pode ser reativada. Para voltar a integrar, emita uma chave nova. As notas e análises já enviadas
            continuam na plataforma.
          </p>
        </Confirmacao>
      )}
    </>
  )
}

/** Guia curto para quem vai integrar. O Swagger fica desligado em produção, então a referência mínima mora aqui. */
function GuiaDeUso() {
  const [aberto, setAberto] = useState(false)
  const exemplo = useMemo(() => exemploDeUso(window.location.origin), [])
  return (
    <details className="guia-api" open={aberto} onToggle={(e) => setAberto((e.target as HTMLDetailsElement).open)} style={{ marginTop: 16 }}>
      <summary style={{ cursor: 'pointer', fontWeight: 600 }}>Como o integrador usa a chave</summary>
      <div style={{ marginTop: 10 }}>
        <p className="cell-sub">
          A chave vai no cabeçalho <code>X-API-Key</code> de toda chamada. A empresa é sempre a da chave: o corpo da
          chamada não escolhe empresa. Notas e classificações respondem 202 e são consultadas até ficarem finalizadas.
        </p>
        <div className="table-wrap" style={{ margin: '10px 0' }}>
          <table className="table table--compact">
            <thead><tr><th>Chamada</th><th>Permissão</th><th>O que faz</th></tr></thead>
            <tbody>
              {ROTAS_PUBLICAS.map((r) => (
                <tr key={r.metodo + r.rota}>
                  <td className="num nowrap"><strong>{r.metodo}</strong> {r.rota}</td>
                  <td>{r.escopo === 'QUALQUER_LEITURA' ? 'qualquer permissão de leitura' : ESCOPOS.find((e) => e.id === r.escopo)?.rotulo}</td>
                  <td>{r.descricao}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        <pre className="bloco-codigo" tabIndex={0}>{exemplo}</pre>
        <Aviso tipo="warn">
          Resultados da API são sugestões de classificação e projeções de 2027 pendentes de validação fiscal: o integrador
          deve apresentá-los assim aos seus usuários. Cada resposta traz os avisos correspondentes.
        </Aviso>
      </div>
    </details>
  )
}

function NovaChave({ empresas, onCriada, onFechar }: {
  empresas: Cliente[]
  onCriada: (c: ChaveCriada) => void
  onFechar: () => void
}) {
  const [form, setForm] = useState<FormChave>({ ...FORM_CHAVE_VAZIO, escopos: [...FORM_CHAVE_VAZIO.escopos] })
  const [tentou, setTentou] = useState(false)
  const [salvando, setSalvando] = useState(false)
  const [erro, setErro] = useState<string | null>(null)
  const erros = validarFormChave(form)
  const mudar = (campo: keyof FormChave, valor: string) => setForm((f) => ({ ...f, [campo]: valor }))
  const alternar = (id: EscopoApi) =>
    setForm((f) => ({ ...f, escopos: f.escopos.includes(id) ? f.escopos.filter((e) => e !== id) : [...f.escopos, id] }))

  const enviar = async (e: FormEvent) => {
    e.preventDefault()
    setTentou(true)
    if (Object.keys(erros).length > 0) return
    setSalvando(true)
    setErro(null)
    try {
      onCriada(await emitirChave(form))
    } catch (err) {
      setErro(err instanceof ApiError ? err.message : 'Não foi possível emitir a chave.')
      setSalvando(false)
    }
  }

  const campoNumero = (campo: keyof FormChave, rotulo: string, dica: string) => (
    <div className="field">
      <label className="field__label" htmlFor={`nc-${campo}`}>{rotulo}</label>
      <input id={`nc-${campo}`} inputMode="numeric" className={`input${tentou && erros[campo] ? ' input--erro' : ''}`}
        value={form[campo] as string} onChange={(e) => mudar(campo, e.target.value)} disabled={salvando} placeholder="padrão" />
      {tentou && erros[campo] ? <span className="field__erro">{erros[campo]}</span> : <span className="cell-sub">{dica}</span>}
    </div>
  )

  return (
    <Modal titulo="Nova chave de API" sub="A chave completa será mostrada uma única vez, logo depois de emitida." onFechar={onFechar}
      travado={salvando} largura={680}
      rodape={
        <>
          <button className="btn btn--secondary" onClick={onFechar} disabled={salvando}>Cancelar</button>
          <button className="btn btn--primary" type="submit" form="form-nova-chave" disabled={salvando}>
            {salvando && <LoaderCircle size={15} className="spin" />} Emitir chave
          </button>
        </>
      }>
      <form id="form-nova-chave" onSubmit={enviar} noValidate>
        {erro && <Aviso tipo="error">{erro}</Aviso>}
        <div className="form-grid">
          <div className="field">
            <label className="field__label" htmlFor="nc-empresa">Empresa</label>
            <select id="nc-empresa" className={`input${tentou && erros.clienteId ? ' input--erro' : ''}`} value={form.clienteId}
              onChange={(e) => mudar('clienteId', e.target.value)} disabled={salvando}>
              <option value="">Escolha…</option>
              {empresas.map((c) => <option key={c.id} value={c.id}>{c.nomeFantasia ?? c.razaoSocial}</option>)}
            </select>
            {tentou && erros.clienteId ? <span className="field__erro">{erros.clienteId}</span>
              : <span className="cell-sub">A chave só enxerga os dados desta empresa.</span>}
          </div>
          <div className="field">
            <label className="field__label" htmlFor="nc-nome">Nome do integrador</label>
            <input id="nc-nome" className={`input${tentou && erros.nomeIntegrador ? ' input--erro' : ''}`} value={form.nomeIntegrador}
              onChange={(e) => mudar('nomeIntegrador', e.target.value)} disabled={salvando} maxLength={100}
              placeholder="Ex.: ERP da Distribuidora" />
            {tentou && erros.nomeIntegrador ? <span className="field__erro">{erros.nomeIntegrador}</span>
              : <span className="cell-sub">Aparece na lista e nos registros de uso.</span>}
          </div>
          <fieldset className="field span-2" style={{ border: 0, padding: 0 }}>
            <legend className="field__label">O que a chave pode fazer</legend>
            {ESCOPOS.map((e) => (
              <label key={e.id} style={{ display: 'flex', gap: 8, alignItems: 'flex-start', margin: '6px 0' }}>
                <input type="checkbox" checked={form.escopos.includes(e.id)} onChange={() => alternar(e.id)} disabled={salvando} />
                <span>
                  <strong>{e.rotulo}</strong>{e.usaIa && <> <Badge cor="blue" sm title="Gasta a cota diária de itens para a IA">usa IA</Badge></>}
                  <span className="cell-sub" style={{ display: 'block' }}>{e.descricao}</span>
                </span>
              </label>
            ))}
            {tentou && erros.escopos && <span className="field__erro">{erros.escopos}</span>}
            <span className="cell-sub">Dê só o necessário: uma chave que apenas consulta não deve poder enviar nada.</span>
          </fieldset>
        </div>
        <details style={{ marginTop: 12 }}>
          <summary style={{ cursor: 'pointer', fontWeight: 600 }}>Validade e limites (opcional)</summary>
          <div className="form-grid" style={{ marginTop: 10 }}>
            {campoNumero('validadeDias', 'Validade (dias)', 'Vazio: 365 dias.')}
            {campoNumero('requisicoesPorMinuto', 'Requisições por minuto', 'Vazio: 60.')}
            {campoNumero('cotaDiariaItensIa', 'Itens para a IA por dia', 'Vazio: 500. Notas e classificações gastam desta cota.')}
            {campoNumero('cotaDiariaAnalises', 'Análises de NCM por dia', 'Vazio: 100.')}
            {campoNumero('maxAnalisesSimultaneas', 'Processamentos simultâneos', 'Vazio: 5.')}
          </div>
        </details>
      </form>
    </Modal>
  )
}

/** A chave completa, uma única vez. Só fecha depois de a pessoa confirmar que a guardou. */
function ChaveEmitida({ criada, onFechar }: { criada: ChaveCriada; onFechar: () => void }) {
  const [copiada, setCopiada] = useState(false)
  const [guardou, setGuardou] = useState(false)

  const copiar = async () => {
    try {
      await navigator.clipboard.writeText(criada.chave)
    } catch {
      // sem permissão da área de transferência: seleciona o texto para copiar à mão
      const alvo = document.getElementById('chave-emitida')
      if (alvo) {
        const faixa = document.createRange()
        faixa.selectNodeContents(alvo)
        const sel = window.getSelection()
        sel?.removeAllRanges()
        sel?.addRange(faixa)
      }
      return
    }
    setCopiada(true)
  }

  return (
    <Modal titulo="Chave emitida" sub={`${criada.dados.nomeIntegrador} · ${criada.dados.empresa}`} onFechar={onFechar}
      travado={!guardou} largura={640}
      rodape={<button className="btn btn--primary" disabled={!guardou} onClick={onFechar}>Concluir</button>}>
      <Aviso tipo="warn">
        <strong>Copie a chave agora.</strong> Ela não será mostrada de novo e o TribIA não consegue recuperá-la. Se
        perder, revogue e emita outra. Entregue-a ao integrador por um canal seguro; não a coloque em código, Git ou chat.
      </Aviso>
      <div className="chave-emitida" style={{ display: 'flex', gap: 8, alignItems: 'center', margin: '14px 0' }}>
        <code id="chave-emitida" className="num" style={{ flex: 1, wordBreak: 'break-all', userSelect: 'all' }}>{criada.chave}</code>
        <button className="btn btn--secondary btn--sm" onClick={copiar}>
          {copiada ? <Check size={14} /> : <Copy size={14} />} {copiada ? 'Copiada' : 'Copiar'}
        </button>
      </div>
      <label style={{ display: 'flex', gap: 8, alignItems: 'center' }}>
        <input type="checkbox" checked={guardou} onChange={(e) => setGuardou(e.target.checked)} />
        Já guardei a chave em um lugar seguro
      </label>
    </Modal>
  )
}
