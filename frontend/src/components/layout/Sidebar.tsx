import { Link, NavLink, useMatch, useNavigate } from 'react-router-dom'
import {
  ArrowLeft, BrainCircuit, Building2, ChartColumn, FileText, FolderKanban, House, ListChecks, LogOut, Settings, ShieldAlert, X,
} from 'lucide-react'
import { Logo } from './Logo'
import { useAuth, useDados } from '../../state/contexts'
import { iniciais, nomeCliente } from '../../lib/format'
import { INTELIGENCIA_FISCAL_ATIVA, rotaEmpresa } from '../../lib/rotas'

type Item = { to: string; rotulo: string; icone: typeof House; end?: boolean }

const ADMIN: Item[] = [
  { to: '/dashboard', rotulo: 'Visão Geral', icone: House, end: true },
  { to: '/dashboard/empresas', rotulo: 'Empresas', icone: Building2, end: true },
  { to: '/dashboard/documentos', rotulo: 'Documentos', icone: FileText },
  ...(INTELIGENCIA_FISCAL_ATIVA
    ? [{ to: '/dashboard/inteligencia-fiscal', rotulo: 'Inteligência Fiscal', icone: BrainCircuit }]
    : []),
  { to: '/dashboard/relatorios', rotulo: 'Relatórios', icone: FolderKanban },
  { to: '/dashboard/configuracoes', rotulo: 'Configurações', icone: Settings },
]

function menuEmpresa(id: number): Item[] {
  return [
    { to: rotaEmpresa(id), rotulo: 'Início', icone: House, end: true },
    { to: rotaEmpresa(id, 'documentos'), rotulo: 'Documentos', icone: FileText },
    { to: rotaEmpresa(id, 'alertas'), rotulo: 'Alertas', icone: ShieldAlert },
    { to: rotaEmpresa(id, 'revisao'), rotulo: 'Revisão', icone: ListChecks },
    ...(INTELIGENCIA_FISCAL_ATIVA
      ? [{ to: rotaEmpresa(id, 'inteligencia-fiscal'), rotulo: 'Inteligência Fiscal', icone: BrainCircuit }]
      : []),
    { to: rotaEmpresa(id, 'analises'), rotulo: 'Análises e Relatórios', icone: ChartColumn },
    { to: rotaEmpresa(id, 'configuracoes'), rotulo: 'Configurações', icone: Settings },
  ]
}

export function Sidebar({ onNavegar }: { onNavegar: () => void }) {
  const { usuario, admin, sair } = useAuth()
  const { clientes } = useDados()
  const navigate = useNavigate()
  const naEmpresa = useMatch('/dashboard/empresas/:empresaId/*')
  const empresaId = naEmpresa ? Number(naEmpresa.params.empresaId) : (usuario?.clienteId ?? null)
  const empresa = clientes.find((c) => c.id === empresaId)

  const itens = admin && !naEmpresa ? ADMIN : empresaId ? menuEmpresa(empresaId) : []

  const encerrar = async () => {
    onNavegar()
    await sair()
    navigate('/login', { replace: true })
  }

  return (
    <aside className="sidebar" aria-label="Navegação principal">
      <button className="icon-btn sidebar__close" onClick={onNavegar} aria-label="Fechar menu">
        <X size={18} />
      </button>
      <Link
        to={admin ? '/dashboard' : rotaEmpresa(empresaId ?? 0)}
        className="sidebar__logo"
        onClick={onNavegar}
        aria-label="TribIA — início"
      >
        <Logo tamanho={40} />
      </Link>

      {admin && naEmpresa && (
        <Link to="/dashboard" className="sidebar__voltar" onClick={onNavegar}>
          <ArrowLeft size={16} /> Visão geral
        </Link>
      )}
      {(naEmpresa || !admin) && empresa && (
        <div className="sidebar__empresa" title={empresa.razaoSocial}>
          <span className="sidebar__empresa-rotulo">Empresa</span>
          <span className="sidebar__empresa-nome">{nomeCliente(empresa)}</span>
        </div>
      )}

      <nav className="sidebar__nav">
        {itens.map(({ to, rotulo, icone: Icone, end }) => (
          <NavLink
            key={to}
            to={to}
            end={end}
            onClick={onNavegar}
            className={({ isActive }) => `nav-item${isActive ? ' active' : ''}`}
          >
            <Icone size={19} strokeWidth={1.8} />
            {rotulo}
          </NavLink>
        ))}
      </nav>

      {usuario && (
        <div className="sidebar__user">
          <span className="avatar avatar--sm" aria-hidden="true">
            {iniciais(usuario.nome) || usuario.nome.slice(0, 1).toUpperCase()}
          </span>
          <span style={{ minWidth: 0, flex: 1 }}>
            <span className="sidebar__user-name">{usuario.nome}</span>
            <span className="sidebar__user-sub">{admin ? 'Administrador' : usuario.clienteNome}</span>
          </span>
          <button className="icon-btn" onClick={encerrar} aria-label="Sair" title="Sair">
            <LogOut size={18} />
          </button>
        </div>
      )}
    </aside>
  )
}
