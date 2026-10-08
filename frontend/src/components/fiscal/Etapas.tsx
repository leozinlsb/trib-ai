import { Check, CircleAlert, CircleHelp, UserSearch } from 'lucide-react'
import { ETAPAS_PROCESSAMENTO, type AnaliseDetalhe, type EtapaProcessamento } from '../../api/inteligenciaFiscal'
import { STATUS } from '../../lib/fiscal'
import { fmtDataHora } from '../../lib/format'

/**
 * Acompanhamento do processamento como sequência de etapas (sem porcentagens: o servidor informa
 * apenas em qual etapa a análise está).
 */
export function EtapasProcessamento({ analise }: { analise: AnaliseDetalhe }) {
  const quando = new Map(analise.historico.map((h) => [h.status, h.em]))
  const especial = !ETAPAS_PROCESSAMENTO.includes(analise.status as EtapaProcessamento)

  // etapa atingida: a atual, ou a última etapa normal registrada antes de uma situação especial
  const ultimaNormal = [...analise.historico]
    .reverse()
    .find((h) => ETAPAS_PROCESSAMENTO.includes(h.status as EtapaProcessamento))?.status as EtapaProcessamento | undefined
  const atual: EtapaProcessamento = especial
    ? analise.status === 'AGUARDANDO_REVISAO' && analise.resultado
      ? 'CONCLUIDA'
      : (ultimaNormal ?? 'AGUARDANDO')
    : (analise.status as EtapaProcessamento)
  const iAtual = ETAPAS_PROCESSAMENTO.indexOf(atual)

  return (
    <ol className="etapas" aria-label="Etapas da análise">
      {ETAPAS_PROCESSAMENTO.map((etapa, i) => {
        // processamento terminado: concluída normalmente ou concluída e enviada para revisão
        const terminou = atual === 'CONCLUIDA' && (!especial || analise.status === 'AGUARDANDO_REVISAO')
        const feita = i < iAtual || (etapa === 'CONCLUIDA' && terminou)
        const corrente = i === iAtual && !feita
        const interrompida = corrente && especial && analise.status !== 'AGUARDANDO_REVISAO'
        const estado = feita ? 'feita' : interrompida ? 'interrompida' : corrente ? 'atual' : 'pendente'
        return (
          <li key={etapa} className={`etapa etapa--${estado}`} aria-current={corrente ? 'step' : undefined}>
            <span className="etapa__marca" aria-hidden="true">
              {feita ? <Check size={14} strokeWidth={3} /> : interrompida ? <CircleAlert size={15} /> : <span className="etapa__ponto" />}
            </span>
            <div className="etapa__texto">
              <span className="etapa__nome">{STATUS[etapa].rotulo}</span>
              {corrente && !interrompida && <span className="etapa__desc">{STATUS[etapa].descricao}</span>}
              {quando.get(etapa) && <span className="etapa__hora">{fmtDataHora(quando.get(etapa)!)}</span>}
            </div>
          </li>
        )
      })}
      {especial && (
        <li className={`etapa etapa--${analise.status === 'FALHA' ? 'interrompida' : 'alerta'}`}>
          <span className="etapa__marca" aria-hidden="true">
            {analise.status === 'AGUARDANDO_REVISAO' ? <UserSearch size={15} /> : analise.status === 'FALHA' ? <CircleAlert size={15} /> : <CircleHelp size={15} />}
          </span>
          <div className="etapa__texto">
            <span className="etapa__nome">{STATUS[analise.status].rotulo}</span>
            <span className="etapa__desc">{analise.mensagem ?? STATUS[analise.status].descricao}</span>
          </div>
        </li>
      )}
    </ol>
  )
}
