import type { Item } from '../api/types'
import { itemComCodigoNoXml } from '../lib/aggregate'
import { corClassificacao, fmtConfianca, ORIGEM_LABEL, REGIME_TRIB_LABEL } from '../lib/classificacao'
import { Badge } from './ui'

/** Célula de classificação de um item: código, efeito, origem e confiança (com a justificativa no título). */
export function CelulaClassificacao({ item }: { item: Item }) {
  const c = item.classificacao
  if (!c) {
    if (itemComCodigoNoXml(item)) {
      const g = item.ibsCbsDestacado!
      return (
        <Badge cor="gray" sm title="Código informado no XML; processe a nota para o TribIA conferir e calcular">
          XML: {g.cst} · {g.cClassTrib}
        </Badge>
      )
    }
    return <Badge cor="gray" sm title="Item ainda sem classificação tributária: processe a nota">Pendente</Badge>
  }
  const origem = ORIGEM_LABEL[c.origem]
  const titulo = [c.nomeCClassTrib, c.justificativa].filter(Boolean).join(' — ')
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 3 }}>
      <span title={titulo || undefined}>
        <Badge cor={corClassificacao(c)} sm>CST {c.cst} · {c.cClassTrib}</Badge>
      </span>
      <span className="cell-sub" title={origem.dica}>
        {c.descricaoRegime ?? REGIME_TRIB_LABEL[c.regime]} · {origem.rotulo}
        {c.origem !== 'XML' && c.origem !== 'MANUAL' ? ` · ${fmtConfianca(c.confianca)}` : ''}
        {!c.aceita && !c.revisada ? ' · a revisar' : ''}
      </span>
    </div>
  )
}
