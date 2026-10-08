import { Link, useSearchParams } from 'react-router-dom'
import { BrainCircuit, ListChecks } from 'lucide-react'
import { INTELIGENCIA_FISCAL_ATIVA, rotaAnaliseFiscal, rotaEmpresa } from '../../lib/rotas'
import { ClassificacaoItens } from '../../components/analises/ClassificacaoItens'
import { Relacionamentos } from '../../components/analises/Relacionamentos'
import { CabecalhoEmpresa, EstadoEmpresa } from '../../components/empresas/CabecalhoEmpresa'
import { RelatorioEmpresa } from '../../components/relatorio/RelatorioEmpresa'
import { useEmpresa } from '../../hooks/useEmpresa'

type Aba = 'relatorio' | 'classificacao' | 'relacionamentos'

const ABAS: { id: Aba; rotulo: string }[] = [
  { id: 'relatorio', rotulo: 'Relatório' },
  { id: 'classificacao', rotulo: 'Classificação dos itens' },
  { id: 'relacionamentos', rotulo: 'Relacionamentos' },
]

/** Resultado das análises da empresa num só lugar: relatório, classificação dos itens e relacionamentos. */
export function AnalisesRelatorios() {
  const { id, empresa, notas, carregando } = useEmpresa()
  const [params, setParams] = useSearchParams()
  const aba = (ABAS.find((a) => a.id === params.get('aba'))?.id ?? 'relatorio') as Aba

  if (!empresa) return <EstadoEmpresa carregando={carregando} />

  const trocar = (a: Aba) => {
    const p = new URLSearchParams(params)
    if (a === 'relatorio') p.delete('aba')
    else p.set('aba', a)
    setParams(p, { replace: true })
  }

  return (
    <>
      <CabecalhoEmpresa
        empresa={empresa}
        secao="Análises e Relatórios"
        acoes={
          INTELIGENCIA_FISCAL_ATIVA ? (
            <Link to={rotaAnaliseFiscal(id)} className="btn btn--secondary">
              <BrainCircuit size={17} /> Relatórios de mercadorias
            </Link>
          ) : (
            <Link to={rotaEmpresa(id, 'revisao')} className="btn btn--secondary">
              <ListChecks size={17} /> Revisar classificações
            </Link>
          )
        }
      />
      <div className="tabs" role="tablist">
        {ABAS.map((a) => (
          <button key={a.id} role="tab" aria-selected={aba === a.id} onClick={() => trocar(a.id)}>
            {a.rotulo}
          </button>
        ))}
      </div>
      {aba === 'relatorio' && <RelatorioEmpresa empresaId={id} notas={notas} />}
      {aba === 'classificacao' && <ClassificacaoItens notas={notas} />}
      {aba === 'relacionamentos' && <Relacionamentos empresa={empresa} notas={notas} />}
    </>
  )
}
