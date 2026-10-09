import { useState } from 'react'
import { Link } from 'react-router-dom'
import { RefreshCw } from 'lucide-react'
import { CardConta, CardPreferencias } from '../../components/Preferencias'
import { CardJev } from '../../components/fiscal/CardJev'
import { CardTabelaNcm } from '../../components/fiscal/CardTabelaNcm'
import { INTELIGENCIA_FISCAL_ATIVA } from '../../lib/rotas'
import { Badge, Card } from '../../components/ui'
import { useAtividades, useDados, useToast } from '../../state/contexts'

/** Configurações do administrador. Os dados de cada empresa ficam no ambiente dela. */
export function Configuracoes() {
  const { clientes, notas, recarregar, conexao, carregadoEm } = useDados()
  const { atividades } = useAtividades()
  const { mostrar } = useToast()
  const [atualizando, setAtualizando] = useState(false)

  return (
    <>
      <div className="page-head">
        <div>
          <h1>Configurações</h1>
          <p>Sua conta, preferências de uso e situação dos dados</p>
        </div>
      </div>

      <div className="grid-2" style={{ marginBottom: 'var(--gap)' }}>
        <CardConta />
        <Card
          titulo="Dados"
          acoes={
            <button
              className="btn btn--secondary btn--sm"
              disabled={atualizando}
              onClick={async () => {
                setAtualizando(true)
                await recarregar()
                setAtualizando(false)
                mostrar({ tipo: 'info', titulo: 'Dados atualizados' })
              }}
            >
              <RefreshCw size={14} className={atualizando ? 'spin' : undefined} /> Atualizar agora
            </button>
          }
        >
          <dl className="dl" style={{ gridTemplateColumns: '1fr 1fr' }}>
            <div>
              <dt>Situação</dt>
              <dd>{conexao === 'offline' ? <Badge cor="red" sm>Indisponível</Badge> : <Badge cor="green" sm>Funcionando</Badge>}</dd>
            </div>
            <div><dt>Última atualização</dt><dd>{carregadoEm?.toLocaleTimeString('pt-BR') ?? '—'}</dd></div>
            <div><dt>Empresas</dt><dd>{clientes.length} <Link to="/dashboard/empresas" style={{ fontSize: 13 }}>gerenciar</Link></dd></div>
            <div><dt>Notas fiscais</dt><dd>{notas.length}</dd></div>
            <div><dt>Seus envios</dt><dd>{atividades.length}</dd></div>
          </dl>
        </Card>
      </div>
      <div className="grid-2">
        <CardPreferencias />
        {INTELIGENCIA_FISCAL_ATIVA && <CardJev />}
      </div>
      {INTELIGENCIA_FISCAL_ATIVA && (
        <div className="grid-2" style={{ marginTop: 'var(--gap)' }}>
          <CardTabelaNcm />
        </div>
      )}
    </>
  )
}
