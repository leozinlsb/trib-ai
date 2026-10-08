import { caminhoMonotono } from './useLargura'

/** Mini linha de tendência (cartão "Documentos Analisados"), com área suave como na referência. */
export function Sparkline({ valores, largura = 52, altura = 26, rotulo }: {
  valores: number[]
  largura?: number
  altura?: number
  rotulo: string
}) {
  if (valores.length < 2) return null
  const max = Math.max(...valores, 1)
  const min = Math.min(...valores, 0)
  const pts = valores.map(
    (v, i) =>
      [2 + (i / (valores.length - 1)) * (largura - 4), altura - 3 - ((v - min) / (max - min || 1)) * (altura - 6)] as [number, number],
  )
  const linha = caminhoMonotono(pts)
  return (
    <svg width={largura} height={altura} role="img" aria-label={rotulo}>
      <defs>
        <linearGradient id="spark-g" x1="0" x2="0" y1="0" y2="1">
          <stop offset="0%" stopColor="#16a06e" stopOpacity={0.28} />
          <stop offset="100%" stopColor="#16a06e" stopOpacity={0} />
        </linearGradient>
      </defs>
      <path d={`${linha} L${pts[pts.length - 1]![0]},${altura} L${pts[0]![0]},${altura} Z`} fill="url(#spark-g)" />
      <path d={linha} fill="none" stroke="#16a06e" strokeWidth={2} strokeLinecap="round" />
    </svg>
  )
}
