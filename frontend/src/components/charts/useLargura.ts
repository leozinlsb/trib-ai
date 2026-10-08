import { useLayoutEffect, useRef, useState } from 'react'

/** Largura do contêiner em px, atualizada com ResizeObserver (gráficos nítidos em qualquer tamanho). */
export function useLargura<T extends HTMLElement>(inicial = 600) {
  const ref = useRef<T>(null)
  const [largura, setLargura] = useState(inicial)
  useLayoutEffect(() => {
    const el = ref.current
    if (!el) return
    setLargura(el.clientWidth || inicial)
    const ro = new ResizeObserver(([e]) => {
      if (e) setLargura(Math.max(200, Math.round(e.contentRect.width)))
    })
    ro.observe(el)
    return () => ro.disconnect()
  }, [inicial])
  return { ref, largura }
}

/** Curva monotônica (Fritsch–Carlson): suave sem "passar do ponto" entre valores. */
export function caminhoMonotono(pts: [number, number][]) {
  const n = pts.length
  if (n === 0) return ''
  if (n === 1) return `M${pts[0]![0]},${pts[0]![1]}`
  const dx: number[] = []
  const m: number[] = []
  for (let i = 0; i < n - 1; i++) {
    dx.push(pts[i + 1]![0] - pts[i]![0])
    m.push((pts[i + 1]![1] - pts[i]![1]) / dx[i]!)
  }
  const t: number[] = [m[0]!]
  for (let i = 1; i < n - 1; i++) {
    t.push(m[i - 1]! * m[i]! <= 0 ? 0 : (m[i - 1]! + m[i]!) / 2)
  }
  t.push(m[n - 2]!)
  for (let i = 0; i < n - 1; i++) {
    if (m[i] === 0) {
      t[i] = 0
      t[i + 1] = 0
    } else {
      const a = t[i]! / m[i]!
      const b = t[i + 1]! / m[i]!
      const s = a * a + b * b
      if (s > 9) {
        const k = 3 / Math.sqrt(s)
        t[i] = k * a * m[i]!
        t[i + 1] = k * b * m[i]!
      }
    }
  }
  let d = `M${pts[0]![0]},${pts[0]![1]}`
  for (let i = 0; i < n - 1; i++) {
    const [x0, y0] = pts[i]!
    const [x1, y1] = pts[i + 1]!
    const h = dx[i]! / 3
    d += ` C${x0 + h},${y0 + t[i]! * h} ${x1 - h},${y1 - t[i + 1]! * h} ${x1},${y1}`
  }
  return d
}

/** Teto "redondo" para o eixo Y e passo das linhas de grade. */
export function escalaY(max: number, divisoes = 4) {
  if (max <= 0) return { topo: divisoes, passo: 1 }
  const bruto = max / divisoes
  const pot = 10 ** Math.floor(Math.log10(bruto))
  const passo = [1, 2, 2.5, 5, 10].map((f) => f * pot).find((p) => p >= bruto) ?? 10 * pot
  return { topo: passo * divisoes, passo }
}
