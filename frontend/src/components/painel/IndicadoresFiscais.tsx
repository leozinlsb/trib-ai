import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { indicadores as buscarIndicadores, servicoIndisponivel, type IndicadoresFiscais as Indicadores } from '../../api/inteligenciaFiscal'
import { fmtNumero } from '../../lib/format'
import { rotaAnaliseFiscal } from '../../lib/rotas'
import { Card, Carregando, ErroEstado } from '../ui'

/**
 * Situação das análises de NCM da empresa (GET /api/clientes/{id}/analises-fiscais/indicadores), contadas no banco.
 * Sem análises, convida a fazer a primeira; sem o serviço no servidor, some (não mostra número inventado).
 */
export function IndicadoresFiscais({ id }: { id: number }) {
  const [dados, setDados] = useState<Indicadores | null>(null)
  const [erro, setErro] = useState<string | null>(null)
  const [indisponivel, setIndisponivel] = useState(false)
  const [tentativa, setTentativa] = useState(0)

  useEffect(() => {
    const ctrl = new AbortController()
    setErro(null)
    setDados(null)
    buscarIndicadores(id, ctrl.signal)
      .then(setDados)
      .catch((e) => {
        if (e instanceof DOMException && e.name === 'AbortError') return
        if (servicoIndisponivel(e)) {
          setIndisponivel(true)
          return
        }
        setErro(e instanceof Error ? e.message : 'Falha ao carregar as análises fiscais.')
      })
    return () => ctrl.abort()
  }, [id, tentativa])

  if (indisponivel) return null

  const titulo = 'Inteligência Fiscal'
  const link = <Link to={rotaAnaliseFiscal(id)} style={{ fontSize: 13, fontWeight: 500 }}>Abrir</Link>

  if (erro) {
    return (
      <Card titulo={titulo} acoes={link}>
        <ErroEstado mensagem={erro} onTentar={() => setTentativa((t) => t + 1)} />
      </Card>
    )
  }
  if (!dados) {
    return <Card titulo={titulo} acoes={link}><Carregando linhas={3} /></Card>
  }
  if (dados.total === 0) {
    return (
      <Card titulo={titulo} sub="Sugestão de NCM de mercadorias, com verificações e relatório.">
        <p style={{ fontSize: 13.5, color: 'var(--text-3)', margin: '0 0 12px' }}>
          Nenhuma análise de mercadoria ainda.
        </p>
        <Link to={rotaAnaliseFiscal(id, 'nova')} className="btn btn--secondary btn--sm">Nova análise</Link>
      </Card>
    )
  }

  const linhas: { rotulo: string; valor: number; destaque?: string }[] = [
    { rotulo: 'Concluídas', valor: dados.concluidas },
    { rotulo: 'Em processamento', valor: dados.emProcessamento },
    { rotulo: 'Aguardando revisão', valor: dados.aguardandoRevisao, destaque: 'var(--badge-amber-fg)' },
  ]
  if (dados.informacoesInsuficientes != null) {
    linhas.push({ rotulo: 'Informações insuficientes', valor: dados.informacoesInsuficientes })
  }
  if (dados.falhas != null) {
    linhas.push({ rotulo: 'Com falha', valor: dados.falhas, destaque: 'var(--badge-red-fg)' })
  }

  return (
    <Card titulo={titulo} sub={`${fmtNumero(dados.total)} análise(s) de NCM`} acoes={link}>
      <div className="resumo-periodo">
        {linhas.map((l) => (
          <div className="resumo-periodo__linha" key={l.rotulo}>
            <span>{l.rotulo}</span>
            <b style={l.valor > 0 && l.destaque ? { color: l.destaque } : undefined}>{fmtNumero(l.valor)}</b>
          </div>
        ))}
      </div>
    </Card>
  )
}
