import { Link } from 'react-router-dom'
import { CircleAlert, CircleCheck, LoaderCircle } from 'lucide-react'
import { fmtDataHora, TIPO_LABEL } from '../lib/format'
import { rotaNota } from '../lib/rotas'
import { useAtividades } from '../state/contexts'
import { Badge, Vazio } from './ui'

/**
 * Acompanhamento dos envios de XML de uma empresa: o que está sendo processado agora e o resultado
 * de cada envio (notas importadas e motivo das recusas).
 */
export function EnviosRecentes({ clienteId, limite = 5, compacto = false }: {
  clienteId: number
  limite?: number
  compacto?: boolean
}) {
  const { atividades, emProcessamento } = useAtividades()
  const daEmpresa = atividades.filter((a) => a.clienteId === clienteId).slice(0, limite)

  if (daEmpresa.length === 0 && emProcessamento === 0) {
    return compacto ? (
      <p style={{ fontSize: 13.5, color: 'var(--text-3)' }}>
        Nenhum envio ainda. Use “Enviar notas” para importar os XMLs desta empresa.
      </p>
    ) : (
      <Vazio titulo="Nenhum envio ainda">Use “Enviar notas” para importar os XMLs desta empresa.</Vazio>
    )
  }

  return (
    <ul className="envios">
      {emProcessamento > 0 && (
        <li className="envios__item">
          <LoaderCircle size={18} className="spin" color="var(--green-600)" />
          <div>
            <div className="cell-main">Processando {emProcessamento} arquivo(s)...</div>
            <div className="cell-sub">O resultado aparece aqui em instantes.</div>
          </div>
        </li>
      )}
      {daEmpresa.map((a) => {
        const ok = a.importadas.length > 0
        return (
          <li key={a.id} className="envios__item">
            {ok ? <CircleCheck size={18} color="var(--green-600)" /> : <CircleAlert size={18} color="var(--danger)" />}
            <div style={{ minWidth: 0, flex: 1 }}>
              <div style={{ display: 'flex', gap: 6, flexWrap: 'wrap', alignItems: 'center' }}>
                <span className="cell-main">
                  {a.arquivos.length} arquivo(s) · {fmtDataHora(a.quando)}
                </span>
                {ok && <Badge cor="green" sm>{a.importadas.length} importada(s)</Badge>}
                {a.rejeitadas.length > 0 && <Badge cor="red" sm>{a.rejeitadas.length} recusada(s)</Badge>}
              </div>
              {!compacto &&
                a.importadas.map((n) => (
                  <div key={n.id} className="cell-sub">
                    <Link to={rotaNota(a.clienteId, n.id)}>NF-e {n.numero}</Link> · {TIPO_LABEL[n.tipo]}
                  </div>
                ))}
              {a.rejeitadas.map((r, i) => (
                <div key={i} className="cell-sub" style={{ overflowWrap: 'anywhere' }}>
                  {r.arquivo}: {r.motivo}
                </div>
              ))}
              {a.erro && <div className="cell-sub">{a.erro}</div>}
            </div>
          </li>
        )
      })}
    </ul>
  )
}
