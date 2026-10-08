import type { NotaDetalhe, NotaResumo } from '../api/types'

export type Periodo = 'semanal' | 'mensal'

export interface Ponto {
  /** chave do período: segunda-feira AAAA-MM-DD (semanal) ou AAAA-MM (mensal) */
  chave: string
  rotulo: string
  rotuloLongo: string
  saidas: number
  entradas: number
  qtdSaidas: number
  qtdEntradas: number
}

const DIA = 86_400_000

/** Datas da API são AAAA-MM-DD sem fuso: tratamos tudo em UTC para não mudar de dia. */
function utc(iso: string) {
  const [a, m, d] = iso.split('-').map(Number)
  return Date.UTC(a!, m! - 1, d!)
}

function isoDia(t: number) {
  return new Date(t).toISOString().slice(0, 10)
}

function segunda(iso: string) {
  const t = utc(iso)
  const dow = (new Date(t).getUTCDay() + 6) % 7 // 0 = segunda
  return t - dow * DIA
}

function ddmm(t: number) {
  const d = new Date(t)
  return `${String(d.getUTCDate()).padStart(2, '0')}/${String(d.getUTCMonth() + 1).padStart(2, '0')}`
}

const MESES = ['Jan', 'Fev', 'Mar', 'Abr', 'Mai', 'Jun', 'Jul', 'Ago', 'Set', 'Out', 'Nov', 'Dez']

/**
 * Valor e quantidade de notas por período, separados em saídas e entradas.
 * Períodos sem nota entram com zero, para o eixo do tempo ficar contínuo.
 */
export function serieTemporal(notas: NotaResumo[], periodo: Periodo, maxPontos = 16): Ponto[] {
  if (notas.length === 0) return []
  const mapa = new Map<string, Ponto>()

  const novo = (chave: string, rotulo: string, rotuloLongo: string): Ponto => ({
    chave, rotulo, rotuloLongo, saidas: 0, entradas: 0, qtdSaidas: 0, qtdEntradas: 0,
  })

  const datas = notas.map((n) => n.dataEmissao).sort()
  const ini = datas[0]!
  const fim = datas[datas.length - 1]!

  if (periodo === 'semanal') {
    for (let t = segunda(ini); t <= segunda(fim); t += 7 * DIA) {
      const k = isoDia(t)
      mapa.set(k, novo(k, ddmm(t), `Semana de ${ddmm(t)} a ${ddmm(t + 6 * DIA)}`))
    }
  } else {
    let [a, m] = ini.slice(0, 7).split('-').map(Number) as [number, number]
    const fimK = fim.slice(0, 7)
    for (;;) {
      const k = `${a}-${String(m).padStart(2, '0')}`
      mapa.set(k, novo(k, `${MESES[m - 1]}/${String(a).slice(2)}`, `${MESES[m - 1]} de ${a}`))
      if (k >= fimK) break
      m += 1
      if (m > 12) {
        m = 1
        a += 1
      }
    }
  }

  for (const n of notas) {
    const k = periodo === 'semanal' ? isoDia(segunda(n.dataEmissao)) : n.competencia
    const p = mapa.get(k)
    if (!p) continue
    if (n.tipo === 'SAIDA') {
      p.saidas += n.valorTotal
      p.qtdSaidas += 1
    } else {
      p.entradas += n.valorTotal
      p.qtdEntradas += 1
    }
  }
  return [...mapa.values()].slice(-maxPontos)
}

/** Ordem: emissão mais recente primeiro; empate pelo id (importação) mais recente. */
export function maisRecentes(notas: NotaResumo[]) {
  return [...notas].sort((a, b) => b.dataEmissao.localeCompare(a.dataEmissao) || b.id - a.id)
}

export function competencias(notas: NotaResumo[]) {
  return [...new Set(notas.map((n) => n.competencia))].sort().reverse()
}

export type StatusClassificacao = 'classificada' | 'parcial' | 'pendente'

/** O XML trouxe CST e cClassTrib no grupo IBS/CBS (o que o emitente informou, ainda não conferido). */
export function itemComCodigoNoXml(i: NotaDetalhe['itens'][number]) {
  const g = i.ibsCbsDestacado
  return !!(g && g.cst && g.cst.trim() && g.cClassTrib && g.cClassTrib.trim())
}

/**
 * O item tem classificação persistida no TribIA (XML, cache, IA, regra ou revisão manual).
 * B5: antes só o XML contava, e itens classificados pelo cache/IA apareciam como pendentes.
 * Fallback no XML enquanto a nota não foi processada (POST /api/notas/{id}/classificar).
 */
export function itemClassificado(i: NotaDetalhe['itens'][number]) {
  return i.classificacao != null || itemComCodigoNoXml(i)
}

/** Status da nota a partir da classificação de cada item (ver {@link itemClassificado}). */
export function statusClassificacao(n: NotaDetalhe): StatusClassificacao {
  const total = n.itens.length
  const ok = n.itens.filter(itemClassificado).length
  if (total > 0 && ok === total) return 'classificada'
  if (ok > 0) return 'parcial'
  return 'pendente'
}
