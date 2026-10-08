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
