import type { SituacaoValidacao, StatusAnalise, Verificacao } from '../api/inteligenciaFiscal'

type Cor = 'green' | 'blue' | 'gray' | 'amber' | 'red'

/** Como cada status aparece para o usuário (sem termos técnicos sobre IA ou provedores). */
export const STATUS: Record<StatusAnalise, { rotulo: string; cor: Cor; descricao: string }> = {
  AGUARDANDO: { rotulo: 'Aguardando processamento', cor: 'gray', descricao: 'A análise está na fila e começa em instantes.' },
  INTERPRETANDO: { rotulo: 'Interpretando a mercadoria', cor: 'blue', descricao: 'Lendo a descrição e os documentos enviados.' },
  PESQUISANDO_NCM: { rotulo: 'Pesquisando códigos NCM', cor: 'blue', descricao: 'Buscando os códigos que podem se aplicar.' },
  AVALIANDO: { rotulo: 'Avaliando classificações', cor: 'blue', descricao: 'Comparando os códigos encontrados com a mercadoria.' },
  VALIDANDO: { rotulo: 'Validando informações fiscais', cor: 'blue', descricao: 'Conferindo vigência e regras aplicáveis.' },
  GERANDO_RELATORIO: { rotulo: 'Gerando relatório', cor: 'blue', descricao: 'Organizando o resultado e as referências.' },
  CONCLUIDA: { rotulo: 'Concluída', cor: 'green', descricao: 'O resultado está disponível.' },
  FALHA: { rotulo: 'Falha no processamento', cor: 'red', descricao: 'Não foi possível concluir a análise.' },
  INFORMACOES_INSUFICIENTES: {
    rotulo: 'Informações insuficientes',
    cor: 'amber',
    descricao: 'Faltam dados sobre a mercadoria para concluir a análise.',
  },
  AGUARDANDO_REVISAO: {
    rotulo: 'Aguardando revisão',
    cor: 'amber',
    descricao: 'O resultado precisa ser conferido por um especialista.',
  },
}

/** Situação das verificações automáticas. Nenhuma delas é homologação oficial. */
export const VALIDACAO: Record<SituacaoValidacao, { rotulo: string; cor: Cor; descricao: string }> = {
  VALIDADO_VERIFICACOES: {
    rotulo: 'Validado pelas verificações disponíveis',
    cor: 'green',
    descricao: 'Passou nas verificações automáticas. Não substitui a análise de um especialista nem é homologação oficial.',
  },
  PENDENTE_REVISAO: { rotulo: 'Pendente de revisão', cor: 'amber', descricao: 'Precisa ser conferido por um especialista.' },
  INFORMACOES_INSUFICIENTES: {
    rotulo: 'Informações insuficientes',
    cor: 'amber',
    descricao: 'Os dados disponíveis não permitem concluir as verificações.',
  },
  INCONSISTENCIA: { rotulo: 'Inconsistência identificada', cor: 'red', descricao: 'Há divergências que precisam de atenção.' },
}

export const VERIFICACAO: Record<Verificacao['resultado'], { rotulo: string; cor: Cor }> = {
  OK: { rotulo: 'Conferido', cor: 'green' },
  ALERTA: { rotulo: 'Atenção', cor: 'amber' },
  FALHA: { rotulo: 'Divergente', cor: 'red' },
  NAO_REALIZADA: { rotulo: 'Não realizada', cor: 'gray' },
}

/** "84713012" → "8471.30.12" */
export function fmtNcm(ncm: string | null | undefined) {
  if (!ncm) return '—'
  const d = ncm.replace(/\D/g, '')
  return d.length === 8 ? `${d.slice(0, 4)}.${d.slice(4, 6)}.${d.slice(6)}` : ncm
}
