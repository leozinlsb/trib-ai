import { useEffect, useRef, useState, type ReactNode } from 'react'
import { Link, useLocation, useMatch, useNavigate } from 'react-router-dom'
import { Bell, Building2, Check, ChevronsUpDown, LayoutGrid, LogOut, Menu, Search, Settings } from 'lucide-react'
import { useAtividades, useAuth, useDados } from '../../state/contexts'
import { fmtCnpj, fmtDataHora, iniciais, nomeCliente, normalizar } from '../../lib/format'
import { rotaEmpresa, trocarEmpresaNoCaminho } from '../../lib/rotas'
import { BuscaGlobal } from './BuscaGlobal'

export function Topbar({ onMenu }: { onMenu: () => void }) {
  const { admin } = useAuth()
  return (
    <header className="topbar">
      <button className="icon-btn topbar__menu" onClick={onMenu} aria-label="Abrir menu">
        <Menu size={20} />
      </button>
      {admin && <SeletorEmpresa />}
      <BuscaGlobal />
      <div className="topbar__right">
        <StatusSistema />
        <Notificacoes />
        <MenuUsuario />
      </div>
    </header>
  )
}

function usePopover() {
  const [aberto, setAberto] = useState(false)
  const ref = useRef<HTMLDivElement>(null)
  useEffect(() => {
    if (!aberto) return
    const fora = (e: MouseEvent) => {
      if (ref.current && !ref.current.contains(e.target as Node)) setAberto(false)
    }
    const esc = (e: KeyboardEvent) => e.key === 'Escape' && setAberto(false)
    document.addEventListener('mousedown', fora)
    document.addEventListener('keydown', esc)
    return () => {
      document.removeEventListener('mousedown', fora)
      document.removeEventListener('keydown', esc)
    }
  }, [aberto])
  return { aberto, setAberto, ref }
}

function Popover({ titulo, children, acao, esquerda }: {
  titulo: string
  children: ReactNode
  acao?: ReactNode
  esquerda?: boolean
}) {
  return (
    <div className="popover" role="dialog" aria-label={titulo} style={esquerda ? { left: 0, right: 'auto' } : undefined}>
      <div className="popover__head">
        {titulo}
        {acao}
      </div>
      <div className="popover__body">{children}</div>
    </div>
  )
}

/** Alterna entre a visão geral e o ambiente de cada empresa, mantendo a seção atual. */
function SeletorEmpresa() {
  const { clientes } = useDados()
  const { aberto, setAberto, ref } = usePopover()
  const [busca, setBusca] = useState('')
  const navigate = useNavigate()
  const { pathname } = useLocation()
  const naEmpresa = useMatch('/dashboard/empresas/:empresaId/*')
  const atualId = naEmpresa ? Number(naEmpresa.params.empresaId) : null
  const atual = clientes.find((c) => c.id === atualId)

  const q = normalizar(busca.trim())
  const lista = clientes.filter(
    (c) => !q || normalizar(`${c.razaoSocial} ${c.nomeFantasia ?? ''} ${c.cnpj}`).includes(q.replace(/[./-]/g, '')) ||
      normalizar(`${c.razaoSocial} ${c.nomeFantasia ?? ''}`).includes(q),
  )

  const ir = (destino: string) => {
    setAberto(false)
    setBusca('')
    navigate(destino)
  }

  return (
    <div className="popover-wrap seletor" ref={ref}>
      <button className="seletor__botao" onClick={() => setAberto((a) => !a)} aria-expanded={aberto} aria-haspopup="listbox">
        {atual ? <Building2 size={17} /> : <LayoutGrid size={17} />}
        <span className="seletor__texto">
          <span className="seletor__rotulo">{atual ? 'Empresa' : 'Visualizando'}</span>
          <span className="seletor__nome">{atual ? nomeCliente(atual) : 'Todas as empresas'}</span>
        </span>
        <ChevronsUpDown size={16} className="seletor__seta" />
      </button>
      {aberto && (
        <div className="popover seletor__painel" role="dialog" aria-label="Selecionar empresa">
          <label className="seletor__busca">
            <Search size={15} aria-hidden="true" />
            <span className="sr-only">Buscar empresa</span>
            <input autoFocus placeholder="Buscar empresa..." value={busca} onChange={(e) => setBusca(e.target.value)} />
          </label>
          <div className="seletor__lista" role="listbox">
            <button className="search__item" role="option" aria-selected={!atual} onClick={() => ir('/dashboard')}>
              <LayoutGrid size={16} color="var(--text-3)" />
              <span style={{ flex: 1 }}>Visão geral (todas as empresas)</span>
              {!atual && <Check size={15} color="var(--green-600)" />}
            </button>
            <div className="search__group">Empresas</div>
            {lista.length === 0 && <div className="search__empty">Nenhuma empresa encontrada.</div>}
            {lista.map((c) => (
              <button
                key={c.id}
                className="search__item"
                role="option"
                aria-selected={c.id === atualId}
                onClick={() => ir(atual ? trocarEmpresaNoCaminho(pathname, c.id) : rotaEmpresa(c.id))}
              >
                <Building2 size={16} color="var(--text-3)" style={{ flex: 'none' }} />
                <span style={{ flex: 1, minWidth: 0 }}>
                  <span style={{ display: 'block' }}>
                    {nomeCliente(c)} {!c.ativo && <span className="badge badge--gray badge--sm">Desativada</span>}
                  </span>
                  <span className="search__item-sub">{fmtCnpj(c.cnpj)}</span>
                </span>
                {c.id === atualId && <Check size={15} color="var(--green-600)" />}
              </button>
            ))}
          </div>
          <div className="seletor__rodape">
            <Link to="/dashboard/empresas" onClick={() => setAberto(false)}>Gerenciar empresas</Link>
          </div>
        </div>
      )}
    </div>
  )
}

/** Situação do sistema, verificada a cada 30 s. */
function StatusSistema() {
  const { conexao, verificarConexao, verificadoEm } = useDados()
  const [verificando, setVerificando] = useState(false)
  const cls = conexao === 'online' ? '' : conexao === 'offline' ? 'status-pill--off' : 'status-pill--wait'
  const rotulo = conexao === 'online' ? 'Sistema Ativo' : conexao === 'offline' ? 'Sistema indisponível' : 'Verificando...'
  const titulo =
    conexao === 'offline'
      ? 'Não foi possível conectar ao TribIA. Clique para tentar novamente.'
      : `Tudo funcionando${verificadoEm ? ` · verificado às ${verificadoEm.toLocaleTimeString('pt-BR')}` : ''}. Clique para verificar agora.`
  return (
    <button
      className={`status-pill ${cls} hide-md`}
      title={titulo}
      disabled={verificando}
      onClick={async () => {
        setVerificando(true)
        await verificarConexao()
        setVerificando(false)
      }}
    >
      <span className="status-pill__dot" aria-hidden="true" />
      {verificando ? 'Verificando...' : rotulo}
    </button>
  )
}

/** Notificações: resultado de cada envio de notas feito por este usuário. */
function Notificacoes() {
  const { atividades, naoLidas, marcarLidas } = useAtividades()
  const { aberto, setAberto, ref } = usePopover()
  const navigate = useNavigate()
  const recentes = atividades.slice(0, 8)

  return (
    <div className="popover-wrap" ref={ref}>
      <button
        className="icon-btn"
        aria-label={`Notificações${naoLidas ? ` (${naoLidas} não lidas)` : ''}`}
        aria-expanded={aberto}
        onClick={() => {
          setAberto((a) => !a)
          if (!aberto && naoLidas) marcarLidas()
        }}
      >
        <Bell size={21} strokeWidth={1.7} />
        {naoLidas > 0 && <span className="icon-btn__dot" aria-hidden="true" />}
      </button>
      {aberto && (
        <Popover titulo="Notificações">
          {recentes.length === 0 ? (
            <p>Nenhuma notificação. O resultado dos seus envios de notas aparece aqui.</p>
          ) : (
            recentes.map((a) => (
              <button
                className="notif notif--link"
                key={a.id}
                onClick={() => {
                  setAberto(false)
                  navigate(rotaEmpresa(a.clienteId, 'documentos'))
                }}
              >
                <div className="notif__title">
                  {a.importadas.length > 0
                    ? `${a.importadas.length} nota(s) importada(s) · ${a.clienteNome}`
                    : `Nenhuma nota importada · ${a.clienteNome}`}
                </div>
                {a.rejeitadas.length > 0 && (
                  <div style={{ color: 'var(--badge-red-fg)', fontSize: 12.5 }}>
                    {a.rejeitadas.length} recusada(s): {a.rejeitadas[0]!.motivo}
                  </div>
                )}
                {a.erro && <div style={{ color: 'var(--badge-red-fg)', fontSize: 12.5 }}>{a.erro}</div>}
                <div className="notif__meta">{fmtDataHora(a.quando)}</div>
              </button>
            ))
          )}
        </Popover>
      )}
    </div>
  )
}

function MenuUsuario() {
  const { usuario, admin, sair } = useAuth()
  const { aberto, setAberto, ref } = usePopover()
  const navigate = useNavigate()
  if (!usuario) return null
  const config = admin ? '/dashboard/configuracoes' : rotaEmpresa(usuario.clienteId!, 'configuracoes')

  return (
    <div className="popover-wrap" ref={ref}>
      <button className="avatar" onClick={() => setAberto((a) => !a)} aria-expanded={aberto} aria-label={`Conta de ${usuario.nome}`}>
        {iniciais(usuario.nome) || usuario.nome.slice(0, 1).toUpperCase()}
      </button>
      {aberto && (
        <div className="popover menu-usuario" role="menu">
          <div className="menu-usuario__topo">
            <strong>{usuario.nome}</strong>
            <span>{usuario.email}</span>
            <span className="badge badge--blue badge--sm" style={{ marginTop: 6, alignSelf: 'flex-start' }}>
              {admin ? 'Administrador' : usuario.clienteNome}
            </span>
          </div>
          <button className="search__item" role="menuitem" onClick={() => { setAberto(false); navigate(config) }}>
            <Settings size={16} /> Configurações
          </button>
          <button
            className="search__item"
            role="menuitem"
            onClick={async () => {
              setAberto(false)
              await sair()
              navigate('/login', { replace: true })
            }}
          >
            <LogOut size={16} /> Sair
          </button>
        </div>
      )}
    </div>
  )
}
