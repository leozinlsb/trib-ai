import { json, request } from './client'
import { corpoNovaChave, type ChaveCriada, type ChaveResumo, type FormChave } from '../lib/chavesApi'

// Chaves da API pública (somente administrador; o backend confere). A chave completa só vem na emissão.

export function listarChaves(signal?: AbortSignal) {
  return request<ChaveResumo[]>('/api/admin/chaves-api', { signal })
}

export function emitirChave(form: FormChave) {
  return request<ChaveCriada>('/api/admin/chaves-api', json('POST', corpoNovaChave(form)))
}

/** Revogação definitiva e idempotente. */
export function revogarChave(id: number) {
  return request<ChaveResumo>(`/api/admin/chaves-api/${id}/revogar`, { method: 'POST' })
}
