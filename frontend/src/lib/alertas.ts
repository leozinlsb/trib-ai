import type { Classificacao, Item, NotaDetalhe, OpcaoClassificacao, RegimeTributario } from '../api/types'

/**
 * Motor de alertas (Etapa 3): confere o que as notas dizem contra a tabela oficial de cClassTrib e a lista de
 * NCMs de cada benefício (GET /api/classificacoes/opcoes?ncm=), e estima quanto dinheiro está em jogo.
 *
 * Tudo aqui é estimativa para orientar a revisão humana, nunca uma conclusão fiscal:
 * - "valor em jogo" = base estimada do item × alíquota de referência de 2027 × diferença de carga entre códigos;
 * - a base segue a regra do backend (valor do item sem ICMS, PIS e Cofins);
 * - a alíquota de referência sai dos próprios cálculos de 2027 da empresa (itens de tributação integral) e, sem
 *   eles, da estimativa configurada no backend (CBS 9,43% + IBS 0,1%).
 */

export type TipoAlerta =
  | 'BENEFICIO_NAO_APLICADO'
  | 'BENEFICIO_FORA_DA_LISTA'
  | 'CODIGO_INEXISTENTE'
  | 'DIVERGENCIA_CONFIRMADA'
  | 'FORNECEDOR_SIMPLES'
  | 'SEM_IBS_CBS'
  | 'REVISAO_PENDENTE'

/** oportunidade: dinheiro que a empresa pode recuperar; risco: exposição a autuação; conformidade: pendência. */
export type CategoriaAlerta = 'oportunidade' | 'risco' | 'conformidade'
export type Severidade = 'alta' | 'media' | 'baixa'

export interface Alerta {
  id: string
  tipo: TipoAlerta
  categoria: CategoriaAlerta
  severidade: Severidade
  /** frase curta do que foi encontrado */
  titulo: string
  /** o que fazer */
  recomendacao: string
  notaId?: number
  notaNumero?: number | null
  contraparte?: string | null
  /** a nota é da própria empresa (venda emitida por ela) */
  notaPropria?: boolean
  itemId?: number
  produto?: string | null
  ncm?: string | null
  /** código informado na nota */
  codigoNota?: string | null
  /** código que a lista oficial associa ao NCM (quando houver um melhor candidato) */
  sugestao?: OpcaoClassificacao | null
  /** códigos que a lista oficial associa ao NCM, para a pessoa escolher na correção */
  candidatas?: OpcaoClassificacao[]
  /** estimativa em R$ do que está em jogo; null quando não dá para estimar */
  impacto: number | null
  /** alertas agregados (por fornecedor, por empresa): quantos itens/notas */
  quantidade?: number
}

export interface InfoTipo {
  rotulo: string
  descricao: string
}

export const TIPOS_ALERTA: Record<TipoAlerta, InfoTipo> = {
  BENEFICIO_NAO_APLICADO: {
    rotulo: 'Benefício não aplicado',
    descricao: 'A nota usa tributação maior do que a que a lista oficial associa ao NCM do produto.',
  },
  BENEFICIO_FORA_DA_LISTA: {
    rotulo: 'Benefício fora da lista do NCM',
    descricao: 'A nota aplica redução ou alíquota zero que só vale para NCMs da lista oficial, e este NCM não está nela.',
  },
  CODIGO_INEXISTENTE: {
    rotulo: 'Código inválido',
    descricao: 'O par CST/cClassTrib informado na nota não confere com a tabela oficial para NF-e.',
  },
  DIVERGENCIA_CONFIRMADA: {
    rotulo: 'Divergência confirmada na revisão',
    descricao: 'A revisão definiu um código diferente do que veio na nota: a nota precisa ser corrigida na origem.',
  },
  FORNECEDOR_SIMPLES: {
    rotulo: 'Crédito limitado (fornecedor do Simples)',
    descricao: 'Compras de fornecedor do Simples Nacional dão crédito limitado ao que ele recolheu no Simples.',
  },
  SEM_IBS_CBS: {
    rotulo: 'Nota sem IBS/CBS',
    descricao: 'Os itens não trazem o grupo IBS/CBS (CST e cClassTrib) da reforma.',
  },
  REVISAO_PENDENTE: {
    rotulo: 'Itens aguardando revisão',
    descricao: 'Sem classificação, não aceitos ou com confiança baixa: os valores de 2027 deles ainda não são confiáveis.',
  },
}

export const CATEGORIAS: Record<CategoriaAlerta, { rotulo: string; dica: string }> = {
  oportunidade: { rotulo: 'Oportunidades', dica: 'Imposto ou crédito que a empresa pode recuperar' },
  risco: { rotulo: 'Riscos fiscais', dica: 'Pontos que podem gerar cobrança ou glosa' },
  conformidade: { rotulo: 'Pendências', dica: 'Cadastro, leiaute e revisão' },
}

/** Estimativa do backend (application.properties): CBS 9,43% + IBS 0,05% + 0,05%. */
export const ALIQUOTA_REFERENCIA_PADRAO = 0.0953

/**
 * Códigos que dependem de quem compra (produtor rural, administração pública, Zona Franca...). Mesma lista de
 * tribia.classificacao.codigos-por-adquirente no backend: a nota sozinha não basta para sugeri-los.
 */
const CODIGOS_POR_ADQUIRENTE = new Set([
  '200001', '200002', '200005', '200006', '200008', '200010', '200011', '200012', '200015', '200020', '200022',
  '200023', '200024', '200038', '200043', '200044', '515001',
])

/** CRT 1 (Simples), 2 (Simples, excesso de sublimite) e 4 (MEI). */
const CRT_SIMPLES = new Set([1, 2, 4])

/** Fração da carga integral que o regime mantém: integral 1, redução de 60% 0,4, alíquota zero 0. */
export function fatorRegime(regime: RegimeTributario | null | undefined, descricaoRegime?: string | null): number | null {
  switch (regime) {
    case 'INTEGRAL':
      return 1
    case 'ALIQUOTA_ZERO':
    case 'SEM_INCIDENCIA':
      return 0
    case 'REDUZIDA': {
      const m = descricaoRegime?.match(/(\d+(?:[.,]\d+)?)\s*%/)
      if (!m?.[1]) return null
      const pct = Number(m[1].replace(',', '.'))
      return Number.isFinite(pct) ? Math.min(1, Math.max(0, 1 - pct / 100)) : null
    }
    default:
      return null
  }
}

const fatorDe = (c: Pick<Classificacao, 'regime' | 'descricaoRegime'> | Pick<OpcaoClassificacao, 'regime' | 'descricaoRegime'>) =>
  fatorRegime(c.regime, c.descricaoRegime)

/** Base de 2026–2032: valor do item sem ICMS, PIS e Cofins (LC 214, art. 12, § 2º, V — regra do backend). */
export function baseEstimada(i: Item) {
  return Math.max(0, (i.valorTotal ?? 0) - (i.vIcms ?? 0) - (i.vPis ?? 0) - (i.vCofins ?? 0))
}

/** Mediana de imposto2027/base nos itens de tributação integral já calculados; sem eles, a estimativa padrão. */
export function aliquotaReferencia(notas: NotaDetalhe[]) {
  const taxas: number[] = []
  for (const n of notas) {
    for (const i of n.itens) {
      if (i.classificacao?.regime !== 'INTEGRAL' || i.calculo?.imposto2027 == null || i.calculo.sujeitoIs) continue
      const base = baseEstimada(i)
      if (base > 0) taxas.push(Math.abs(i.calculo.imposto2027) / base)
    }
  }
  if (!taxas.length) return { aliquota: ALIQUOTA_REFERENCIA_PADRAO, origem: 'padrao' as const }
  taxas.sort((a, b) => a - b)
  const meio = Math.floor(taxas.length / 2)
  const mediana = taxas.length % 2 ? taxas[meio]! : (taxas[meio - 1]! + taxas[meio]!) / 2
  const plausivel = mediana > 0.01 && mediana < 0.4
  return plausivel
    ? { aliquota: mediana, origem: 'calculos' as const }
    : { aliquota: ALIQUOTA_REFERENCIA_PADRAO, origem: 'padrao' as const }
}

const somenteDigitos = (v: string) => v.replace(/\D/g, '')
const arred = (v: number) => Math.round(v * 100) / 100

function precisaRevisao(c: Classificacao | null, confiancaMinima: number) {
  if (!c) return true
  if (c.revisada) return false
  return !c.aceita || (c.confianca ?? 0) < confiancaMinima
}

const rotuloCodigo = (o: OpcaoClassificacao) => `${o.cClassTrib} (${o.descricaoRegime ?? o.nome})`

export interface Entrada {
  /** CNPJ da empresa analisada: separa as notas que ela emitiu das de terceiros */
  cnpjEmpresa: string
  notas: NotaDetalhe[]
  /** opções da tabela oficial por NCM (chave '' = item sem NCM) */
  opcoesPorNcm: ReadonlyMap<string, OpcaoClassificacao[]>
  confiancaMinima?: number
}

export function gerarAlertas({ cnpjEmpresa, notas, opcoesPorNcm, confiancaMinima = 0.7 }: Entrada) {
  const { aliquota, origem } = aliquotaReferencia(notas)
  const cnpj = somenteDigitos(cnpjEmpresa)
  const alertas: Alerta[] = []
  const simples = new Map<string, Alerta>()
  const semGrupoCompras = new Map<string, Alerta>()
  let pendentesRevisao = 0

  const emJogo = (i: Item, diferencaFator: number) => arred(baseEstimada(i) * aliquota * Math.abs(diferencaFator))

  for (const n of notas) {
    const propria = !!n.emitenteCnpj && somenteDigitos(n.emitenteCnpj) === cnpj
    const compra = n.operacao ? n.operacao === 'COMPRA' : n.tipo === 'ENTRADA' && !propria
    const venda = n.operacao ? n.operacao === 'VENDA' : n.tipo === 'SAIDA' && propria
    const base = { notaId: n.id, notaNumero: n.numero, contraparte: n.contraparteNome, notaPropria: propria }
    let itensSemGrupo = 0

    for (const i of n.itens) {
      const c = i.classificacao
      if (precisaRevisao(c, confiancaMinima)) pendentesRevisao++

      const xml = i.ibsCbsDestacado
      const codigo = xml?.cClassTrib?.trim() || null
      if (!codigo) {
        itensSemGrupo++
      } else {
        const lista = opcoesPorNcm.get(i.ncm ?? '')
        const doItem = { ...base, itemId: i.id, produto: i.descricao, ncm: i.ncm, codigoNota: codigo }
        const opcao = lista?.find((o) => o.cClassTrib === codigo)

        if (c?.revisada && c.cClassTrib !== codigo) {
          // a pessoa já decidiu: o alerta passa a ser "corrigir a nota na origem"
          const fNota = opcao ? fatorDe(opcao) : null
          const fRev = fatorDe(c)
          const notaCobraMais = fNota != null && fRev != null && fNota > fRev
          alertas.push({
            id: `div-${i.id}`,
            tipo: 'DIVERGENCIA_CONFIRMADA',
            categoria: notaCobraMais ? 'oportunidade' : 'risco',
            severidade: 'alta',
            titulo: `Nota com ${codigo}; revisão definiu ${c.cClassTrib} (${c.descricaoRegime ?? c.nomeCClassTrib ?? c.regime}).`,
            recomendacao: propria
              ? 'Corrija o cadastro do produto no emissor e avalie nota complementar ou de ajuste.'
              : 'Peça ao fornecedor a correção da classificação (carta de correção não altera valores: pode exigir nova nota).',
            ...doItem,
            impacto: fNota != null && fRev != null ? emJogo(i, fNota - fRev) : null,
          })
        } else if (lista && !opcao && c && !(c.origem === 'XML' && c.cClassTrib === codigo)) {
          // o backend só descarta o código da nota quando o par CST/cClassTrib não confere com a tabela oficial;
          // códigos válidos sem carga comparável (diferimento, suspensão) ficam fora das opções e não são alerta
          alertas.push({
            id: `inx-${i.id}`,
            tipo: 'CODIGO_INEXISTENTE',
            categoria: 'risco',
            severidade: 'alta',
            titulo: `CST ${xml?.cst ?? '—'} / cClassTrib ${codigo} não confere com a tabela oficial para NF-e.`,
            recomendacao: propria
              ? 'Corrija o código no emissor: a nota pode ser rejeitada quando a validação for obrigatória.'
              : 'Avise o fornecedor. O TribIA ignorou o código e classificou o item por conta própria.',
            ...doItem,
            impacto: null,
          })
        } else if (opcao) {
          const fNota = fatorDe(opcao)
          // candidatas: associadas ao NCM, que não dependem do comprador e cuja carga dá para comparar
          const sugeridas = (lista ?? []).filter(
            (o) => o.sugeridaPeloNcm && !CODIGOS_POR_ADQUIRENTE.has(o.cClassTrib) && fatorDe(o) != null,
          )
          if (opcao.exigeNcmNaLista && !opcao.sugeridaPeloNcm) {
            const integral = lista?.find((o) => o.cClassTrib === '000001') ?? lista?.find((o) => o.regime === 'INTEGRAL') ?? null
            const alternativa = sugeridas[0] ?? integral
            const fAlt = alternativa ? fatorDe(alternativa) : 1
            alertas.push({
              id: `fora-${i.id}`,
              tipo: 'BENEFICIO_FORA_DA_LISTA',
              categoria: propria ? 'risco' : 'conformidade',
              severidade: propria ? 'alta' : 'media',
              titulo: `${rotuloCodigo(opcao)} aplicado ao NCM ${i.ncm ?? '—'}, que não está na lista do benefício.`,
              recomendacao: propria
                ? 'Risco de recolher a menos: confira o enquadramento antes de manter o benefício.'
                : 'O fornecedor destacou menos imposto do que o devido; o crédito da compra acompanha o destaque. Confirme com ele.',
              ...doItem,
              sugestao: alternativa,
              candidatas: integral && !sugeridas.includes(integral) ? [...sugeridas, integral] : sugeridas,
              impacto: fNota != null && fAlt != null ? emJogo(i, fAlt - fNota) : null,
            })
          } else if (fNota != null && sugeridas.length > 0) {
            const comFator = sugeridas.map((o) => ({ o, f: fatorDe(o)! }))
            if (comFator.every((x) => x.f < fNota)) {
              // a mais conservadora: a sugestão de maior carga (menor economia)
              const melhor = comFator.reduce((a, b) => (b.f > a.f ? b : a))
              alertas.push({
                id: `ben-${i.id}`,
                tipo: 'BENEFICIO_NAO_APLICADO',
                categoria: 'oportunidade',
                severidade: 'alta',
                titulo: `Nota com ${rotuloCodigo(opcao)}; a lista oficial associa o NCM ${i.ncm ?? '—'} a ${sugeridas.map(rotuloCodigo).join(', ')}.`,
                recomendacao: propria
                  ? 'Possível imposto pago a mais: confira se o produto se enquadra e ajuste o cadastro no emissor.'
                  : 'O fornecedor pode não estar repassando o benefício: imposto a mais embutido no preço. Negocie a correção.',
                ...doItem,
                sugestao: melhor.o,
                // as candidatas mais vantajosas primeiro; o valor em jogo usa a mais conservadora
                candidatas: [...sugeridas].sort((a, b) => fatorDe(a)! - fatorDe(b)!),
                impacto: emJogo(i, fNota - melhor.f),
              })
            }
          }
        }
      }

      // compra de fornecedor do Simples: crédito limitado (o backend calcula sem crédito por padrão)
      if (compra && n.emitenteCrt != null && CRT_SIMPLES.has(n.emitenteCrt)) {
        const chave = n.emitenteCnpj ?? n.emitenteNome ?? String(n.id)
        const f = c ? fatorDe(c) : null
        const acc = simples.get(chave) ?? {
          id: `sn-${chave}`,
          tipo: 'FORNECEDOR_SIMPLES' as const,
          categoria: 'oportunidade' as const,
          severidade: 'media' as const,
          titulo: `${n.emitenteNome ?? 'Fornecedor'} é optante do Simples Nacional: o crédito de CBS/IBS das compras é limitado.`,
          recomendacao: 'Compare o preço com fornecedores do regime normal, que dão crédito integral, ou negocie desconto.',
          contraparte: n.emitenteNome,
          impacto: 0,
          quantidade: 0,
        }
        acc.quantidade = (acc.quantidade ?? 0) + 1
        if (f != null) acc.impacto = arred((acc.impacto ?? 0) + baseEstimada(i) * aliquota * f)
        simples.set(chave, acc)
      }
    }

    if (n.itens.length > 0 && itensSemGrupo === n.itens.length) {
      if (venda) {
        alertas.push({
          id: `sg-${n.id}`,
          tipo: 'SEM_IBS_CBS',
          categoria: 'conformidade',
          severidade: 'media',
          titulo: `Sua NF-e ${n.numero ?? 's/n'} saiu sem o grupo IBS/CBS em nenhum item.`,
          recomendacao: 'Confira se o emissor já está no leiaute da reforma e se os produtos têm CST e cClassTrib cadastrados.',
          ...base,
          impacto: null,
          quantidade: n.itens.length,
        })
      } else if (compra) {
        const chave = n.emitenteCnpj ?? n.emitenteNome ?? String(n.id)
        const acc = semGrupoCompras.get(chave) ?? {
          id: `sgc-${chave}`,
          tipo: 'SEM_IBS_CBS' as const,
          categoria: 'conformidade' as const,
          severidade: 'baixa' as const,
          titulo: `${n.emitenteNome ?? 'Fornecedor'} emite notas sem o grupo IBS/CBS.`,
          recomendacao: 'O TribIA classificou os itens por conta própria. Peça ao fornecedor para atualizar o emissor.',
          contraparte: n.emitenteNome,
          impacto: null,
          quantidade: 0,
        }
        acc.quantidade = (acc.quantidade ?? 0) + 1
        semGrupoCompras.set(chave, acc)
      }
    }
  }

  alertas.push(...simples.values(), ...semGrupoCompras.values())
  if (pendentesRevisao > 0) {
    alertas.push({
      id: 'revisao',
      tipo: 'REVISAO_PENDENTE',
      categoria: 'conformidade',
      severidade: 'media',
      titulo: `${pendentesRevisao} item(ns) sem classificação confirmada.`,
      recomendacao: 'Aceite ou corrija na tela de Revisão para que comparativo e alertas fiquem completos.',
      impacto: null,
      quantidade: pendentesRevisao,
    })
  }

  const peso: Record<Severidade, number> = { alta: 0, media: 1, baixa: 2 }
  alertas.sort((a, b) => peso[a.severidade] - peso[b.severidade] || (b.impacto ?? 0) - (a.impacto ?? 0))

  const total = (cat: CategoriaAlerta) =>
    arred(alertas.filter((a) => a.categoria === cat).reduce((s, a) => s + (a.impacto ?? 0), 0))
  return {
    alertas,
    aliquotaReferencia: aliquota,
    origemAliquota: origem,
    resumo: {
      total: alertas.length,
      oportunidade: total('oportunidade'),
      risco: total('risco'),
      porCategoria: {
        oportunidade: alertas.filter((a) => a.categoria === 'oportunidade').length,
        risco: alertas.filter((a) => a.categoria === 'risco').length,
        conformidade: alertas.filter((a) => a.categoria === 'conformidade').length,
      } satisfies Record<CategoriaAlerta, number>,
    },
  }
}

export type ResultadoAlertas = ReturnType<typeof gerarAlertas>
