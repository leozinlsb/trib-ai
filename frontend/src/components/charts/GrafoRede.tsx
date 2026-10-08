import { useId, useMemo, useState } from 'react'
import { layoutConstelacao, papelNo, type No, type Posicao, type Rede } from '../../lib/rede'
import { fmtMoeda, fmtNumero, iniciais } from '../../lib/format'
import { useLargura } from './useLargura'

const NAVY = '#14306a'
const NAVY_CLARO = '#3a63b5'
const AZUL = '#2c4f96'
const VERDE = '#16a06e'
const VERDE_CLARO = '#2fd396'

/** Aresta curva (arco suave para o mesmo lado), estilo "constelação". */
function curva(a: Posicao, b: Posicao, k = 0.16) {
  const mx = (a.x + b.x) / 2
  const my = (a.y + b.y) / 2
  return `M${a.x},${a.y} Q${mx - (b.y - a.y) * k},${my + (b.x - a.x) * k} ${b.x},${b.y}`
}

function encurtar(s: string, max: number) {
  return s.length > max ? `${s.slice(0, max - 1).trimEnd()}…` : s
}

/**
 * Rede de relacionamentos. Clientes do escritório (azul-marinho) no centro de órbitas com as
 * contrapartes (verde). Arestas azuis = predominam vendas; verdes = predominam compras. As partículas
 * correm no sentido da mercadoria: do cliente para fora nas vendas, para o cliente nas compras.
 */
export function GrafoRede({
  rede, altura, interativo = false, selecionado, onSelecionar, rotulos = false,
}: {
  rede: Rede
  altura: number
  interativo?: boolean
  selecionado?: string | null
  onSelecionar?: (no: No | null) => void
  rotulos?: boolean
}) {
  const uid = useId().replace(/:/g, '')
  const [hover, setHover] = useState<string | null>(null)
  const { ref, largura } = useLargura<HTMLDivElement>(900)

  // grande: a área de desenho acompanha a largura real (até 900), para não encolher tudo no celular
  const w = Math.round(Math.min(900, Math.max(340, largura)))
  const k = Math.min(1, Math.max(0.62, w / 900))
  const base = interativo
    ? { w, h: Math.round(Math.min(altura, Math.max(w * 0.7, 300))), m: Math.round(64 * k) }
    : { w: 260, h: 200, m: 18 }
  const alturaReal = interativo ? base.h : altura
  const { pos, orbitas } = useMemo(() => layoutConstelacao(rede, base.w, base.h, base.m), [rede, base.w, base.h, base.m])
  const porId = useMemo(() => new Map(rede.nos.map((n) => [n.id, n])), [rede.nos])

  const grau = useMemo(() => {
    const g = new Map<string, number>()
    for (const a of rede.arestas) {
      g.set(a.origem, (g.get(a.origem) ?? 0) + 1)
      g.set(a.destino, (g.get(a.destino) ?? 0) + 1)
    }
    return g
  }, [rede.arestas])

  const foco = hover ?? selecionado ?? null
  const vizinhos = useMemo(() => {
    if (!foco) return null
    const s = new Set<string>([foco])
    for (const a of rede.arestas) {
      if (a.origem === foco) s.add(a.destino)
      if (a.destino === foco) s.add(a.origem)
    }
    return s
  }, [foco, rede.arestas])

  const maxAresta = Math.max(1, ...rede.arestas.map((a) => a.valor))
  const maxNo = Math.max(1, ...rede.nos.filter((n) => n.papel !== 'cliente').map((n) => n.valor))
  const raio = (n: No) =>
    n.papel === 'cliente'
      ? interativo ? 21 * k : 13
      : interativo ? (8 + Math.sqrt(n.valor / maxNo) * 6) * k : 6 + Math.sqrt(n.valor / maxNo) * 3.5

  const noHover = hover ? porId.get(hover) : undefined

  return (
    <div className={`chart rede${interativo ? ' rede--grande' : ''}`} ref={ref} style={{ height: alturaReal }}>
      <svg
        width="100%"
        height={alturaReal}
        viewBox={`0 0 ${base.w} ${base.h}`}
        preserveAspectRatio="xMidYMid meet"
        style={{ overflow: 'hidden' }}
        role="img"
        aria-label={`Rede com ${orbitas.length} clientes, ${rede.nos.length - orbitas.length} contrapartes e ${rede.arestas.length} ligações`}
        onClick={() => interativo && onSelecionar?.(null)}
      >
        <defs>
          <pattern id={`${uid}-pontos`} width={interativo ? 22 : 12} height={interativo ? 22 : 12} patternUnits="userSpaceOnUse">
            <circle cx={1.2} cy={1.2} r={interativo ? 1.3 : 0.7} fill="#dfe3ec" />
          </pattern>
          <radialGradient id={`${uid}-halo`}>
            <stop offset="0%" stopColor={AZUL} stopOpacity={0.13} />
            <stop offset="55%" stopColor={VERDE} stopOpacity={0.06} />
            <stop offset="100%" stopColor={VERDE} stopOpacity={0} />
          </radialGradient>
          <linearGradient id={`${uid}-hub`} x1="0" y1="0" x2="1" y2="1">
            <stop offset="0%" stopColor={NAVY_CLARO} />
            <stop offset="100%" stopColor={NAVY} />
          </linearGradient>
          <linearGradient id={`${uid}-sat`} x1="0" y1="0" x2="1" y2="1">
            <stop offset="0%" stopColor={VERDE_CLARO} />
            <stop offset="100%" stopColor="#0c8257" />
          </linearGradient>
          <radialGradient id={`${uid}-brilho-hub`}>
            <stop offset="55%" stopColor={AZUL} stopOpacity={0.35} />
            <stop offset="100%" stopColor={AZUL} stopOpacity={0} />
          </radialGradient>
          <radialGradient id={`${uid}-brilho-sat`}>
            <stop offset="50%" stopColor={VERDE} stopOpacity={0.4} />
            <stop offset="100%" stopColor={VERDE} stopOpacity={0} />
          </radialGradient>
          <filter id={`${uid}-sombra`} x="-50%" y="-50%" width="200%" height="200%">
            <feDropShadow dx="0" dy={interativo ? 3 : 1.5} stdDeviation={interativo ? 3 : 1.6} floodColor={NAVY} floodOpacity="0.28" />
          </filter>
          <filter id={`${uid}-sombra-leve`} x="-20%" y="-50%" width="140%" height="200%">
            <feDropShadow dx="0" dy="2" stdDeviation="3" floodColor={NAVY} floodOpacity="0.12" />
          </filter>
          {rede.arestas.map((a) => {
            const p1 = pos.get(a.origem)!
            const p2 = pos.get(a.destino)!
            const cor = a.saidas >= a.entradas ? AZUL : VERDE
            return (
              <linearGradient key={a.id} id={`${uid}-a-${a.id}`} gradientUnits="userSpaceOnUse" x1={p1.x} y1={p1.y} x2={p2.x} y2={p2.y}>
                <stop offset="0%" stopColor={cor} stopOpacity={0.95} />
                <stop offset="100%" stopColor={cor} stopOpacity={0.35} />
              </linearGradient>
            )
          })}
        </defs>

        {/* fundo pontilhado (retângulo maior que o viewBox para cobrir as sobras do "meet") */}
        <rect x={-base.w} y={-base.h} width={base.w * 3} height={base.h * 3} fill={`url(#${uid}-pontos)`} />

        {/* halo e órbita de cada cliente */}
        {orbitas.map((o) => {
          const ativo = !vizinhos || vizinhos.has(o.id)
          return (
            <g key={o.id} className="rede__fade" style={{ opacity: ativo ? 1 : 0.25 }}>
              <circle cx={o.x} cy={o.y} r={o.r * 1.42} fill={`url(#${uid}-halo)`} className={foco === o.id ? 'rede__halo is-on' : 'rede__halo'} style={{ transformOrigin: `${o.x}px ${o.y}px` }} />
              <circle
                cx={o.x}
                cy={o.y}
                r={o.r}
                fill="none"
                stroke="#c9d2e4"
                strokeWidth={interativo ? 1.4 : 0.9}
                strokeDasharray={interativo ? '3 7' : '2 4'}
                className="rede__orbita"
                style={{ transformOrigin: `${o.x}px ${o.y}px` }}
              />
            </g>
          )
        })}

        {/* arestas */}
        {rede.arestas.map((a, i) => {
          const p1 = pos.get(a.origem)!
          const p2 = pos.get(a.destino)!
          const d = curva(p1, p2)
          const venda = a.saidas >= a.entradas
          const ativo = !vizinhos || (vizinhos.has(a.origem) && vizinhos.has(a.destino))
          const destaque = !!vizinhos && ativo
          const w = interativo ? 1.8 + Math.sqrt(a.valor / maxAresta) * 3.6 : 2
          return (
            <g key={a.id} className="rede__fade" style={{ opacity: ativo ? 1 : 0.07 }}>
              <path
                d={d}
                pathLength={1}
                fill="none"
                stroke={`url(#${uid}-a-${a.id})`}
                strokeWidth={destaque ? w + 1.2 : w}
                strokeLinecap="round"
                className="rede__aresta"
                style={{ animationDelay: `${120 + i * 45}ms` }}
              />
              <path
                d={d}
                pathLength={100}
                fill="none"
                stroke="#fff"
                strokeWidth={Math.max(1.4, w * 0.6)}
                strokeLinecap="round"
                className={`rede__fluxo${venda ? '' : ' rede__fluxo--entrada'}${destaque ? ' is-on' : !interativo ? ' is-ambiente' : ''}`}
              />
            </g>
          )
        })}

        {/* nós */}
        {rede.nos.map((n, i) => {
          const p = pos.get(n.id)!
          const r = raio(n)
          const hub = n.papel === 'cliente'
          const ativo = !vizinhos || vizinhos.has(n.id)
          const emFoco = foco === n.id
          const origem = { transformOrigin: `${p.x}px ${p.y}px` }
          return (
            <g key={n.id} className="rede__fade" style={{ opacity: ativo ? 1 : 0.2 }}>
              <g className="rede__entrada" style={{ ...origem, animationDelay: `${(hub ? 0 : 260) + i * 30}ms` }}>
                <g
                  className={`rede__no${emFoco ? ' is-foco' : ''}`}
                  style={{ ...origem, cursor: 'pointer' }}
                  onMouseEnter={() => setHover(n.id)}
                  onMouseLeave={() => setHover(null)}
                  onFocus={() => setHover(n.id)}
                  onBlur={() => setHover(null)}
                  onClick={(e) => {
                    if (!interativo) return
                    e.stopPropagation()
                    onSelecionar?.(n)
                  }}
                  tabIndex={interativo ? 0 : undefined}
                  role={interativo ? 'button' : undefined}
                  aria-label={interativo ? `${n.nome}: ${n.notas} notas, ${fmtMoeda(n.valor)}` : undefined}
                  onKeyDown={(e) => interativo && (e.key === 'Enter' || e.key === ' ') && onSelecionar?.(n)}
                >
                  {/* área de toque maior que o nó */}
                  <circle cx={p.x} cy={p.y} r={r + (interativo ? 10 : 6)} fill="transparent" />
                  <circle
                    cx={p.x}
                    cy={p.y}
                    r={r * (hub ? 2 : 2.2)}
                    fill={`url(#${uid}-brilho-${hub ? 'hub' : 'sat'})`}
                    className="rede__brilho"
                  />
                  {hub && (
                    <circle cx={p.x} cy={p.y} r={r} fill="none" stroke={AZUL} strokeWidth={interativo ? 2 : 1.4} className="rede__pulso" style={{ ...origem, animationDelay: `${i * 0.7}s` }} />
                  )}
                  {selecionado === n.id && (
                    <circle cx={p.x} cy={p.y} r={r + 8} fill="none" stroke={hub ? AZUL : VERDE} strokeWidth={2} strokeDasharray="5 5" className="rede__selecao" style={origem} />
                  )}
                  <circle
                    className="rede__core"
                    cx={p.x}
                    cy={p.y}
                    r={r}
                    fill={`url(#${uid}-${hub ? 'hub' : 'sat'})`}
                    stroke="#fff"
                    strokeWidth={hub ? (interativo ? 3.5 : 2.4) : interativo ? 2.6 : 1.8}
                    filter={`url(#${uid}-sombra)`}
                  />
                  {hub ? (
                    <text x={p.x} y={p.y} dy="0.36em" textAnchor="middle" fontSize={r * 0.68} fontWeight={700} fill="#fff" letterSpacing="0.02em" pointerEvents="none">
                      {iniciais(n.nome) || n.nome.slice(0, 2).toUpperCase()}
                    </text>
                  ) : (
                    <circle cx={p.x - r * 0.28} cy={p.y - r * 0.28} r={r * 0.3} fill="#fff" opacity={0.55} pointerEvents="none" />
                  )}
                </g>
              </g>
            </g>
          )
        })}

        {/* rótulos dos clientes (pílulas) */}
        {rotulos &&
          orbitas.map((o) => {
            const no = porId.get(o.id)!
            // o rótulo não passa da largura da órbita, para não encostar no cluster vizinho
            const fonte = k < 0.8 ? 11.5 : 13
            const letra = fonte * 0.56
            const alt = fonte * 2
            const texto = encurtar(no.nome, Math.max(10, Math.min(28, Math.floor((o.r * 2.8 - 30) / letra))))
            const largura = texto.length * letra + 33
            const x0 = o.x - largura / 2
            const y = o.y + o.r + 8
            const ativo = !vizinhos || vizinhos.has(o.id)
            return (
              <g key={`r-${o.id}`} className="rede__fade" style={{ opacity: ativo ? 1 : 0.25 }} pointerEvents="none">
                <rect x={x0} y={y} width={largura} height={alt} rx={alt / 2} fill="#fff" stroke="#e1e5ee" filter={`url(#${uid}-sombra-leve)`} />
                <circle cx={x0 + 12} cy={y + alt / 2} r={3.6} fill={AZUL} />
                <text x={x0 + 21} y={y + alt / 2} dy="0.35em" fontSize={fonte} fontWeight={600} fill="#1c2b4a">
                  {texto}
                </text>
              </g>
            )
          })}

        {/* rótulos das contrapartes: aparecem ao focar um nó */}
        {rotulos &&
          vizinhos &&
          [...vizinhos].map((id) => {
            const no = porId.get(id)
            if (!no || no.papel === 'cliente') return null
            const p = pos.get(id)!
            const r = raio(no)
            const cos = Math.cos(p.ang)
            const sin = Math.sin(p.ang)
            const anchor = cos > 0.35 ? 'start' : cos < -0.35 ? 'end' : 'middle'
            const dist = r + 9
            return (
              <text
                key={`l-${id}-${foco}`}
                x={p.x + cos * dist}
                y={p.y + sin * dist}
                dy={anchor === 'middle' ? (sin > 0 ? '0.9em' : '-0.2em') : '0.35em'}
                textAnchor={anchor}
                fontSize={12.5}
                fontWeight={500}
                fill="#1c1c20"
                stroke="#fff"
                strokeWidth={4}
                strokeLinejoin="round"
                paintOrder="stroke"
                className="rede__rotulo"
                pointerEvents="none"
              >
                {encurtar(no.nome, 30)}
              </text>
            )
          })}
      </svg>

      {/* detalhes num canto fixo, para não cobrir o cluster em foco */}
      {noHover &&
        (interativo ? (
          <div key={noHover.id} className="chart-tooltip rede-tooltip" style={cantoOposto(pos.get(noHover.id)!, base)}>
            <div className="rede-tooltip__head">
              <span className="rede-tooltip__dot" style={{ background: noHover.papel === 'cliente' ? AZUL : VERDE }} />
              <span>{noHover.nome}</span>
            </div>
            <div className="rede-tooltip__papel">{papelNo(noHover)}</div>
            <div className="chart-tooltip__row">Notas<b>{fmtNumero(noHover.notas)}</b></div>
            <div className="chart-tooltip__row">Valor movimentado<b>{fmtMoeda(noHover.valor)}</b></div>
            <div className="chart-tooltip__row">Conexões<b>{fmtNumero(grau.get(noHover.id) ?? 0)}</b></div>
          </div>
        ) : (
          <div key={noHover.id} className="chart-tooltip rede-tooltip rede-tooltip--compacto">
            <div className="rede-tooltip__head">
              <span className="rede-tooltip__dot" style={{ background: noHover.papel === 'cliente' ? AZUL : VERDE }} />
              <span className="rede-tooltip__nome">{noHover.nome}</span>
            </div>
            <div className="rede-tooltip__papel">
              {papelNo(noHover)} · {fmtNumero(noHover.notas)} nota(s) · {fmtMoeda(noHover.valor)}
            </div>
          </div>
        ))}
    </div>
  )
}

/** Canto do gráfico oposto ao nó em foco, para o painel de detalhes não cobrir a vizinhança dele. */
function cantoOposto(p: Posicao, base: { w: number; h: number }): React.CSSProperties {
  return {
    ...(p.x < base.w / 2 ? { right: 12 } : { left: 12 }),
    ...(p.y < base.h / 2 ? { bottom: 12 } : { top: 12 }),
  }
}
