// Espelho dos DTOs do backend (br.com.tribia.dto). BigDecimal chega como number no JSON.

export type Regime = 'LUCRO_REAL' | 'LUCRO_PRESUMIDO'
export type TipoNota = 'ENTRADA' | 'SAIDA'

export interface Cliente {
  id: number
  cnpj: string
  razaoSocial: string
  nomeFantasia: string | null
  regime: Regime
  setor: string | null
  uf: string | null
  municipio: string | null
  codigoMunicipio: string | null
  email: string | null
  telefone: string | null
  responsavel: string | null
  observacoes: string | null
  /** false = desativada (exclusão lógica) */
  ativo: boolean
}

/** Corpo de cadastro/edição de empresa (ClienteForm). */
export interface ClienteForm {
  razaoSocial: string
  nomeFantasia: string
  cnpj: string
  regime: Regime | ''
  setor: string
  uf: string
  municipio: string
  codigoMunicipio: string
  email: string
  telefone: string
  responsavel: string
  observacoes: string
}

export type Papel = 'ADMIN' | 'EMPRESA'

export interface Usuario {
  id: number
  nome: string
  email: string
  papel: Papel
  /** empresa do usuário; null para ADMIN */
  clienteId: number | null
  clienteNome: string | null
  criadoEm: string
}

export interface ApuracaoPisCofins {
  debito: number
  credito: number
  liquido: number
  aPagar: number
  saldoCredor: number
}

export interface Relatorio {
  identificacao: string
  geradoEm: string
  empresa: Cliente
  periodoDe: string | null
  periodoAte: string | null
  situacao: 'SEM_DADOS' | 'PARCIAL' | 'COMPLETO'
  resumo: {
    notas: number
    entradas: number
    saidas: number
    itens: number
    itensClassificados: number
    valorEntradas: number
    valorSaidas: number
    contrapartes: number
  }
  apuracaoPisCofins: ApuracaoPisCofins
  competencias: { competencia: string; notas: number; valorEntradas: number; valorSaidas: number; apuracao: ApuracaoPisCofins }[]
  documentos: {
    id: number
    tipo: TipoNota
    numero: number | null
    serie: number | null
    dataEmissao: string
    competencia: string
    contraparteDocumento: string | null
    contraparteNome: string | null
    valorTotal: number
    itens: number
    itensClassificados: number
    pisCofinsDestacado: number
    pisCofinsApurado: number
  }[]
  contrapartes: { documento: string | null; nome: string | null; fornecedor: boolean; clienteFinal: boolean; notas: number; valor: number }[]
  observacoes: {
    codigo: string
    nivel: 'ATENCAO' | 'INFO'
    mensagem: string
    total: number
    referencias: { notaId: number; numero: number | null; item: number | null; descricao: string | null }[]
  }[]
}

export interface NotaResumo {
  id: number
  clienteId: number
  tipo: TipoNota
  chave: string
  numero: number | null
  serie: number | null
  /** AAAA-MM-DD */
  dataEmissao: string
  /** AAAA-MM */
  competencia: string
  contraparteCnpj: string | null
  contraparteNome: string | null
  valorTotal: number
  quantidadeItens: number
}

export interface IbsCbsDestacado {
  cst: string | null
  cClassTrib: string | null
  vBc: number | null
  pIbsUf: number | null
  vIbsUf: number | null
  pIbsMun: number | null
  vIbsMun: number | null
  vIbs: number | null
  pCbs: number | null
  vCbs: number | null
}

export interface Item {
  id: number
  nItem: number
  codigo: string | null
  descricao: string | null
  ncm: string | null
  cfop: string | null
  unidade: string | null
  quantidade: number | null
  valorUnitario: number | null
  valorTotal: number | null
  vIcms: number | null
  cstPisCofins: string | null
  vPis: number | null
  vCofins: number | null
  creditavel: boolean
  /** null quando a nota não trouxe o grupo IBS/CBS no item */
  ibsCbsDestacado: IbsCbsDestacado | null
  /** classificação persistida (XML, cache, IA, regra ou revisão); null enquanto o item não for classificado */
  classificacao: Classificacao | null
  /** cálculo de 2027 persistido; null enquanto a nota não for calculada ou o item estiver sem classificação */
  calculo: Calculo | null
}

/* ---------- Classificação e cálculo (reforma tributária) ---------- */

export type OrigemClassificacao = 'XML' | 'CACHE' | 'IA' | 'REGRA' | 'MANUAL'
export type RegimeTributario = 'INTEGRAL' | 'REDUZIDA' | 'ALIQUOTA_ZERO' | 'SEM_INCIDENCIA' | 'OUTRO'
export type OrigemCalculo = 'CALCULADORA' | 'SIMPLIFICADA'

/** ClassificacaoDto do backend. */
export interface Classificacao {
  cst: string
  cClassTrib: string
  /** nome oficial do cClassTrib */
  nomeCClassTrib: string | null
  regime: RegimeTributario
  /** ex.: "Redução de 60%" */
  descricaoRegime: string | null
  justificativa: string | null
  /** 0 a 1 */
  confianca: number | null
  origem: OrigemClassificacao
  aceita: boolean
  revisada: boolean
}

/** CalculoDto do backend (um item). */
export interface Calculo {
  natureza: 'DEBITO' | 'CREDITO'
  origemValores: OrigemCalculo
  vCbs: number | null
  vIbsUf: number | null
  vIbsMun: number | null
  vIs: number | null
  pCbs: number | null
  sujeitoIs: boolean
  impostoHoje: number | null
  imposto2027: number | null
  simulado: boolean
}

export interface Apuracao {
  debito: number
  credito: number
  /** débito - crédito (pode ser negativo) */
  liquido: number
  aPagar: number
  saldoCredor: number
}

/** Hoje (PIS/Cofins) x 2027 (CBS/IBS/IS). A chave "2027" vem assim do backend. */
export interface Comparativo {
  hoje: Apuracao
  '2027': Apuracao
  /** null quando hoje não há imposto a pagar */
  variacaoPct: number | null
}

/** Resultado de POST /api/notas/{id}/calcular. */
export interface CalculoNota {
  notaId: number
  origem: OrigemCalculo | null
  simulado: boolean
  aliquotaCbs: number | null
  itensCalculados: number
  itensPendentes: number[]
  avisos: string[]
  comparativo: Comparativo | null
}

/** Resultado de POST /api/notas/{id}/classificar (já com o recálculo da nota). */
export interface ClassificacaoNota {
  notaId: number
  totalItens: number
  classificados: number
  porOrigem: Partial<Record<OrigemClassificacao, number>>
  /** nItem dos itens ainda sem classificação */
  pendentes: number[]
  avisos: string[]
  calculo: CalculoNota | null
}

/* ---------- Painel do cliente (GET /api/clientes/{id}/dashboard) ---------- */

export interface Indicadores {
  faturamento: number
  compras: number
  liquidoHoje: number
  liquido2027: number
  variacaoPct: number | null
  credito2027: number
  saldoCredor: boolean
  pendentesRevisao: number
  icmsVendas: number | null
}

export interface ItemImpacto {
  descricao: string | null
  ncm: string | null
  cClassTrib: string | null
  regime: RegimeTributario | null
  produtos: number
  impostoHoje: number
  imposto2027: number
  /** imposto2027 - impostoHoje; positivo = aumenta o imposto */
  diferenca: number
}

export interface Painel {
  cliente: { id: number; nome: string; cnpj: string; regime: Regime }
  periodo: { de: string | null; ate: string | null }
  indicadores: Indicadores
  comparativo: Comparativo
  porMes: { competencia: string; faturamento: number; liquidoHoje: number; liquido2027: number }[]
  topItens: ItemImpacto[]
  topFornecedores: { cnpj: string | null; nome: string | null; compras: number; creditoHoje: number; credito2027: number }[]
  avisos: string[]
}

/* ---------- Revisão (GET /api/clientes/{id}/revisao e PUT /api/itens/{id}/classificacao) ---------- */

export type MotivoRevisao = 'SEM_CLASSIFICACAO' | 'NAO_ACEITA' | 'CONFIANCA_BAIXA'

export interface OpcaoClassificacao {
  cClassTrib: string
  cst: string
  nome: string
  regime: RegimeTributario
  descricaoRegime: string | null
  anexo: string | null
  /** benefício de anexo: só vale para NCMs da lista oficial */
  exigeNcmNaLista: boolean
  /** a lista oficial associa este código ao NCM do item */
  sugeridaPeloNcm: boolean
}

export interface ItemRevisao {
  itemId: number
  notaId: number
  notaNumero: number | null
  tipo: TipoNota
  competencia: string
  contraparteNome: string | null
  nItem: number
  codigo: string | null
  descricao: string | null
  ncm: string | null
  valorTotal: number | null
  creditavel: boolean
  classificacao: Classificacao | null
  motivos: MotivoRevisao[]
  opcoesSugeridas: OpcaoClassificacao[]
}

export interface Revisao {
  clienteId: number
  confiancaMinima: number
  total: number
  itens: ItemRevisao[]
}

/** Corpo do PUT /api/itens/{id}/classificacao: informe ao menos aceitar, cClassTrib ou creditavel. */
export interface RevisarItem {
  aceitar?: boolean
  cst?: string
  cClassTrib?: string
  justificativa?: string
  creditavel?: boolean
  /** padrão no backend: true (aplica aos itens idênticos ainda não revisados) */
  aplicarAosIguais?: boolean
}

export interface RevisaoResultado {
  item: Item
  itensAtualizados: number
  notasRecalculadas: number[]
  avisos: string[]
}

export interface NotaDetalhe {
  id: number
  clienteId: number
  tipo: TipoNota
  chave: string
  numero: number | null
  serie: number | null
  dataEmissao: string
  competencia: string
  emitenteCnpj: string | null
  emitenteNome: string | null
  destinatarioDocumento: string | null
  destinatarioNome: string | null
  contraparteCnpj: string | null
  contraparteNome: string | null
  valorProdutos: number | null
  valorTotal: number
  itens: Item[]
}

export interface Rejeicao {
  arquivo: string
  /** 422 inválida, 409 duplicada */
  status: number
  motivo: string
}

export interface UploadResultado {
  importadas: NotaResumo[]
  rejeitadas: Rejeicao[]
}

/** Corpo de erro do backend (RFC 9457). O campo a exibir é "detail". */
export interface ProblemDetail {
  type?: string
  title?: string
  status?: number
  detail?: string
  instance?: string
  rejeitadas?: Rejeicao[]
  /** erros de validação por campo */
  campos?: Record<string, string>
}
