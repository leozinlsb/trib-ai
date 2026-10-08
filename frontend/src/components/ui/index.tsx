import { useEffect, useId, useRef, useState, type ReactNode } from 'react'
import { ChevronLeft, ChevronRight, CircleAlert, Inbox, Info, LoaderCircle, RefreshCw, TriangleAlert, X } from 'lucide-react'

/* ---------- Card ---------- */

export function Card({
  titulo, sub, acoes, children, className = '', corpo = 'normal', id,
}: {
  titulo?: ReactNode
  sub?: ReactNode
  acoes?: ReactNode
  children: ReactNode
  className?: string
  corpo?: 'normal' | 'flush' | 'nenhum'
  id?: string
}) {
  const head = titulo || acoes
  return (
    <section className={`card ${className}`} aria-labelledby={titulo && id ? `${id}-t` : undefined}>
      {head && (
        <header className="card__head">
          <div>
            {titulo && <h2 className="card__title" id={id ? `${id}-t` : undefined}>{titulo}</h2>}
            {sub && <p className="card__sub">{sub}</p>}
          </div>
          {acoes}
        </header>
      )}
      {corpo === 'nenhum' ? children : <div className={corpo === 'flush' ? 'card__body--flush' : 'card__body'}>{children}</div>}
    </section>
  )
}

/* ---------- KPI ---------- */

export function KpiCard({
  rotulo, valor, dica, arte, indisponivel, carregando,
}: {
  rotulo: string
  valor: ReactNode
  dica?: string
  arte?: ReactNode
  indisponivel?: boolean
  carregando?: boolean
}) {
  return (
    <div className="card kpi">
      <div className="kpi__label">{rotulo}</div>
      <div className="kpi__row">
        {carregando ? (
          <span className="skeleton" style={{ width: 90, height: 30 }} aria-label="Carregando" />
        ) : (
          <div className={`kpi__value ${indisponivel ? 'kpi__value--na' : ''}`}>{valor}</div>
        )}
        {arte && <div className="kpi__art">{arte}</div>}
      </div>
      {dica && <div className="kpi__hint" title={dica}>{dica}</div>}
    </div>
  )
}

/* ---------- Badge ---------- */

export type CorBadge = 'green' | 'blue' | 'gray' | 'amber' | 'red'

export function Badge({ cor, children, sm, title }: { cor: CorBadge; children: ReactNode; sm?: boolean; title?: string }) {
  return (
    <span className={`badge badge--${cor} ${sm ? 'badge--sm' : ''}`} title={title}>
      {children}
    </span>
  )
}

/* ---------- Estados ---------- */

export function Carregando({ linhas = 4, altura = 18 }: { linhas?: number; altura?: number }) {
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 12, padding: 18 }} aria-busy="true" aria-label="Carregando">
      {Array.from({ length: linhas }, (_, i) => (
        <span key={i} className="skeleton" style={{ height: altura, width: `${92 - i * 9}%` }} />
      ))}
    </div>
  )
}

export function ErroEstado({ titulo = 'Não foi possível carregar', mensagem, onTentar }: {
  titulo?: string
  mensagem: string
  onTentar?: () => void
}) {
  return (
    <div className="state state--error" role="alert">
      <span className="state__icon"><CircleAlert size={20} /></span>
      <div className="state__title">{titulo}</div>
      <p>{mensagem}</p>
      {onTentar && (
        <button className="btn btn--secondary btn--sm" onClick={onTentar} style={{ marginTop: 6 }}>
          <RefreshCw size={14} /> Tentar novamente
        </button>
      )}
    </div>
  )
}

export function Vazio({ titulo, children, icone }: { titulo: string; children?: ReactNode; icone?: ReactNode }) {
  return (
    <div className="state">
      <span className="state__icon">{icone ?? <Inbox size={20} />}</span>
      <div className="state__title">{titulo}</div>
      {children && <p>{children}</p>}
    </div>
  )
}

/* ---------- Aviso de limitação ---------- */

export function Aviso({ tipo = 'info', children, style }: {
  tipo?: 'info' | 'warn' | 'error'
  children: ReactNode
  style?: React.CSSProperties
}) {
  const Icone = tipo === 'warn' ? TriangleAlert : tipo === 'error' ? CircleAlert : Info
  return (
    <div className={`notice ${tipo !== 'info' ? `notice--${tipo}` : ''}`} style={style}>
      <Icone size={17} />
      <div>{children}</div>
    </div>
  )
}

/* ---------- Segmented ---------- */

export function Segmented<T extends string>({ opcoes, valor, onChange, rotulo }: {
  opcoes: { valor: T; rotulo: string }[]
  valor: T
  onChange: (v: T) => void
  rotulo: string
}) {
  return (
    <div className="segmented" role="group" aria-label={rotulo}>
      {opcoes.map((o) => (
        <button key={o.valor} type="button" aria-pressed={o.valor === valor} onClick={() => onChange(o.valor)}>
          {o.rotulo}
        </button>
      ))}
    </div>
  )
}

/* ---------- Paginação ---------- */

export function Paginacao({ pagina, total, porPagina, onChange, resumo }: {
  resumo?: ReactNode
  pagina: number
  total: number
  porPagina: number
  onChange: (p: number) => void
}) {
  const paginas = Math.max(1, Math.ceil(total / porPagina))
  if (total === 0) return null
  const ini = (pagina - 1) * porPagina + 1
  const fim = Math.min(total, pagina * porPagina)
  return (
    <nav className="pagination" aria-label="Paginação">
      <span>
        {ini}–{fim} de {total}
        {resumo}
      </span>
      <div className="pagination__btns">
        <button className="btn btn--secondary btn--sm" disabled={pagina <= 1} onClick={() => onChange(pagina - 1)}>
          <ChevronLeft size={14} /> Anterior
        </button>
        <span style={{ alignSelf: 'center', padding: '0 4px' }}>
          {pagina} / {paginas}
        </span>
        <button className="btn btn--secondary btn--sm" disabled={pagina >= paginas} onClick={() => onChange(pagina + 1)}>
          Próxima <ChevronRight size={14} />
        </button>
      </div>
    </nav>
  )
}

/* ---------- Confirmação (ações destrutivas) ---------- */

export function Confirmacao({ titulo, children, rotulo, perigo = true, onConfirmar, onFechar }: {
  titulo: string
  children: ReactNode
  rotulo: string
  perigo?: boolean
  onConfirmar: () => Promise<void>
  onFechar: () => void
}) {
  const [executando, setExecutando] = useState(false)
  const [erro, setErro] = useState<string | null>(null)
  return (
    <Modal
      titulo={titulo}
      onFechar={onFechar}
      travado={executando}
      rodape={
        <>
          <button className="btn btn--secondary" onClick={onFechar} disabled={executando}>Cancelar</button>
          <button
            className={`btn ${perigo ? 'btn--perigo' : 'btn--primary'}`}
            disabled={executando}
            onClick={async () => {
              setExecutando(true)
              setErro(null)
              try {
                await onConfirmar()
              } catch (e) {
                setErro(e instanceof Error ? e.message : 'Não foi possível concluir.')
                setExecutando(false)
              }
            }}
          >
            {executando && <LoaderCircle size={16} className="spin" />}
            {rotulo}
          </button>
        </>
      }
    >
      <div style={{ fontSize: 14.5, color: 'var(--text-2)', lineHeight: 1.6 }}>{children}</div>
      {erro && <Aviso tipo="error">{erro}</Aviso>}
    </Modal>
  )
}

/* ---------- Modal ---------- */

export function Modal({ titulo, sub, onFechar, children, rodape, travado, largura }: {
  titulo: string
  sub?: string
  onFechar: () => void
  children: ReactNode
  rodape?: ReactNode
  /** impede fechar (ex.: envio em andamento) */
  travado?: boolean
  /** largura máxima em px (padrão 620) */
  largura?: number
}) {
  const id = useId()
  const ref = useRef<HTMLDivElement>(null)

  useEffect(() => {
    const anterior = document.activeElement as HTMLElement | null
    ref.current?.focus()
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape' && !travado) onFechar()
    }
    document.addEventListener('keydown', onKey)
    document.body.style.overflow = 'hidden'
    return () => {
      document.removeEventListener('keydown', onKey)
      document.body.style.overflow = ''
      anterior?.focus?.()
    }
  }, [onFechar, travado])

  return (
    <div className="modal-backdrop" onMouseDown={(e) => e.target === e.currentTarget && !travado && onFechar()}>
      <div className="modal" style={largura ? { width: `min(${largura}px, 100%)` } : undefined} role="dialog" aria-modal="true" aria-labelledby={id} ref={ref} tabIndex={-1}>
        <div className="modal__head">
          <div>
            <h2 className="modal__title" id={id}>{titulo}</h2>
            {sub && <p className="card__sub">{sub}</p>}
          </div>
          <button className="icon-btn" onClick={onFechar} disabled={travado} aria-label="Fechar">
            <X size={18} />
          </button>
        </div>
        <div className="modal__body">{children}</div>
        {rodape && <div className="modal__foot">{rodape}</div>}
      </div>
    </div>
  )
}
