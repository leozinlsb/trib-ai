import type { ReactNode } from 'react'
import { Link } from 'react-router-dom'
import type { Cliente } from '../../api/types'
import { fmtCnpj, nomeCliente, REGIME_LABEL } from '../../lib/format'
import { useAuth } from '../../state/contexts'
import { Badge, Carregando, Vazio } from '../ui'

/** Cabeçalho do ambiente de uma empresa: deixa claro de qual empresa são os dados na tela. */
export function CabecalhoEmpresa({ empresa, secao, sub, acoes }: {
  empresa: Cliente
  /** título da seção (ex.: "Documentos"); sem ele, o título é o nome da empresa */
  secao?: string
  sub?: ReactNode
  acoes?: ReactNode
}) {
  return (
    <div className="page-head">
      <div style={{ minWidth: 0 }}>
        <span className="eyebrow">Empresa selecionada</span>
        <h1 style={{ display: 'flex', gap: 10, alignItems: 'center', flexWrap: 'wrap' }}>
          {secao ?? nomeCliente(empresa)}
          {!empresa.ativo && <Badge cor="gray">Desativada</Badge>}
        </h1>
        <p>
          {secao ? <strong style={{ color: 'var(--text)', fontWeight: 600 }}>{nomeCliente(empresa)}</strong> : empresa.razaoSocial}
          {' · '}CNPJ {fmtCnpj(empresa.cnpj)} · {REGIME_LABEL[empresa.regime]}
          {empresa.municipio ? ` · ${empresa.municipio}/${empresa.uf ?? ''}` : ''}
        </p>
        {sub && <p style={{ marginTop: 4 }}>{sub}</p>}
      </div>
      {acoes && <div className="page-head__actions">{acoes}</div>}
    </div>
  )
}

/** Estados de carregamento / empresa inexistente para as páginas do ambiente da empresa. */
export function EstadoEmpresa({ carregando }: { carregando: boolean }) {
  const { admin } = useAuth()
  if (carregando) return <div className="card"><Carregando linhas={6} /></div>
  return (
    <div className="card">
      <Vazio titulo="Empresa não encontrada">
        {admin ? <Link to="/dashboard/empresas">Ver empresas</Link> : 'Fale com o escritório.'}
      </Vazio>
    </div>
  )
}
