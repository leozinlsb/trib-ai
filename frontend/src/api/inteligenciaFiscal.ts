/**
 * Inteligência Fiscal — CONTRATO PROPOSTO.
 *
 * Os endpoints abaixo AINDA NÃO EXISTEM no backend. Este arquivo define o contrato que o frontend espera
 * (detalhado em frontend/docs/inteligencia-fiscal-api.md). Toda a IA (Gemini), a pontuação (JEV), a busca NCM
 * e a validação fiscal acontecem no servidor: o navegador só envia dados e exibe o que voltar.
 *
 * Enquanto as rotas não existirem, o servidor responde 404 de "rota inexistente" e as telas mostram o estado
 * "serviço indisponível" (ver {@link servicoIndisponivel}). Nada aqui simula resultado.
 */
import { ApiError, json, request } from './client'

/* ---------- Tipos ---------- */

/** Etapas do processamento, na ordem em que acontecem. */
export const ETAPAS_PROCESSAMENTO = [
  'AGUARDANDO',
  'INTERPRETANDO',
  'PESQUISANDO_NCM',
  'AVALIANDO',
  'VALIDANDO',
  'GERANDO_RELATORIO',
  'CONCLUIDA',
] as const

export type EtapaProcessamento = (typeof ETAPAS_PROCESSAMENTO)[number]

/** Situações que encerram ou interrompem o fluxo normal. */
export type SituacaoEspecial = 'FALHA' | 'INFORMACOES_INSUFICIENTES' | 'AGUARDANDO_REVISAO'

export type StatusAnalise = EtapaProcessamento | SituacaoEspecial

/** Resultado das verificações automáticas. Nunca significa homologação pela Receita Federal. */
export type SituacaoValidacao =
  | 'VALIDADO_VERIFICACOES'
  | 'PENDENTE_REVISAO'
  | 'INFORMACOES_INSUFICIENTES'
  | 'INCONSISTENCIA'

/** Dados informados pelo usuário sobre a mercadoria. */
export interface MercadoriaEntrada {
  nome: string
  descricao: string
  composicao?: string
  finalidade?: string
  caracteristicas?: string
  /** NCM já usado pela empresa, se houver (8 dígitos) */
  ncmAtual?: string
}

export interface AnexoInfo {
  nome: string
  /** bytes */
  tamanho: number
  tipo: string
}

export interface AnaliseResumo {
  id: number
  clienteId: number
  mercadoria: string
  /** null enquanto não houver sugestão */
  ncmSugerida: string | null
  status: StatusAnalise
  /** ISO 8601 */
  criadaEm: string
  atualizadaEm: string
  relatorioDisponivel: boolean
}

export interface Pagina<T> {
  itens: T[]
  total: number
  pagina: number
  tamanho: number
}

export interface IndicadoresFiscais {
  total: number
  concluidas: number
  emProcessamento: number
  aguardandoRevisao: number
  /** encerradas com falha (IA indisponível, erro); ausente em backends antigos */
  falhas?: number
  informacoesInsuficientes?: number
}

/** Pontuação de compatibilidade (JEV), com a escala e o significado definidos pelo backend. */
export interface Pontuacao {
  valor: number
  /** ex.: "0 a 1" */
  escala: string
  /** texto explicando o que a pontuação mede (não é probabilidade de acerto) */
  significado: string
}

export interface Alternativa {
  ncm: string
  descricao: string
  /** avaliação textual produzida pela análise */
  avaliacao: string
  pontuacao?: Pontuacao
}

export interface Verificacao {
  nome: string
  resultado: 'OK' | 'ALERTA' | 'FALHA' | 'NAO_REALIZADA'
  detalhe?: string
}

export interface ValidacaoFiscal {
  situacao: SituacaoValidacao
  /** ex.: "Código vigente na TIPI" */
  situacaoNcm: string
  vigencia?: { inicio?: string; fim?: string | null }
  verificacoes: Verificacao[]
  regrasAplicaveis: string[]
  divergencias: string[]
  pendencias: string[]
}

export interface Fonte {
  titulo: string
  identificacao?: string
  /** data ou versão */
  versao?: string
  trecho?: string
  url?: string
}

export interface AnaliseDetalhe extends AnaliseResumo {
  entrada: MercadoriaEntrada
  anexos: AnexoInfo[]
  /** etapas já percorridas, com horário (para o acompanhamento) */
  historico: { status: StatusAnalise; em: string }[]
  resultado?: {
    ncm: string
    descricaoOficial: string
    situacaoValidacao: SituacaoValidacao
    analisadaEm: string
  }
  fundamentacao?: {
    caracteristicas: string[]
    motivos: string[]
    regrasConsideradas: string[]
    observacoes: string[]
    limitacoes: string[]
  }
  alternativas?: Alternativa[]
  validacao?: ValidacaoFiscal
  fontes?: Fonte[]
  /** motivo da falha ou das informações que faltam */
  mensagem?: string
  relatorio?: { disponivel: boolean; downloadUrl?: string }
  /** decisões de pessoas sobre a sugestão, em ordem; o resultado automático continua como evidência */
  revisoes?: RevisaoHumana[]
}

export interface FiltroAnalises {
  q?: string
  status?: StatusAnalise | ''
  /** AAAA-MM-DD */
  de?: string
  ate?: string
  pagina?: number
  tamanho?: number
}

/* ---------- Serviço (rotas propostas) ---------- */

const base = (clienteId: number) => `/api/clientes/${clienteId}/analises-fiscais`

export function listarAnalises(clienteId: number, filtro: FiltroAnalises = {}, signal?: AbortSignal) {
  const qs = new URLSearchParams()
  Object.entries(filtro).forEach(([k, v]) => {
    if (v !== undefined && v !== '') qs.set(k, String(v))
  })
  const q = qs.toString()
  return request<Pagina<AnaliseResumo>>(`${base(clienteId)}${q ? `?${q}` : ''}`, { signal })
}

export function indicadores(clienteId: number, signal?: AbortSignal) {
  return request<IndicadoresFiscais>(`${base(clienteId)}/indicadores`, { signal })
}

/** Multipart: parte "dados" (JSON da mercadoria) + partes "arquivos". Resposta 202 com a análise criada. */
export function iniciarAnalise(clienteId: number, dados: MercadoriaEntrada, arquivos: File[]) {
  const form = new FormData()
  form.append('dados', new Blob([JSON.stringify(dados)], { type: 'application/json' }))
  arquivos.forEach((f) => form.append('arquivos', f, f.name))
  return request<AnaliseResumo>(base(clienteId), { method: 'POST', body: form })
}

export function detalharAnalise(id: number, signal?: AbortSignal) {
  return request<AnaliseDetalhe>(`/api/analises-fiscais/${id}`, { signal })
}

/* ---------- Utilitários ---------- */

/**
 * true quando a rota não existe no servidor (serviço ainda não implementado). O 404 de "rota inexistente"
 * do Spring não tem o título "Recurso não encontrado" usado pelos erros de negócio (empresa/análise inexistente).
 */
/** Revisão humana registrada: ACEITA (manteve a sugestão) ou ALTERADA (escolheu outra NCM). */
export interface RevisaoHumana {
  decisao: 'ACEITA' | 'ALTERADA'
  ncm: string
  ncmSugerida?: string
  observacao?: string
  revisadaPor: string
  revisadaEm: string
}

/** Registra a decisão de quem revisou (PUT /api/analises-fiscais/{id}/revisao). Devolve o detalhe atualizado. */
export function revisarAnalise(id: number, dados: { ncm: string; observacao?: string }) {
  return request<AnaliseDetalhe>(`/api/analises-fiscais/${id}/revisao`, json('PUT', dados))
}

export function servicoIndisponivel(e: unknown) {
  if (!(e instanceof ApiError)) return false
  if (e.status === 501 || e.status === 405) return true
  return e.status === 404 && e.problem?.title !== 'Recurso não encontrado'
}

export const EM_ANDAMENTO: StatusAnalise[] = ['AGUARDANDO', 'INTERPRETANDO', 'PESQUISANDO_NCM', 'AVALIANDO', 'VALIDANDO', 'GERANDO_RELATORIO']

export function emAndamento(s: StatusAnalise) {
  return EM_ANDAMENTO.includes(s)
}
