type Secao = 'documentos' | 'alertas' | 'revisao' | 'analises' | 'configuracoes' | 'inteligencia-fiscal'

/**
 * Inteligência Fiscal (sugestão de NCM): ligada por padrão desde que o backend ganhou os endpoints
 * (/api/clientes/{id}/analises-fiscais). Para esconder do menu: VITE_INTELIGENCIA_FISCAL=false no frontend/.env.
 */
export const INTELIGENCIA_FISCAL_ATIVA = import.meta.env.VITE_INTELIGENCIA_FISCAL !== 'false'

/** Caminhos do ambiente de uma empresa. */
export function rotaEmpresa(id: number, sub?: Secao | `${Secao}/${string}`) {
  return `/dashboard/empresas/${id}${sub ? `/${sub}` : ''}`
}

export function rotaNota(empresaId: number, notaId: number) {
  return rotaEmpresa(empresaId, `documentos/${notaId}`)
}

export function rotaAnaliseFiscal(empresaId: number, analiseId?: number | 'nova' | 'exemplo') {
  return rotaEmpresa(empresaId, analiseId != null ? `inteligencia-fiscal/${analiseId}` : 'inteligencia-fiscal')
}

/**
 * Troca a empresa mantendo a seção atual (ex.: de /empresas/1/documentos para /empresas/2/documentos).
 * Itens específicos (uma nota, uma análise) não existem na outra empresa: volta para a lista da seção.
 */
export function trocarEmpresaNoCaminho(pathname: string, novoId: number) {
  const m = pathname.match(/^\/dashboard\/empresas\/\d+(\/(documentos|alertas|revisao|analises|configuracoes|inteligencia-fiscal))?/)
  const secao = m?.[2] as Secao | undefined
  return rotaEmpresa(novoId, secao)
}
