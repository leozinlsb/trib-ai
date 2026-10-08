import type { ProblemDetail } from './types'

/** Vazio = mesma origem (proxy do Vite repassa /api para o backend). */
export const API_URL = (import.meta.env.VITE_API_URL ?? '').replace(/\/$/, '')

/** Disparado quando uma chamada recebe 401: a sessão acabou (o AuthProvider leva ao login). */
export const EVENTO_SESSAO_EXPIRADA = 'tribia:sessao-expirada'

export class ApiError extends Error {
  readonly status: number
  readonly problem: ProblemDetail | null

  constructor(status: number, message: string, problem: ProblemDetail | null = null) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.problem = problem
  }

  /** Sem resposta do servidor (backend fora do ar, rede, CORS). */
  get semConexao() {
    return this.status === 0
  }
}

async function lerProblema(res: Response): Promise<ProblemDetail | null> {
  const tipo = res.headers.get('content-type') ?? ''
  if (!tipo.includes('json')) return null
  try {
    return (await res.json()) as ProblemDetail
  } catch {
    return null
  }
}

function mensagemPadrao(status: number) {
  if (status === 401) return 'Faça login para continuar.'
  if (status === 403) return 'Você não tem permissão para esta operação.'
  if (status === 404) return 'Recurso não encontrado.'
  if (status === 413) return 'O upload excede o tamanho máximo permitido.'
  if (status >= 500) return 'Ocorreu um erro inesperado. Tente novamente.'
  return 'Não foi possível concluir a operação. Tente novamente.'
}

/** Token CSRF do cookie XSRF-TOKEN (o cookie de sessão é HttpOnly e não é lido aqui). */
function tokenCsrf() {
  const m = document.cookie.match(/(?:^|;\s*)XSRF-TOKEN=([^;]+)/)
  return m ? decodeURIComponent(m[1]!) : null
}

async function garantirTokenCsrf() {
  if (tokenCsrf()) return
  await fetch(`${API_URL}/api/auth/csrf`, { credentials: 'same-origin' }).catch(() => undefined)
}

export interface Opcoes extends RequestInit {
  /** Não dispara o evento de sessão expirada (login e verificação inicial). */
  silenciar401?: boolean
}

export async function request<T>(path: string, init: Opcoes = {}): Promise<T> {
  const { silenciar401, ...opcoes } = init
  const metodo = (opcoes.method ?? 'GET').toUpperCase()
  const headers: Record<string, string> = { Accept: 'application/json', ...(opcoes.headers as Record<string, string>) }
  if (metodo !== 'GET' && metodo !== 'HEAD') {
    await garantirTokenCsrf()
    const t = tokenCsrf()
    if (t) headers['X-XSRF-TOKEN'] = t
  }

  let res: Response
  try {
    res = await fetch(`${API_URL}${path}`, { ...opcoes, headers, credentials: 'same-origin' })
  } catch (e) {
    if (e instanceof DOMException && e.name === 'AbortError') throw e
    throw new ApiError(0, 'Não foi possível conectar ao TribIA. Verifique sua conexão e tente novamente.')
  }

  if (!res.ok) {
    const problem = await lerProblema(res)
    // Sem corpo JSON em 5xx do proxy = backend fora do ar
    if (res.status >= 500 && !problem) {
      throw new ApiError(0, 'O TribIA não respondeu. Tente novamente em instantes.')
    }
    if (res.status === 401 && !silenciar401) {
      window.dispatchEvent(new Event(EVENTO_SESSAO_EXPIRADA))
    }
    throw new ApiError(res.status, problem?.detail || mensagemPadrao(res.status), problem)
  }
  if (res.status === 204) return undefined as T
  return (await res.json()) as T
}

export function json(metodo: 'POST' | 'PUT', corpo: unknown): RequestInit {
  return { method: metodo, headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(corpo) }
}
