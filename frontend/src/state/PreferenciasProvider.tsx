import { useCallback, useMemo, useState, type ReactNode } from 'react'
import { gravarLocal, lerLocal } from '../lib/storage'
import { PreferenciasContext, type Preferencias } from './contexts'

const CHAVE = 'tribia.preferencias'
const PADRAO: Preferencias = { clientePadraoId: null, periodoGrafico: 'semanal', itensPorPagina: 10 }

export function PreferenciasProvider({ children }: { children: ReactNode }) {
  const [prefs, setPrefs] = useState<Preferencias>(() => ({ ...PADRAO, ...lerLocal(CHAVE, {}) }))

  const definir = useCallback((p: Partial<Preferencias>) => {
    setPrefs((atual) => {
      const novo = { ...atual, ...p }
      gravarLocal(CHAVE, novo)
      return novo
    })
  }, [])

  const valor = useMemo(() => ({ prefs, definir }), [prefs, definir])
  return <PreferenciasContext value={valor}>{children}</PreferenciasContext>
}
