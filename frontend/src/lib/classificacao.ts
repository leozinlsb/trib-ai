import type { Classificacao, OrigemClassificacao, RegimeTributario } from '../api/types'
import type { CorBadge } from '../components/ui'
import { fmtNumero } from './format'

/** De onde veio a classificação: é o "selo de origem" de cada resultado. */
export const ORIGEM_LABEL: Record<OrigemClassificacao, { rotulo: string; dica: string }> = {
  XML: { rotulo: 'XML', dica: 'Informada pelo emitente no grupo IBS/CBS da nota (conferida contra a tabela oficial)' },
  CACHE: { rotulo: 'Cache', dica: 'Reaproveitada de um produto idêntico (mesmo NCM e descrição) já classificado' },
  IA: { rotulo: 'IA', dica: 'Sugerida pela IA entre as opções da tabela oficial; precisa da revisão do contador' },
  REGRA: { rotulo: 'Regra', dica: 'Definida por regra a partir da lista oficial NCM × cClassTrib' },
  MANUAL: { rotulo: 'Revisada', dica: 'Aceita ou corrigida pelo contador na revisão' },
}

export const REGIME_TRIB_LABEL: Record<RegimeTributario, string> = {
  INTEGRAL: 'Tributação integral',
  REDUZIDA: 'Alíquota reduzida',
  ALIQUOTA_ZERO: 'Alíquota zero',
  SEM_INCIDENCIA: 'Sem incidência',
  OUTRO: 'Regime específico',
}

export function fmtConfianca(c: number | null | undefined) {
  return c == null ? '—' : `${fmtNumero(c * 100, 0)}%`
}

/** Verde: aceita/revisada; azul: automática confiável; âmbar: precisa de revisão. */
export function corClassificacao(c: Classificacao, confiancaMinima = 0.7): CorBadge {
  if (c.revisada || c.aceita) return 'green'
  if (c.confianca != null && c.confianca < confiancaMinima) return 'amber'
  return 'blue'
}
