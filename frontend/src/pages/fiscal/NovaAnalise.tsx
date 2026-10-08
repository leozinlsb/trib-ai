import { useRef, useState } from 'react'
import { Link, useLocation, useNavigate } from 'react-router-dom'
import { ArrowLeft, ArrowRight, Check, ChevronRight, FileText, LoaderCircle, Trash2, UploadCloud } from 'lucide-react'
import { iniciarAnalise, servicoIndisponivel, type MercadoriaEntrada } from '../../api/inteligenciaFiscal'
import { CabecalhoEmpresa, EstadoEmpresa } from '../../components/empresas/CabecalhoEmpresa'
import { ServicoIndisponivel } from '../../components/fiscal/Comum'
import { Aviso, Card } from '../../components/ui'
import { useEmpresa } from '../../hooks/useEmpresa'
import { fmtNcm } from '../../lib/fiscal'
import { fmtBytes } from '../../lib/format'
import { rotaAnaliseFiscal } from '../../lib/rotas'
import { useToast } from '../../state/contexts'

const PASSOS = ['Mercadoria', 'Documentos', 'Confirmar'] as const
const FORMATOS = ['pdf', 'png', 'jpg', 'jpeg', 'webp', 'doc', 'docx', 'xls', 'xlsx', 'txt']
const MAX_ARQUIVO = 10 * 1024 * 1024
const MAX_ARQUIVOS = 10

const VAZIO: Required<MercadoriaEntrada> = {
  nome: '', descricao: '', composicao: '', finalidade: '', caracteristicas: '', ncmAtual: '',
}

function mascaraNcm(v: string) {
  const d = v.replace(/\D/g, '').slice(0, 8)
  return d.replace(/^(\d{4})(\d)/, '$1.$2').replace(/^(\d{4})\.(\d{2})(\d)/, '$1.$2.$3')
}

const extensao = (f: File) => f.name.split('.').pop()?.toLowerCase() ?? ''

/** Formulário em etapas. Só coleta e envia; a análise acontece no servidor. */
export function NovaAnalise() {
  const { id, empresa, carregando } = useEmpresa()
  const navigate = useNavigate()
  const { mostrar } = useToast()
  const location = useLocation()
  // "Nova análise com mais informações": reaproveita os dados de uma análise anterior
  const inicial = (location.state as { entrada?: MercadoriaEntrada } | null)?.entrada

  const [passo, setPasso] = useState(0)
  const [form, setForm] = useState<Required<MercadoriaEntrada>>({
    ...VAZIO,
    ...Object.fromEntries(Object.entries(inicial ?? {}).filter(([, v]) => v != null)),
    ncmAtual: mascaraNcm(inicial?.ncmAtual ?? ''),
  })
  const [tentou, setTentou] = useState(false)
  const [arquivos, setArquivos] = useState<File[]>([])
  const [sobre, setSobre] = useState(false)
  const [enviando, setEnviando] = useState(false)
  const [erro, setErro] = useState<string | null>(null)
  const [indisponivel, setIndisponivel] = useState(false)
  const input = useRef<HTMLInputElement>(null)

  if (!empresa) return <EstadoEmpresa carregando={carregando} />

  const erros = {
    nome: !form.nome.trim() ? 'Informe o nome da mercadoria.' : null,
    descricao: form.descricao.trim().length < 20 ? 'Descreva a mercadoria com pelo menos 20 caracteres.' : null,
    ncmAtual: form.ncmAtual && form.ncmAtual.replace(/\D/g, '').length !== 8 ? 'A NCM tem 8 dígitos.' : null,
  }
  const passoValido = !erros.nome && !erros.descricao && !erros.ncmAtual
  const set = (k: keyof MercadoriaEntrada, v: string) => setForm((f) => ({ ...f, [k]: v }))

  const adicionar = (lista: FileList | null) => {
    if (!lista) return
    const novos = [...lista]
    setArquivos((atual) => {
      const nomes = new Set(atual.map((f) => f.name + f.size))
      return [...atual, ...novos.filter((f) => !nomes.has(f.name + f.size))].slice(0, MAX_ARQUIVOS)
    })
  }
  const invalidos = arquivos.filter((f) => !FORMATOS.includes(extensao(f)) || f.size > MAX_ARQUIVO)

  const avancar = () => {
    if (passo === 0) {
      setTentou(true)
      if (!passoValido) return
    }
    if (passo === 1 && invalidos.length) return
    setPasso((p) => Math.min(p + 1, 2))
  }

  const iniciar = async () => {
    setEnviando(true)
    setErro(null)
    setIndisponivel(false)
    const dados: MercadoriaEntrada = {
      nome: form.nome.trim(),
      descricao: form.descricao.trim(),
      ...(form.composicao.trim() && { composicao: form.composicao.trim() }),
      ...(form.finalidade.trim() && { finalidade: form.finalidade.trim() }),
      ...(form.caracteristicas.trim() && { caracteristicas: form.caracteristicas.trim() }),
      ...(form.ncmAtual && { ncmAtual: form.ncmAtual.replace(/\D/g, '') }),
    }
    try {
      const criada = await iniciarAnalise(id, dados, arquivos)
      mostrar({ tipo: 'sucesso', titulo: 'Análise iniciada', texto: 'Acompanhe o processamento nesta tela.' })
      navigate(rotaAnaliseFiscal(id, criada.id), { replace: true })
    } catch (e) {
      if (servicoIndisponivel(e)) setIndisponivel(true)
      else setErro(e instanceof Error ? e.message : 'Não foi possível iniciar a análise.')
      setEnviando(false)
    }
  }

  const campo = (k: keyof MercadoriaEntrada, rotulo: string, ajuda: string, opcoes: { area?: boolean; obrigatorio?: boolean; placeholder?: string } = {}) => {
    const erroCampo = tentou ? erros[k as keyof typeof erros] : null
    const props = {
      id: `na-${k}`,
      className: `input${opcoes.area ? ' textarea' : ''}${erroCampo ? ' input--erro' : ''}`,
      value: form[k],
      placeholder: opcoes.placeholder,
      'aria-invalid': !!erroCampo,
      'aria-describedby': `na-${k}-ajuda`,
    }
    return (
      <div className="field">
        <label className="field__label" htmlFor={`na-${k}`}>
          {rotulo} {opcoes.obrigatorio ? '*' : <span className="muted">(opcional)</span>}
        </label>
        {opcoes.area ? (
          <textarea {...props} rows={k === 'descricao' ? 4 : 2} onChange={(e) => set(k, e.target.value)} />
        ) : (
          <input
            {...props}
            onChange={(e) => set(k, k === 'ncmAtual' ? mascaraNcm(e.target.value) : e.target.value)}
            inputMode={k === 'ncmAtual' ? 'numeric' : undefined}
          />
        )}
        {erroCampo ? <span className="field__erro">{erroCampo}</span> : <span className="cell-sub" id={`na-${k}-ajuda`}>{ajuda}</span>}
      </div>
    )
  }

  return (
    <>
      <nav className="breadcrumb" aria-label="Trilha">
        <Link to={rotaAnaliseFiscal(id)}>Inteligência Fiscal</Link>
        <ChevronRight size={14} />
        <span>Nova análise</span>
      </nav>
      <CabecalhoEmpresa empresa={empresa} secao="Nova análise" sub="Descreva a mercadoria. Quanto mais detalhes, melhor a sugestão de classificação." />

      <ol className="passos" aria-label="Etapas">
        {PASSOS.map((p, i) => (
          <li key={p} className={i < passo ? 'feito' : i === passo ? 'atual' : ''} aria-current={i === passo ? 'step' : undefined}>
            <span className="passos__num">{i < passo ? <Check size={14} strokeWidth={3} /> : i + 1}</span>
            {p}
          </li>
        ))}
      </ol>

      <Card className="nova-analise">
        {passo === 0 && (
          <div className="stack" style={{ gap: 16 }}>
            <div className="form-grid">
              <div className="span-2">{campo('nome', 'Nome da mercadoria', 'Como a mercadoria é chamada pela empresa.', { obrigatorio: true, placeholder: 'Ex.: Detergente líquido neutro 500 ml' })}</div>
              <div className="span-2">{campo('descricao', 'Descrição detalhada', 'O que é, como se apresenta, embalagem e uso principal.', { area: true, obrigatorio: true })}</div>
              {campo('composicao', 'Composição ou material', 'Materiais ou componentes principais.', { area: true })}
              {campo('finalidade', 'Finalidade de uso', 'Para que a mercadoria é usada.', { area: true })}
              <div className="span-2">{campo('caracteristicas', 'Características técnicas', 'Medidas, potência, concentração, normas técnicas etc.', { area: true })}</div>
              {campo('ncmAtual', 'NCM atual', 'Se a empresa já usa um código, informe para comparação.', { placeholder: '0000.00.00' })}
            </div>
          </div>
        )}

        {passo === 1 && (
          <div className="stack" style={{ gap: 14 }}>
            <p className="cell-sub" style={{ fontSize: 14 }}>
              Anexe fichas técnicas, catálogos, fotos ou laudos que ajudem a identificar a mercadoria. Esta etapa é opcional.
            </p>
            <div
              className={`dropzone${sobre ? ' is-over' : ''}`}
              role="button"
              tabIndex={0}
              onClick={() => input.current?.click()}
              onKeyDown={(e) => (e.key === 'Enter' || e.key === ' ') && input.current?.click()}
              onDragOver={(e) => {
                e.preventDefault()
                setSobre(true)
              }}
              onDragLeave={() => setSobre(false)}
              onDrop={(e) => {
                e.preventDefault()
                setSobre(false)
                adicionar(e.dataTransfer.files)
              }}
              aria-label="Selecionar documentos técnicos"
            >
              <UploadCloud size={28} color="var(--green-600)" />
              <div style={{ marginTop: 8 }}><strong>Arraste os documentos aqui</strong> ou clique para selecionar</div>
              <div style={{ fontSize: 12.5, marginTop: 4 }}>
                PDF, imagens, Word, Excel ou texto · até 10 MB por arquivo · no máximo {MAX_ARQUIVOS} arquivos
              </div>
              <input
                ref={input}
                type="file"
                multiple
                hidden
                accept={FORMATOS.map((f) => `.${f}`).join(',')}
                onChange={(e) => {
                  adicionar(e.target.files)
                  e.target.value = ''
                }}
              />
            </div>
            {arquivos.length > 0 && (
              <ul className="file-list" aria-label="Documentos selecionados">
                {arquivos.map((f) => {
                  const formatoInvalido = !FORMATOS.includes(extensao(f))
                  const grande = f.size > MAX_ARQUIVO
                  return (
                    <li key={f.name + f.size}>
                      <FileText size={17} color="var(--navy-700)" />
                      <span className="grow" title={f.name}>{f.name}</span>
                      <span className="badge badge--gray badge--sm">{extensao(f).toUpperCase() || '?'}</span>
                      <span className="cell-sub" style={{ color: grande ? 'var(--danger)' : undefined }}>{fmtBytes(f.size)}</span>
                      <button className="icon-btn" style={{ width: 28, height: 28 }} onClick={() => setArquivos((a) => a.filter((x) => x !== f))} aria-label={`Remover ${f.name}`}>
                        <Trash2 size={15} />
                      </button>
                      {(formatoInvalido || grande) && (
                        <span className="field__erro" style={{ flexBasis: '100%' }}>
                          {formatoInvalido ? 'Formato não aceito.' : 'Arquivo acima de 10 MB.'} Remova para continuar.
                        </span>
                      )}
                    </li>
                  )
                })}
              </ul>
            )}
          </div>
        )}

        {passo === 2 && (
          <div className="stack" style={{ gap: 16 }}>
            <div className="resumo-analise">
              <h3>Mercadoria</h3>
              <dl className="dl">
                <div className="dl--largo"><dt>Nome</dt><dd>{form.nome}</dd></div>
                <div className="dl--largo"><dt>Descrição</dt><dd>{form.descricao}</dd></div>
                {form.composicao && <div><dt>Composição</dt><dd>{form.composicao}</dd></div>}
                {form.finalidade && <div><dt>Finalidade</dt><dd>{form.finalidade}</dd></div>}
                {form.caracteristicas && <div><dt>Características técnicas</dt><dd>{form.caracteristicas}</dd></div>}
                <div><dt>NCM atual</dt><dd className="num">{form.ncmAtual ? fmtNcm(form.ncmAtual) : 'Não informada'}</dd></div>
              </dl>
              <h3 style={{ marginTop: 18 }}>Documentos</h3>
              {arquivos.length === 0 ? (
                <p className="cell-sub">Nenhum documento anexado.</p>
              ) : (
                <ul className="lista-icone">
                  {arquivos.map((f) => <li key={f.name + f.size}><FileText size={15} color="var(--muted)" /> <span>{f.name} · {fmtBytes(f.size)}</span></li>)}
                </ul>
              )}
            </div>
            {indisponivel && (
              <ServicoIndisponivel compacto>
                <p className="cell-sub" style={{ marginTop: 6 }}>Os dados preenchidos foram mantidos nesta tela.</p>
              </ServicoIndisponivel>
            )}
            {erro && <Aviso tipo="error">{erro}</Aviso>}
            <p className="cell-sub">
              Ao iniciar, a mercadoria e os documentos são enviados para análise. Você acompanha o andamento em seguida.
            </p>
          </div>
        )}

        <div className="form-acoes nova-analise__acoes">
          {passo > 0 ? (
            <button className="btn btn--secondary" onClick={() => setPasso((p) => p - 1)} disabled={enviando}>
              <ArrowLeft size={16} /> Voltar
            </button>
          ) : (
            <Link to={rotaAnaliseFiscal(id)} className="btn btn--secondary">Cancelar</Link>
          )}
          {passo < 2 ? (
            <button className="btn btn--primary" onClick={avancar} disabled={passo === 1 && invalidos.length > 0}>
              Continuar <ArrowRight size={16} />
            </button>
          ) : (
            <button className="btn btn--verde" onClick={iniciar} disabled={enviando || !empresa.ativo}>
              {enviando ? <LoaderCircle size={16} className="spin" /> : null}
              {enviando ? 'Enviando...' : 'Iniciar análise fiscal'}
            </button>
          )}
        </div>
      </Card>
    </>
  )
}
