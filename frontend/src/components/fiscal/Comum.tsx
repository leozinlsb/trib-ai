import type { ReactNode } from 'react'
import { Unplug } from 'lucide-react'
import type { SituacaoValidacao, StatusAnalise } from '../../api/inteligenciaFiscal'
import { STATUS, VALIDACAO } from '../../lib/fiscal'
import { Badge } from '../ui'

export function BadgeStatus({ status, sm }: { status: StatusAnalise; sm?: boolean }) {
  const s = STATUS[status]
  return <Badge cor={s.cor} sm={sm} title={s.descricao}>{s.rotulo}</Badge>
}

export function BadgeValidacao({ situacao, sm }: { situacao: SituacaoValidacao; sm?: boolean }) {
  const v = VALIDACAO[situacao]
  return <Badge cor={v.cor} sm={sm} title={v.descricao}>{v.rotulo}</Badge>
}

/** Estado "recurso aguardando integração": o serviço de análise ainda não está ativo neste ambiente. */
export function ServicoIndisponivel({ compacto, children }: { compacto?: boolean; children?: ReactNode }) {
  return (
    <div className={`indisponivel${compacto ? ' indisponivel--compacto' : ''}`} role="status">
      <span className="indisponivel__icone"><Unplug size={compacto ? 18 : 22} /></span>
      <div>
        <div className="indisponivel__titulo">Análise fiscal ainda não disponível</div>
        <p>
          O serviço que analisa as mercadorias ainda não foi ativado neste ambiente. Assim que estiver disponível, as
          análises da empresa aparecerão aqui automaticamente.
        </p>
        {children}
      </div>
    </div>
  )
}
