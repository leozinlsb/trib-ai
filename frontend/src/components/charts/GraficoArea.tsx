import { useId, useMemo, useState } from 'react'
import type { Ponto } from '../../lib/aggregate'
import { fmtMoeda, fmtMoedaCompacta } from '../../lib/format'
import { caminhoMonotono, escalaY, useLargura } from './useLargura'

const SERIES = [
  { chave: 'saidas', qtd: 'qtdSaidas', rotulo: 'Saídas', cor: 'var(--serie-saida)', hex: '#2c4f96' },
  { chave: 'entradas', qtd: 'qtdEntradas', rotulo: 'Entradas', cor: 'var(--serie-entrada)', hex: '#16a06e' },
] as const

const M = { top: 10, right: 12, bottom: 30, left: 52 }

/** Área suavizada com duas séries (saídas x entradas), crosshair e tooltip. */
export function GraficoArea({ pontos, altura = 228 }: { pontos: Ponto[]; altura?: number }) {
  const { ref, largura } = useLargura<HTMLDivElement>()
  const [hover, setHover] = useState<number | null>(null)
  const uid = useId().replace(/:/g, '')

  const w = largura - M.left - M.right
  const h = altura - M.top - M.bottom
  const max = Math.max(0, ...pontos.flatMap((p) => [p.saidas, p.entradas]))
  const { topo, passo } = escalaY(max)
  const x = (i: number) => M.left + (pontos.length <= 1 ? w / 2 : (i / (pontos.length - 1)) * w)
  const y = (v: number) => M.top + h - (v / topo) * h

  const caminhos = useMemo(
    () =>
      SERIES.map((s) => {
        const pts = pontos.map((p, i) => [x(i), y(p[s.chave])] as [number, number])
        const linha = caminhoMonotono(pts)
        const area = pts.length
          ? `${linha} L${pts[pts.length - 1]![0]},${M.top + h} L${pts[0]![0]},${M.top + h} Z`
          : ''
        return { ...s, linha, area }
      }),
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [pontos, largura, altura, topo],
  )

  const ticks = Array.from({ length: Math.round(topo / passo) + 1 }, (_, i) => i * passo)
  // rótulos do eixo X sem colisão: no máximo ~1 a cada 56 px
  const cada = Math.max(1, Math.ceil(pontos.length / Math.max(1, Math.floor(w / 56))))

  const ultimoMultiplo = Math.floor((pontos.length - 1) / cada) * cada
  // o último período ganha rótulo só se não colidir com o anterior
  const mostraRotulo = (i: number) =>
    i % cada === 0 || (i === pontos.length - 1 && x(i) - x(ultimoMultiplo) >= 48)

  const onMove = (e: React.MouseEvent<SVGRectElement>) => {
    const r = e.currentTarget.getBoundingClientRect()
    const px = e.clientX - r.left
    const i = pontos.length <= 1 ? 0 : Math.round((px / r.width) * (pontos.length - 1))
    setHover(Math.max(0, Math.min(pontos.length - 1, i)))
  }

  const p = hover != null ? pontos[hover] : null
  const resumo = `Valor das notas por período: ${pontos
    .map((p) => `${p.rotuloLongo}: saídas ${fmtMoeda(p.saidas)}, entradas ${fmtMoeda(p.entradas)}`)
    .join('; ')}`

  return (
    <div className="chart" ref={ref}>
      <svg width={largura} height={altura} role="img" aria-label={resumo}>
        <defs>
          {SERIES.map((s) => (
            <linearGradient key={s.chave} id={`${uid}-${s.chave}`} x1="0" x2="0" y1="0" y2="1">
              <stop offset="0%" stopColor={s.hex} stopOpacity={s.chave === 'saidas' ? 0.55 : 0.5} />
              <stop offset="100%" stopColor={s.hex} stopOpacity={0.03} />
            </linearGradient>
          ))}
        </defs>

        {ticks.map((t) => (
          <g key={t}>
            <line x1={M.left} x2={M.left + w} y1={y(t)} y2={y(t)} stroke="#ececf0" strokeWidth={1} />
            <text x={M.left - 10} y={y(t)} dy="0.32em" textAnchor="end" fontSize={12} fill="#5f5f66">
              {fmtMoedaCompacta(t)}
            </text>
          </g>
        ))}

        {caminhos.map((c) => (
          <path key={`a-${c.chave}`} d={c.area} fill={`url(#${uid}-${c.chave})`} />
        ))}
        {caminhos.map((c) => (
          <path key={`l-${c.chave}`} d={c.linha} fill="none" stroke={c.cor} strokeWidth={2.4} strokeLinejoin="round" strokeLinecap="round" />
        ))}

        {pontos.map((pt, i) =>
          mostraRotulo(i) ? (
            <text key={pt.chave} x={x(i)} y={altura - 8} textAnchor="middle" fontSize={12.5} fill="#3a3a40">
              {pt.rotulo}
            </text>
          ) : null,
        )}

        {p && hover != null && (
          <g pointerEvents="none">
            <line x1={x(hover)} x2={x(hover)} y1={M.top} y2={M.top + h} stroke="#9a9aa3" strokeDasharray="3 3" />
            {SERIES.map((s) => (
              <circle key={s.chave} cx={x(hover)} cy={y(p[s.chave])} r={4.5} fill={s.cor} stroke="#fff" strokeWidth={2} />
            ))}
          </g>
        )}

        <rect
          x={M.left}
          y={M.top}
          width={Math.max(w, 1)}
          height={h}
          fill="transparent"
          onMouseMove={onMove}
          onMouseLeave={() => setHover(null)}
        />
      </svg>

      {p && hover != null && (
        <div
          className="chart-tooltip"
          style={{
            left: Math.min(Math.max(x(hover) - 90, 0), largura - 190),
            top: M.top + 4,
          }}
        >
          <div className="chart-tooltip__title">{p.rotuloLongo}</div>
          {SERIES.map((s) => (
            <div className="chart-tooltip__row" key={s.chave}>
              <span className="legend__swatch" style={{ background: s.cor }} />
              {s.rotulo} ({p[s.qtd]})<b>{fmtMoeda(p[s.chave])}</b>
            </div>
          ))}
        </div>
      )}

      <div className="sr-only">
        <table>
          <caption>Valor das notas por período</caption>
          <thead>
            <tr>
              <th>Período</th>
              <th>Saídas</th>
              <th>Entradas</th>
            </tr>
          </thead>
          <tbody>
            {pontos.map((pt) => (
              <tr key={pt.chave}>
                <td>{pt.rotuloLongo}</td>
                <td>{fmtMoeda(pt.saidas)} ({pt.qtdSaidas} notas)</td>
                <td>{fmtMoeda(pt.entradas)} ({pt.qtdEntradas} notas)</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  )
}

export function LegendaSeries() {
  return (
    <div className="legend">
      {SERIES.map((s) => (
        <span className="legend__item" key={s.chave}>
          <span className="legend__swatch" style={{ background: s.cor }} />
          {s.rotulo}
        </span>
      ))}
    </div>
  )
}
