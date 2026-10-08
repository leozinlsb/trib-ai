import { createContext, useContext } from 'react'
import type { Cliente, NotaDetalhe, NotaResumo, Rejeicao, TipoNota, Usuario } from '../api/types'
import type { Periodo } from '../lib/aggregate'

/* ---------- Autenticação ---------- */

export interface AuthCtx {
  usuario: Usuario | null
  /** verificando = ainda consultando a sessão no servidor */
  status: 'verificando' | 'pronto'
  admin: boolean
  entrar: (email: string, senha: string) => Promise<Usuario>
  sair: () => Promise<void>
}

export const AuthContext = createContext<AuthCtx | null>(null)

export function useAuth() {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth fora do AuthProvider')
  return ctx
}

/** Página inicial de cada perfil. */
export function inicioDoUsuario(u: Usuario | null) {
  if (!u) return '/login'
  return u.papel === 'ADMIN' ? '/dashboard' : `/dashboard/empresas/${u.clienteId}`
}

/* ---------- Dados do backend ---------- */

export type Conexao = 'verificando' | 'online' | 'offline'

export interface DadosCtx {
  clientes: Cliente[]
  notas: NotaResumo[]
  status: 'carregando' | 'pronto' | 'erro'
  erro: string | null
  carregadoEm: Date | null
  recarregar: () => Promise<void>
  conexao: Conexao
  verificadoEm: Date | null
  verificarConexao: () => Promise<void>
  /** Busca (com cache) o detalhe das notas pedidas. */
  carregarDetalhes: (ids: number[]) => Promise<void>
  detalhes: ReadonlyMap<number, NotaDetalhe>
  errosDetalhe: ReadonlyMap<number, string>
}

export const DadosContext = createContext<DadosCtx | null>(null)

export function useDados() {
  const ctx = useContext(DadosContext)
  if (!ctx) throw new Error('useDados fora do DadosProvider')
  return ctx
}

/* ---------- Uploads e atividades (registradas neste navegador) ---------- */

export interface Atividade {
  id: string
  /** ISO */
  quando: string
  clienteId: number
  clienteNome: string
  arquivos: string[]
  importadas: { id: number; numero: number | null; tipo: TipoNota }[]
  rejeitadas: Rejeicao[]
  /** erro geral (ex.: backend fora do ar) */
  erro?: string
  lida: boolean
}

export interface ResultadoEnvio {
  ok: boolean
  atividade: Atividade
}

export interface AtividadeCtx {
  atividades: Atividade[]
  /** arquivos sendo enviados/processados agora por esta aba */
  emProcessamento: number
  naoLidas: number
  marcarLidas: () => void
  limpar: () => void
  enviar: (clienteId: number, arquivos: File[]) => Promise<ResultadoEnvio>
  /** travado = o seletor de empresa fica fixo (upload dentro do ambiente da empresa) */
  abrirUpload: (clienteId?: number, travado?: boolean) => void
}

export const AtividadeContext = createContext<AtividadeCtx | null>(null)

export function useAtividades() {
  const ctx = useContext(AtividadeContext)
  if (!ctx) throw new Error('useAtividades fora do AtividadeProvider')
  return ctx
}

/* ---------- Preferências (locais) ---------- */

export interface Preferencias {
  clientePadraoId: number | null
  periodoGrafico: Periodo
  itensPorPagina: number
}

export interface PreferenciasCtx {
  prefs: Preferencias
  definir: (p: Partial<Preferencias>) => void
}

export const PreferenciasContext = createContext<PreferenciasCtx | null>(null)

export function usePreferencias() {
  const ctx = useContext(PreferenciasContext)
  if (!ctx) throw new Error('usePreferencias fora do PreferenciasProvider')
  return ctx
}

/* ---------- Toasts ---------- */

export type TipoToast = 'sucesso' | 'erro' | 'info'

export interface Toast {
  id: number
  tipo: TipoToast
  titulo: string
  texto?: string
}

export interface ToastCtx {
  mostrar: (t: Omit<Toast, 'id'>) => void
}

export const ToastContext = createContext<ToastCtx | null>(null)

export function useToast() {
  const ctx = useContext(ToastContext)
  if (!ctx) throw new Error('useToast fora do ToastProvider')
  return ctx
}
