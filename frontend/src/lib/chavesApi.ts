/**
 * Lógica pura da tela de integrações (chaves da API pública): rótulos dos escopos, validação do formulário, corpo da
 * emissão e exemplo de uso. Sem React nem chamadas de rede, para poder ser testada em scripts/chavesApi.test.ts.
 */

export type EscopoApi = 'ANALISES_CRIAR' | 'ANALISES_LER' | 'NOTAS_ENVIAR' | 'NOTAS_LER' | 'CLASSIFICAR' | 'CALCULAR'

export interface InfoEscopo {
  id: EscopoApi
  rotulo: string
  descricao: string
  /** gasta a cota de IA da chave */
  usaIa: boolean
}

/** Na ordem em que aparecem no formulário. Os ids são os do backend (EscopoApi). */
export const ESCOPOS: InfoEscopo[] = [
  { id: 'NOTAS_ENVIAR', rotulo: 'Enviar notas fiscais', usaIa: true,
    descricao: 'Recebe o XML da NF-e, classifica os itens e calcula 2027.' },
  { id: 'NOTAS_LER', rotulo: 'Consultar notas e comparativo', usaIa: false,
    descricao: 'Lê as notas enviadas, os itens classificados e o comparativo hoje × 2027.' },
  { id: 'CLASSIFICAR', rotulo: 'Classificar produtos', usaIa: true,
    descricao: 'Sugere CST e cClassTrib de produtos avulsos (sem nota).' },
  { id: 'CALCULAR', rotulo: 'Simular o cálculo de 2027', usaIa: false,
    descricao: 'Calcula CBS, IBS e IS de itens avulsos, sem gravar nada.' },
  { id: 'ANALISES_CRIAR', rotulo: 'Analisar NCM de mercadorias', usaIa: true,
    descricao: 'Sugere a NCM de uma mercadoria a partir da descrição.' },
  { id: 'ANALISES_LER', rotulo: 'Consultar análises de NCM', usaIa: false,
    descricao: 'Lê o resultado e a lista das análises de NCM.' },
]

export const TODOS_OS_ESCOPOS: EscopoApi[] = ESCOPOS.map((e) => e.id)

export type SituacaoChave = 'ATIVA' | 'REVOGADA' | 'EXPIRADA'

export const COR_SITUACAO: Record<SituacaoChave, 'green' | 'gray' | 'amber'> = {
  ATIVA: 'green', REVOGADA: 'gray', EXPIRADA: 'amber',
}

export const ROTULO_SITUACAO: Record<SituacaoChave, string> = {
  ATIVA: 'Ativa', REVOGADA: 'Revogada', EXPIRADA: 'Expirada',
}

/** ChaveResumo do backend (nunca traz o segredo). */
export interface ChaveResumo {
  id: number
  prefixo: string
  nomeIntegrador: string
  clienteId: number
  empresa: string
  escopos: EscopoApi[]
  situacao: SituacaoChave
  criadaEm: string
  criadaPor: string
  expiraEm: string | null
  revogadaEm: string | null
  revogadaPor: string | null
  ultimoUsoEm: string | null
  requisicoesPorMinuto: number | null
  cotaDiariaAnalises: number | null
  maxAnalisesSimultaneas: number | null
  cotaDiariaItensIa: number | null
  analisesHoje: number
  itensIaHoje: number
}

/** ChaveCriada: a chave completa só existe nesta resposta. */
export interface ChaveCriada {
  chave: string
  aviso: string
  dados: ChaveResumo
}

/** Campos do formulário, como texto (os numéricos são opcionais: vazio = padrão do servidor). */
export interface FormChave {
  clienteId: string
  nomeIntegrador: string
  escopos: EscopoApi[]
  validadeDias: string
  requisicoesPorMinuto: string
  cotaDiariaAnalises: string
  maxAnalisesSimultaneas: string
  cotaDiariaItensIa: string
}

export const FORM_CHAVE_VAZIO: FormChave = {
  clienteId: '',
  nomeIntegrador: '',
  escopos: [...TODOS_OS_ESCOPOS],
  validadeDias: '',
  requisicoesPorMinuto: '',
  cotaDiariaAnalises: '',
  maxAnalisesSimultaneas: '',
  cotaDiariaItensIa: '',
}

/** Limites do backend (CriarChaveForm); a validade máxima padrão é de 365 dias. */
const LIMITES_NUMERICOS: { campo: keyof FormChave; rotulo: string; min: number; max: number }[] = [
  { campo: 'validadeDias', rotulo: 'Validade', min: 1, max: 3650 },
  { campo: 'requisicoesPorMinuto', rotulo: 'Requisições por minuto', min: 1, max: 10_000 },
  { campo: 'cotaDiariaAnalises', rotulo: 'Análises de NCM por dia', min: 1, max: 100_000 },
  { campo: 'maxAnalisesSimultaneas', rotulo: 'Processamentos simultâneos', min: 1, max: 100 },
  { campo: 'cotaDiariaItensIa', rotulo: 'Itens para a IA por dia', min: 1, max: 1_000_000 },
]

export type ErrosFormChave = Partial<Record<keyof FormChave, string>>

export function validarFormChave(f: FormChave): ErrosFormChave {
  const erros: ErrosFormChave = {}
  if (!f.clienteId) erros.clienteId = 'Escolha a empresa.'
  const nome = f.nomeIntegrador.trim()
  if (!nome) erros.nomeIntegrador = 'Informe o nome do integrador.'
  else if (nome.length > 100) erros.nomeIntegrador = 'Use até 100 caracteres.'
  if (f.escopos.length === 0) erros.escopos = 'Marque ao menos uma permissão.'
  for (const l of LIMITES_NUMERICOS) {
    const texto = f[l.campo] as string
    if (texto.trim() === '') continue
    const n = Number(texto)
    if (!Number.isInteger(n) || n < l.min || n > l.max) {
      erros[l.campo] = `${l.rotulo}: número inteiro de ${l.min} a ${l.max.toLocaleString('pt-BR')}.`
    }
  }
  return erros
}

/** Corpo de POST /api/admin/chaves-api: campos vazios são omitidos (o servidor usa os padrões). */
export function corpoNovaChave(f: FormChave) {
  const numero = (t: string) => (t.trim() === '' ? undefined : Number(t))
  return {
    clienteId: Number(f.clienteId),
    nomeIntegrador: f.nomeIntegrador.trim(),
    escopos: f.escopos,
    validadeDias: numero(f.validadeDias),
    requisicoesPorMinuto: numero(f.requisicoesPorMinuto),
    cotaDiariaAnalises: numero(f.cotaDiariaAnalises),
    maxAnalisesSimultaneas: numero(f.maxAnalisesSimultaneas),
    cotaDiariaItensIa: numero(f.cotaDiariaItensIa),
  }
}

/** Resumo curto das permissões para a lista ("Todas" ou os rótulos). */
export function resumoEscopos(escopos: EscopoApi[]) {
  if (TODOS_OS_ESCOPOS.every((e) => escopos.includes(e))) return 'Todas as permissões'
  return ESCOPOS.filter((e) => escopos.includes(e.id)).map((e) => e.rotulo).join(' · ') || 'Nenhuma'
}

/** Exemplo de chamada para o integrador. A chave nunca vai no texto: só o nome da variável. */
export function exemploDeUso(baseUrl: string) {
  const base = baseUrl.replace(/\/$/, '')
  return [
    `# a chave fica numa variável de ambiente do seu sistema, nunca no código`,
    `curl -s "${base}/api/v1/uso" -H "X-API-Key: $TRIBIA_API_KEY"`,
    ``,
    `curl -s -X POST "${base}/api/v1/notas" \\`,
    `  -H "X-API-Key: $TRIBIA_API_KEY" -H "Idempotency-Key: <um-uuid-por-nota>" \\`,
    `  -H "Content-Type: application/json" \\`,
    `  -d '{"referenciaExterna":"NF-123","xml":"<conteúdo do XML da NF-e>"}'`,
  ].join('\n')
}

/** Rotas públicas, para a tabela do guia. */
export const ROTAS_PUBLICAS: { metodo: string; rota: string; escopo: EscopoApi | 'QUALQUER_LEITURA'; descricao: string }[] = [
  { metodo: 'POST', rota: '/api/v1/notas', escopo: 'NOTAS_ENVIAR', descricao: 'Envia o XML de uma NF-e (resposta 202; consulte até finalizada).' },
  { metodo: 'GET', rota: '/api/v1/notas/{id}', escopo: 'NOTAS_LER', descricao: 'Itens classificados, cálculo de 2027 e comparativo da nota.' },
  { metodo: 'GET', rota: '/api/v1/comparativo', escopo: 'NOTAS_LER', descricao: 'Comparativo hoje × 2027 da empresa.' },
  { metodo: 'POST', rota: '/api/v1/classificacoes', escopo: 'CLASSIFICAR', descricao: 'Classifica até 50 produtos avulsos (resposta 202; consulte até finalizada).' },
  { metodo: 'POST', rota: '/api/v1/calculos/simular', escopo: 'CALCULAR', descricao: 'Simula CBS, IBS e IS de 2027 de até 100 itens.' },
  { metodo: 'POST', rota: '/api/v1/analises', escopo: 'ANALISES_CRIAR', descricao: 'Envia uma mercadoria para sugestão de NCM.' },
  { metodo: 'GET', rota: '/api/v1/uso', escopo: 'QUALQUER_LEITURA', descricao: 'Limites e consumo do dia da própria chave.' },
]
