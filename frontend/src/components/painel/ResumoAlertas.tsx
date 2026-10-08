import { Link } from 'react-router-dom'
import { ArrowRight, ShieldAlert } from 'lucide-react'
import type { Cliente, NotaResumo } from '../../api/types'
import { useAlertas } from '../../hooks/useAlertas'
import { fmtMoeda, fmtNumero } from '../../lib/format'
import { rotaEmpresa } from '../../lib/rotas'
import { Card } from '../ui'

/** Faixa do início da empresa: quanto os alertas fiscais somam e o caminho para eles. */
export function ResumoAlertas({ empresa, notas }: { empresa: Cliente; notas: NotaResumo[] }) {
  const { resultado, erro } = useAlertas(empresa, notas)
  if (erro || notas.length === 0) return null
  const r = resultado?.resumo

  const bloco = (rotulo: string, valor: string, cor?: string) => (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 2, minWidth: 140 }}>
      <span className="cell-sub">{rotulo}</span>
      <b style={{ fontSize: 20, color: cor }}>{valor}</b>
    </div>
  )

  return (
    <Card
      titulo={
        <span style={{ display: 'inline-flex', alignItems: 'center', gap: 8 }}>
          <ShieldAlert size={18} /> Alertas fiscais
        </span>
      }
      sub="Códigos das notas conferidos contra a tabela oficial e a lista de NCMs dos benefícios. Valores estimados."
      acoes={
        <Link to={rotaEmpresa(empresa.id, 'alertas')} className="btn btn--secondary btn--sm">
          Ver alertas <ArrowRight size={14} />
        </Link>
      }
    >
      {!r ? (
        <span className="cell-sub">Conferindo as notas...</span>
      ) : (
        <div style={{ display: 'flex', gap: 28, flexWrap: 'wrap' }}>
          {bloco('Oportunidades', fmtMoeda(r.oportunidade), 'var(--green-600)')}
          {bloco('Riscos fiscais', fmtMoeda(r.risco), r.risco > 0 ? 'var(--danger)' : undefined)}
          {bloco('Alertas', fmtNumero(r.total))}
          {bloco('Pendências', fmtNumero(r.porCategoria.conformidade))}
        </div>
      )}
    </Card>
  )
}
