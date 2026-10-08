import { useCallback, useEffect, useMemo, useState, type ReactNode } from 'react'
import { EVENTO_SESSAO_EXPIRADA } from '../api/client'
import * as api from '../api/tribia'
import type { Usuario } from '../api/types'
import { AuthContext, type AuthCtx } from './contexts'

/**
 * Sessão do usuário. A sessão em si é um cookie HttpOnly do servidor: aqui só guardamos os dados
 * públicos devolvidos por /api/auth/me (nada sensível no navegador).
 */
export function AuthProvider({ children }: { children: ReactNode }) {
  const [usuario, setUsuario] = useState<Usuario | null>(null)
  const [status, setStatus] = useState<AuthCtx['status']>('verificando')

  useEffect(() => {
    const ctrl = new AbortController()
    api
      .usuarioAtual(ctrl.signal)
      .then((u) => setUsuario(u ?? null)) // 204 = sem sessão
      .catch(() => setUsuario(null))
      .finally(() => setStatus('pronto'))
    const expirou = () => setUsuario(null)
    window.addEventListener(EVENTO_SESSAO_EXPIRADA, expirou)
    return () => {
      ctrl.abort()
      window.removeEventListener(EVENTO_SESSAO_EXPIRADA, expirou)
    }
  }, [])

  const entrar = useCallback(async (email: string, senha: string) => {
    const u = await api.entrar(email, senha)
    setUsuario(u)
    return u
  }, [])

  const sair = useCallback(async () => {
    try {
      await api.sair()
    } finally {
      setUsuario(null)
    }
  }, [])

  const valor = useMemo<AuthCtx>(
    () => ({ usuario, status, admin: usuario?.papel === 'ADMIN', entrar, sair }),
    [usuario, status, entrar, sair],
  )
  return <AuthContext value={valor}>{children}</AuthContext>
}
