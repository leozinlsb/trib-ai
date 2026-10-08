import { useEffect, useMemo, useState } from 'react'
import { opcoesClassificacao } from '../api/tribia'
import type { Cliente, NotaDetalhe, NotaResumo, OpcaoClassificacao } from '../api/types'
import { gerarAlertas, type ResultadoAlertas } from '../lib/alertas'
import { useDetalhes } from './useDetalhes'

/** A tabela oficial não muda durante a sessão: uma busca por NCM basta. */
const opcoesCache = new Map<string, Promise<OpcaoClassificacao[]>>()

function opcoesDoNcm(ncm: string) {
  let p = opcoesCache.get(ncm)
  if (!p) {
    p = opcoesClassificacao(ncm || null).catch((e: unknown) => {
      opcoesCache.delete(ncm)
      throw e
    })
    opcoesCache.set(ncm, p)
  }
  return p
}

/**
 * Alertas fiscais da empresa, calculados no navegador a partir do detalhe das notas (com classificação e cálculo
 * persistidos) e da tabela oficial de cClassTrib por NCM.
 */
export function useAlertas(empresa: Cliente | undefined, notas: NotaResumo[]) {
  const ids = useMemo(() => notas.map((n) => n.id), [notas])
  const { detalhes, errosDetalhe, completo, prontos } = useDetalhes(ids)
  const lista = useMemo(
    () => ids.map((id) => detalhes.get(id)).filter((d): d is NotaDetalhe => d != null),
    [ids, detalhes],
  )

  // só os NCMs de itens que trouxeram cClassTrib na nota precisam da tabela
  const ncms = useMemo(
    () => [...new Set(lista.flatMap((n) => n.itens).filter((i) => i.ibsCbsDestacado?.cClassTrib).map((i) => i.ncm ?? ''))].sort(),
    [lista],
  )
  const chaveNcms = ncms.join(',')
  const [opcoes, setOpcoes] = useState<ReadonlyMap<string, OpcaoClassificacao[]>>(new Map())
  const [erroOpcoes, setErroOpcoes] = useState<string | null>(null)
  const [tentativa, setTentativa] = useState(0)

  useEffect(() => {
    let vivo = true
    setErroOpcoes(null)
    Promise.all(ncms.map((n) => opcoesDoNcm(n).then((l) => [n, l] as const)))
      .then((pares) => {
        if (vivo) setOpcoes(new Map(pares))
      })
      .catch((e: unknown) => {
        if (vivo) setErroOpcoes(e instanceof Error ? e.message : 'Falha ao carregar a tabela oficial.')
      })
    return () => {
      vivo = false
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [chaveNcms, tentativa])

  const opcoesProntas = ncms.every((n) => opcoes.has(n))
  const resultado: ResultadoAlertas | null = useMemo(
    () =>
      empresa && completo && opcoesProntas
        ? gerarAlertas({ cnpjEmpresa: empresa.cnpj, notas: lista, opcoesPorNcm: opcoes })
        : null,
    [empresa, completo, opcoesProntas, lista, opcoes],
  )

  return {
    resultado,
    carregando: !resultado && !erroOpcoes,
    erro: erroOpcoes,
    tentarNovamente: () => setTentativa((t) => t + 1),
    notasComErro: errosDetalhe.size ? ids.filter((id) => errosDetalhe.has(id)).length : 0,
    progresso: { prontos, de: ids.length },
  }
}
