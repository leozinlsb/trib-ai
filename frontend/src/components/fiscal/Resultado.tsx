import type { ReactNode } from 'react'
import { BookOpen, CircleAlert, CircleCheck, ExternalLink, Info, ListChecks, Scale, Sparkles, TriangleAlert } from 'lucide-react'
import type { AnaliseDetalhe } from '../../api/inteligenciaFiscal'
import { fmtNcm, VALIDACAO, VERIFICACAO } from '../../lib/fiscal'
import { fmtData, fmtDataHora } from '../../lib/format'
import { Badge, Card } from '../ui'
import { BadgeValidacao } from './Comum'

/** A — Resultado principal. Sugestão automatizada: nunca apresentada como classificação oficial. */
export function CardResultado({ analise }: { analise: AnaliseDetalhe }) {
  const r = analise.resultado
  if (!r) return null
  return (
    <section className="card resultado-ncm" aria-labelledby="ncm-sugerida">
      <div className="resultado-ncm__corpo">
        <span className="eyebrow" id="ncm-sugerida">NCM sugerida</span>
        <div className="resultado-ncm__codigo">{fmtNcm(r.ncm)}</div>
        <p className="resultado-ncm__descricao">{r.descricaoOficial}</p>
        <div className="resultado-ncm__meta">
          <BadgeValidacao situacao={r.situacaoValidacao} />
          <span>Analisada em {fmtDataHora(r.analisadaEm)}</span>
          {analise.entrada.ncmAtual && (
            <span>
              NCM usada hoje: <strong className="num">{fmtNcm(analise.entrada.ncmAtual)}</strong>
              {analise.entrada.ncmAtual.replace(/\D/g, '') === r.ncm.replace(/\D/g, '') ? ' (igual à sugerida)' : ' (diferente da sugerida)'}
            </span>
          )}
        </div>
      </div>
      <p className="resultado-ncm__aviso">
        <Info size={14} /> Sugestão gerada automaticamente. Antes de usar em documentos fiscais, confirme a classificação
        com um especialista.
      </p>
    </section>
  )
}

function Lista({ titulo, itens, icone, tom }: { titulo: string; itens?: string[]; icone: ReactNode; tom?: 'alerta' }) {
  if (!itens || itens.length === 0) return null
  return (
    <div className={`fundamento${tom ? ` fundamento--${tom}` : ''}`}>
      <h3>{icone} {titulo}</h3>
      <ul>
        {itens.map((t, i) => <li key={i}>{t}</li>)}
      </ul>
    </div>
  )
}

/** B — Análise e Fundamentação (conteúdo interpretativo gerado pela análise automática). */
export function Fundamentacao({ analise }: { analise: AnaliseDetalhe }) {
  const f = analise.fundamentacao
  return (
    <Card titulo={<>Análise e Fundamentação <span className="fonte fonte--calc">Interpretação da análise automática</span></>}>
      {!f ? (
        <p className="texto-vazio">A fundamentação não foi informada para esta análise.</p>
      ) : (
        <div className="fundamentos">
          <Lista titulo="Características identificadas" itens={f.caracteristicas} icone={<ListChecks size={16} />} />
          <Lista titulo="Motivos da sugestão" itens={f.motivos} icone={<Sparkles size={16} />} />
          <Lista titulo="Regras fiscais consideradas" itens={f.regrasConsideradas} icone={<Scale size={16} />} />
          <Lista titulo="Observações" itens={f.observacoes} icone={<Info size={16} />} />
          <Lista titulo="Limitações da análise" itens={f.limitacoes} icone={<TriangleAlert size={16} />} tom="alerta" />
        </div>
      )}
    </Card>
  )
}

/** C — Classificações alternativas. A pontuação é de compatibilidade, nunca exibida como % de acerto. */
export function Alternativas({ analise }: { analise: AnaliseDetalhe }) {
  const lista = analise.alternativas ?? []
  const significado = lista.find((a) => a.pontuacao)?.pontuacao
  return (
    <Card titulo="Classificações avaliadas" sub="Outros códigos considerados durante a análise." corpo="flush">
      {lista.length === 0 ? (
        <p className="texto-vazio" style={{ padding: '4px 18px 18px' }}>Nenhuma classificação alternativa foi informada.</p>
      ) : (
        <>
          <div className="table-wrap">
            <table className="table table--compact">
              <thead>
                <tr>
                  <th>Código NCM</th>
                  <th>Descrição</th>
                  <th>Avaliação</th>
                  {significado && <th className="right">Compatibilidade</th>}
                </tr>
              </thead>
              <tbody>
                {lista.map((a) => (
                  <tr key={a.ncm}>
                    <td className="num nowrap"><strong>{fmtNcm(a.ncm)}</strong></td>
                    <td>{a.descricao}</td>
                    <td>{a.avaliacao}</td>
                    {significado && (
                      <td className="right num" title={a.pontuacao?.significado}>
                        {a.pontuacao ? `${a.pontuacao.valor.toLocaleString('pt-BR')} (escala ${a.pontuacao.escala})` : '—'}
                      </td>
                    )}
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          {significado && (
            <p className="cell-sub" style={{ padding: '10px 18px 16px' }}>
              <strong>Compatibilidade:</strong> {significado.significado} Não indica a probabilidade de a classificação estar correta.
            </p>
          )}
        </>
      )}
    </Card>
  )
}

/** D — Validação fiscal (verificações automáticas; não equivale a aprovação da Receita Federal). */
export function Validacao({ analise }: { analise: AnaliseDetalhe }) {
  const v = analise.validacao
  return (
    <Card titulo="Validação fiscal" acoes={v ? <BadgeValidacao situacao={v.situacao} /> : undefined}>
      {!v ? (
        <p className="texto-vazio">As verificações fiscais ainda não foram informadas.</p>
      ) : (
        <div className="stack" style={{ gap: 16 }}>
          <p className="cell-sub">{VALIDACAO[v.situacao].descricao}</p>
          <dl className="dl" style={{ gridTemplateColumns: '1fr 1fr' }}>
            <div><dt>Situação do código</dt><dd>{v.situacaoNcm}</dd></div>
            <div>
              <dt>Vigência</dt>
              <dd>
                {v.vigencia?.inicio ? `desde ${fmtData(v.vigencia.inicio)}` : '—'}
                {v.vigencia?.fim ? ` até ${fmtData(v.vigencia.fim)}` : v.vigencia?.inicio ? ' (em vigor)' : ''}
              </dd>
            </div>
          </dl>
          {v.verificacoes.length > 0 && (
            <div>
              <div className="field__label" style={{ marginBottom: 6 }}>Verificações realizadas</div>
              <ul className="verificacoes">
                {v.verificacoes.map((c, i) => (
                  <li key={i}>
                    <Badge cor={VERIFICACAO[c.resultado].cor} sm>{VERIFICACAO[c.resultado].rotulo}</Badge>
                    <div>
                      <div className="cell-main">{c.nome}</div>
                      {c.detalhe && <div className="cell-sub">{c.detalhe}</div>}
                    </div>
                  </li>
                ))}
              </ul>
            </div>
          )}
          <ListaSimples titulo="Regras aplicáveis" itens={v.regrasAplicaveis} />
          <ListaSimples titulo="Divergências encontradas" itens={v.divergencias} icone={<CircleAlert size={15} color="var(--danger)" />} />
          <ListaSimples titulo="Informações pendentes" itens={v.pendencias} icone={<TriangleAlert size={15} color="var(--warning)" />} />
          <p className="cell-sub" style={{ borderTop: '1px solid var(--border-soft)', paddingTop: 12 }}>
            Verificações automáticas sobre os dados disponíveis. Não representam aprovação ou homologação pela Receita Federal.
          </p>
        </div>
      )}
    </Card>
  )
}

function ListaSimples({ titulo, itens, icone }: { titulo: string; itens: string[]; icone?: ReactNode }) {
  if (!itens || itens.length === 0) return null
  return (
    <div>
      <div className="field__label" style={{ marginBottom: 6 }}>{titulo}</div>
      <ul className="lista-icone">
        {itens.map((t, i) => (
          <li key={i}>{icone ?? <CircleCheck size={15} color="var(--muted)" />} <span>{t}</span></li>
        ))}
      </ul>
    </div>
  )
}

/** E — Fontes e referências, exatamente como informadas pela análise. */
export function Fontes({ analise }: { analise: AnaliseDetalhe }) {
  const fontes = analise.fontes ?? []
  return (
    <Card titulo="Fontes e referências" sub="Documentos e normas usados na análise.">
      {fontes.length === 0 ? (
        <p className="texto-vazio">Nenhuma fonte foi informada para esta análise.</p>
      ) : (
        <ul className="fontes">
          {fontes.map((f, i) => (
            <li key={i}>
              <BookOpen size={17} />
              <div style={{ minWidth: 0, flex: 1 }}>
                <div className="cell-main">{f.titulo}</div>
                {(f.identificacao || f.versao) && (
                  <div className="cell-sub">{[f.identificacao, f.versao].filter(Boolean).join(' · ')}</div>
                )}
                {f.trecho && <blockquote>{f.trecho}</blockquote>}
                {f.url && (
                  <a href={f.url} target="_blank" rel="noopener noreferrer" className="fontes__link">
                    Abrir fonte <ExternalLink size={12} />
                  </a>
                )}
              </div>
            </li>
          ))}
        </ul>
      )}
    </Card>
  )
}
