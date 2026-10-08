import type { NotaDetalhe, TipoNota } from '../api/types'
import { statusClassificacao, type StatusClassificacao } from '../lib/aggregate'
import { Badge } from './ui'

export function BadgeTipo({ tipo, sm }: { tipo: TipoNota; sm?: boolean }) {
  return tipo === 'SAIDA' ? (
    <Badge cor="blue" sm={sm} title="Venda: o cliente é o emitente (gera débito)">Saída</Badge>
  ) : (
    <Badge cor="green" sm={sm} title="Compra: o cliente é o destinatário (gera crédito)">Entrada</Badge>
  )
}

const CLASSIF: Record<StatusClassificacao, { cor: 'green' | 'blue' | 'gray'; rotulo: string; dica: string }> = {
  classificada: { cor: 'green', rotulo: 'Classificada', dica: 'Todos os itens têm classificação tributária (CST e cClassTrib)' },
  parcial: { cor: 'blue', rotulo: 'Parcial', dica: 'Parte dos itens já tem classificação tributária' },
  pendente: {
    cor: 'gray',
    rotulo: 'Pendente',
    dica: 'Os itens desta nota ainda não têm classificação tributária',
  },
}

export function BadgeClassificacao({ detalhe, erro, sm }: { detalhe?: NotaDetalhe; erro?: string; sm?: boolean }) {
  if (erro) return <Badge cor="red" sm={sm} title={erro}>Erro</Badge>
  if (!detalhe) return <span className="skeleton" style={{ width: 84, height: 22, display: 'inline-block', borderRadius: 99 }} />
  const s = CLASSIF[statusClassificacao(detalhe)]
  return <Badge cor={s.cor} sm={sm} title={s.dica}>{s.rotulo}</Badge>
}
