/**
 * Logotipo TribIA: documento com circuito de rede + "Trib" e "IA" verde.
 * fundo="escuro" (sidebar azul) ou "claro" (landing, fundo branco).
 */
export function Logo({ tamanho = 40, fundo = 'escuro' }: { tamanho?: number; fundo?: 'escuro' | 'claro' }) {
  const traco = fundo === 'escuro' ? '#fff' : '#12306a'
  const contorno = fundo === 'escuro' ? '#12306a' : '#fff'
  const noCentral = fundo === 'escuro' ? '#fff' : '#12306a'
  return (
    <span style={{ display: 'inline-flex', alignItems: 'center', gap: 8 }}>
      <svg width={tamanho} height={tamanho} viewBox="0 0 40 40" fill="none" aria-hidden="true">
        <path
          d="M8 5.5A2.5 2.5 0 0 1 10.5 3h14l7.5 7.5v24A2.5 2.5 0 0 1 29.5 37h-19A2.5 2.5 0 0 1 8 34.5v-29Z"
          stroke={traco} strokeWidth="2.4" strokeLinejoin="round"
        />
        <path d="M24.5 3v7.5H32" stroke={traco} strokeWidth="2.4" strokeLinejoin="round" />
        <path d="M13 13h7M13 18h5" stroke={traco} strokeWidth="2.4" strokeLinecap="round" />
        <path d="M17 31l5-8 6 5" stroke="#1fb07c" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round" />
        <circle cx="17" cy="31" r="3" fill="#1fb07c" stroke={contorno} strokeWidth="1.4" />
        <circle cx="22" cy="23" r="3" fill={noCentral} stroke={contorno} strokeWidth="1.4" />
        <circle cx="28" cy="28" r="3" fill="#1fb07c" stroke={contorno} strokeWidth="1.4" />
      </svg>
      <span style={{ fontSize: tamanho * 0.8, fontWeight: 700, letterSpacing: '-0.02em', lineHeight: 1 }}>
        <span style={{ color: fundo === 'escuro' ? '#fff' : '#12306a' }}>Trib</span>
        <span style={{ color: fundo === 'escuro' ? 'var(--green-500)' : 'var(--green-600)' }}>IA</span>
      </span>
    </span>
  )
}
