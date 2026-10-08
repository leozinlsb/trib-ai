import { json, request } from './client'
import type {
  CalculoNota, ClassificacaoNota, Cliente, ClienteForm, NotaDetalhe, NotaResumo, OpcaoClassificacao, Painel, Relatorio,
  Revisao, RevisaoResultado, RevisarItem, TipoNota, UploadResultado, Usuario,
} from './types'

// Endpoints reais do backend. O backend aplica as permissões: cada usuário só recebe o que pode ver.

/* ---------- Autenticação ---------- */

export function entrar(email: string, senha: string) {
  return request<Usuario>('/api/auth/login', { ...json('POST', { email, senha }), silenciar401: true })
}

export function sair() {
  return request<void>('/api/auth/logout', { method: 'POST', silenciar401: true })
}

/** Usuário da sessão; undefined quando não há sessão (o servidor responde 204). */
export function usuarioAtual(signal?: AbortSignal) {
  return request<Usuario | undefined>('/api/auth/me', { signal, silenciar401: true })
}

/* ---------- Empresas ---------- */

export function listarClientes(signal?: AbortSignal) {
  return request<Cliente[]>('/api/clientes', { signal })
}

export function buscarCliente(id: number, signal?: AbortSignal) {
  return request<Cliente>(`/api/clientes/${id}`, { signal })
}

export function criarCliente(form: ClienteForm) {
  return request<Cliente>('/api/clientes', json('POST', form))
}

export function atualizarCliente(id: number, form: ClienteForm) {
  return request<Cliente>(`/api/clientes/${id}`, json('PUT', form))
}

/** Exclusão lógica: a empresa fica desativada, notas e acessos são mantidos. */
export function desativarCliente(id: number) {
  return request<Cliente>(`/api/clientes/${id}`, { method: 'DELETE' })
}

export function reativarCliente(id: number) {
  return request<Cliente>(`/api/clientes/${id}/reativar`, { method: 'POST' })
}

export function listarUsuarios(clienteId: number, signal?: AbortSignal) {
  return request<Usuario[]>(`/api/clientes/${clienteId}/usuarios`, { signal })
}

export function criarUsuario(clienteId: number, dados: { nome: string; email: string; senha: string }) {
  return request<Usuario>(`/api/clientes/${clienteId}/usuarios`, json('POST', dados))
}

export function removerUsuario(id: number) {
  return request<void>(`/api/usuarios/${id}`, { method: 'DELETE' })
}

/* ---------- Notas ---------- */

export interface FiltroNotas {
  tipo?: TipoNota
  /** AAAA-MM */
  competencia?: string
}

export function listarNotas(clienteId: number, filtro: FiltroNotas = {}, signal?: AbortSignal) {
  const qs = new URLSearchParams()
  if (filtro.tipo) qs.set('tipo', filtro.tipo)
  if (filtro.competencia) qs.set('competencia', filtro.competencia)
  const q = qs.toString()
  return request<NotaResumo[]>(`/api/clientes/${clienteId}/notas${q ? `?${q}` : ''}`, { signal })
}

export function detalharNota(id: number, signal?: AbortSignal) {
  return request<NotaDetalhe>(`/api/notas/${id}`, { signal })
}

/**
 * Upload de um ou mais XMLs (campo multipart "arquivos").
 * 201 quando ao menos um foi importado; senão ApiError 422/409 com o motivo.
 */
export function enviarNotas(clienteId: number, arquivos: File[]) {
  const form = new FormData()
  arquivos.forEach((f) => form.append('arquivos', f, f.name))
  return request<UploadResultado>(`/api/clientes/${clienteId}/notas`, { method: 'POST', body: form })
}

/* ---------- Relatório ---------- */

export function gerarRelatorio(clienteId: number, periodo: { de?: string; ate?: string } = {}, signal?: AbortSignal) {
  const qs = new URLSearchParams()
  if (periodo.de) qs.set('de', periodo.de)
  if (periodo.ate) qs.set('ate', periodo.ate)
  const q = qs.toString()
  return request<Relatorio>(`/api/clientes/${clienteId}/relatorio${q ? `?${q}` : ''}`, { signal })
}

/* ---------- Classificação, cálculo 2027, painel e revisão ---------- */

/**
 * Classifica os itens da nota (XML → cache → IA, sempre dentro da tabela oficial) e recalcula a nota.
 * Itens já classificados não mudam. Sem IA configurada, responde 200 com itens pendentes e um aviso.
 */
export function classificarNota(id: number) {
  return request<ClassificacaoNota>(`/api/notas/${id}/classificar`, { method: 'POST' })
}

/** Recalcula 2027 para os itens já classificados da nota (calculadora oficial ou simplificada, com aviso). */
export function calcularNota(id: number) {
  return request<CalculoNota>(`/api/notas/${id}/calcular`, { method: 'POST' })
}

/** Painel da empresa: indicadores e comparativo hoje (PIS/Cofins) x 2027 (CBS/IBS/IS). Só lê o que já foi calculado. */
export function painelEmpresa(clienteId: number, periodo: { de?: string; ate?: string } = {}, signal?: AbortSignal) {
  const qs = new URLSearchParams({ limiteItens: '5', limiteFornecedores: '5' })
  if (periodo.de) qs.set('de', periodo.de)
  if (periodo.ate) qs.set('ate', periodo.ate)
  return request<Painel>(`/api/clientes/${clienteId}/dashboard?${qs}`, { signal })
}

/** Itens que precisam de revisão: sem classificação, não aceitos ou com confiança baixa. */
export function listarRevisao(clienteId: number, signal?: AbortSignal) {
  return request<Revisao>(`/api/clientes/${clienteId}/revisao`, { signal })
}

/** Opções de cClassTrib aceitas em NF-e; com NCM, as associadas a ele pela lista oficial vêm primeiro. */
export function opcoesClassificacao(ncm?: string | null, signal?: AbortSignal) {
  const q = ncm ? `?ncm=${encodeURIComponent(ncm)}` : ''
  return request<OpcaoClassificacao[]>(`/api/classificacoes/opcoes${q}`, { signal })
}

/** Aceita, corrige ou marca como uso e consumo a classificação de um item (recalcula as notas afetadas). */
export function revisarItem(itemId: number, corpo: RevisarItem) {
  return request<RevisaoResultado>(`/api/itens/${itemId}/classificacao`, json('PUT', corpo))
}
