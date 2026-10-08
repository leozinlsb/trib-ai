import type { Cliente, Regime, TipoNota } from '../api/types'

const moeda = new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' })
const numero = new Intl.NumberFormat('pt-BR')
const compacto = new Intl.NumberFormat('pt-BR', { notation: 'compact', maximumFractionDigits: 1 })

export const fmtMoeda = (v: number | null | undefined) => (v == null ? '—' : moeda.format(v))
export const fmtNumero = (v: number | null | undefined, casas?: number) =>
  v == null
    ? '—'
    : casas == null
      ? numero.format(v)
      : v.toLocaleString('pt-BR', { minimumFractionDigits: casas, maximumFractionDigits: casas })
export const fmtCompacto = (v: number) => compacto.format(v)
export const fmtMoedaCompacta = (v: number) => (v === 0 ? 'R$ 0' : `R$ ${compacto.format(v)}`)

/** "2026-08-05" → "05/08/2026" sem passar por Date (evita deslocamento de fuso). */
export function fmtData(iso: string | null | undefined) {
  if (!iso) return '—'
  const [a, m, d] = iso.slice(0, 10).split('-')
  return `${d}/${m}/${a}`
}

const MESES = ['jan', 'fev', 'mar', 'abr', 'mai', 'jun', 'jul', 'ago', 'set', 'out', 'nov', 'dez']
const MESES_LONGOS = [
  'janeiro', 'fevereiro', 'março', 'abril', 'maio', 'junho',
  'julho', 'agosto', 'setembro', 'outubro', 'novembro', 'dezembro',
]

/** "2026-08" → "ago/2026" */
export function fmtCompetencia(c: string, longo = false) {
  const [a, m] = c.split('-')
  const i = Number(m) - 1
  return longo ? `${MESES_LONGOS[i]} de ${a}` : `${MESES[i]}/${a}`
}

export function fmtDataHora(iso: string) {
  const d = new Date(iso)
  return d.toLocaleString('pt-BR', { dateStyle: 'short', timeStyle: 'short' })
}

export function fmtCnpj(doc: string | null | undefined) {
  if (!doc) return '—'
  if (doc.length === 14) return doc.replace(/^(\d{2})(\d{3})(\d{3})(\d{4})(\d{2})$/, '$1.$2.$3/$4-$5')
  if (doc.length === 11) return doc.replace(/^(\d{3})(\d{3})(\d{3})(\d{2})$/, '$1.$2.$3-$4')
  return doc
}

export function fmtChave(chave: string) {
  return chave.replace(/(\d{4})(?=\d)/g, '$1 ')
}

export const REGIME_LABEL: Record<Regime, string> = {
  LUCRO_REAL: 'Lucro Real',
  LUCRO_PRESUMIDO: 'Lucro Presumido',
}

export const TIPO_LABEL: Record<TipoNota, string> = {
  ENTRADA: 'Entrada',
  SAIDA: 'Saída',
}

export function nomeCliente(c: Cliente | undefined | null) {
  if (!c) return '—'
  return c.nomeFantasia || c.razaoSocial
}

export function tituloNota(n: { numero: number | null; serie: number | null }) {
  return `NF-e ${n.numero ?? 's/n'}${n.serie != null ? ` · série ${n.serie}` : ''}`
}

/** "MERCADO FICTICIO BOM PRECO LTDA" → "Mercado Ficticio Bom Preco Ltda" */
export function capitalizar(s: string | null | undefined) {
  if (!s) return '—'
  if (s !== s.toUpperCase()) return s
  return s
    .toLowerCase()
    .replace(/(^|\s|[-/(])([\p{L}])/gu, (_, sep: string, ch: string) => sep + ch.toUpperCase())
    .replace(/\b(De|Da|Do|Das|Dos|E)\b/g, (w) => w.toLowerCase())
}

export function iniciais(nome: string) {
  return nome
    .split(/\s+/)
    .filter((p) => p.length > 2)
    .slice(0, 2)
    .map((p) => p[0]!.toUpperCase())
    .join('')
}

export function fmtBytes(b: number) {
  if (b < 1024) return `${b} B`
  if (b < 1024 * 1024) return `${(b / 1024).toFixed(1).replace('.', ',')} KB`
  return `${(b / 1024 / 1024).toFixed(1).replace('.', ',')} MB`
}

/** Normaliza para busca: minúsculas, sem acentos, sem pontuação de CNPJ. */
export function normalizar(s: string) {
  return s
    .normalize('NFD')
    .replace(/[̀-ͯ]/g, '')
    .toLowerCase()
}
