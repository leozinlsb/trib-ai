import { useEffect, useState, type FormEvent } from 'react'
import { Check, Eye, EyeOff, LoaderCircle, Trash2, UserPlus, X } from 'lucide-react'
import { ApiError } from '../../api/client'
import { criarUsuario, listarUsuarios, removerUsuario } from '../../api/tribia'
import type { Cliente, Usuario } from '../../api/types'
import { fmtDataHora } from '../../lib/format'
import { useToast } from '../../state/contexts'
import { Aviso, Card, Carregando, Confirmacao, ErroEstado, Vazio } from '../ui'

const REQUISITOS = [
  { rotulo: 'Pelo menos 8 caracteres', ok: (s: string) => s.length >= 8 },
  { rotulo: 'Letras e números', ok: (s: string) => /[A-Za-z]/.test(s) && /\d/.test(s) },
]

/** Usuários com acesso à empresa (somente administrador cria e remove). */
export function AcessosEmpresa({ empresa }: { empresa: Cliente }) {
  const { mostrar } = useToast()
  const [usuarios, setUsuarios] = useState<Usuario[] | null>(null)
  const [erro, setErro] = useState<string | null>(null)
  const [tentativa, setTentativa] = useState(0)
  const [criando, setCriando] = useState(false)
  const [removendo, setRemovendo] = useState<Usuario | null>(null)

  useEffect(() => {
    const ctrl = new AbortController()
    setErro(null)
    listarUsuarios(empresa.id, ctrl.signal)
      .then(setUsuarios)
      .catch((e) => {
        if (e instanceof DOMException && e.name === 'AbortError') return
        setErro(e instanceof Error ? e.message : 'Falha ao carregar os acessos.')
      })
    return () => ctrl.abort()
  }, [empresa.id, tentativa])

  return (
    <Card
      titulo="Acessos da empresa"
      sub="Pessoas da empresa que podem entrar no TribIA. Cada uma vê somente os dados desta empresa."
      acoes={
        !criando && (
          <button className="btn btn--secondary btn--sm" onClick={() => setCriando(true)}>
            <UserPlus size={15} /> Novo acesso
          </button>
        )
      }
    >
      {criando && (
        <NovoAcesso
          empresaId={empresa.id}
          onCancelar={() => setCriando(false)}
          onCriado={(u) => {
            setCriando(false)
            setUsuarios((l) => [...(l ?? []), u].sort((a, b) => a.nome.localeCompare(b.nome)))
            mostrar({ tipo: 'sucesso', titulo: 'Acesso criado', texto: `${u.nome} já pode entrar com ${u.email}.` })
          }}
        />
      )}

      {erro ? (
        <ErroEstado mensagem={erro} onTentar={() => setTentativa((t) => t + 1)} />
      ) : !usuarios ? (
        <Carregando linhas={2} />
      ) : usuarios.length === 0 ? (
        !criando && <Vazio titulo="Nenhum acesso criado">Crie um acesso para que a empresa possa enviar notas e consultar relatórios.</Vazio>
      ) : (
        <ul className="acessos">
          {usuarios.map((u) => (
            <li key={u.id}>
              <span className="avatar avatar--sm" aria-hidden="true">{u.nome.slice(0, 1).toUpperCase()}</span>
              <div style={{ minWidth: 0, flex: 1 }}>
                <div className="cell-main">{u.nome}</div>
                <div className="cell-sub">{u.email} · criado em {fmtDataHora(u.criadoEm)}</div>
              </div>
              <button className="icon-btn" onClick={() => setRemovendo(u)} aria-label={`Remover acesso de ${u.nome}`} title="Remover acesso">
                <Trash2 size={16} />
              </button>
            </li>
          ))}
        </ul>
      )}

      {removendo && (
        <Confirmacao
          titulo={`Remover o acesso de ${removendo.nome}?`}
          rotulo="Remover acesso"
          onFechar={() => setRemovendo(null)}
          onConfirmar={async () => {
            await removerUsuario(removendo.id)
            setUsuarios((l) => (l ?? []).filter((x) => x.id !== removendo.id))
            mostrar({ tipo: 'sucesso', titulo: 'Acesso removido' })
            setRemovendo(null)
          }}
        >
          <p>{removendo.email} não poderá mais entrar no TribIA. Os documentos da empresa não são afetados.</p>
        </Confirmacao>
      )}
    </Card>
  )
}

function NovoAcesso({ empresaId, onCriado, onCancelar }: {
  empresaId: number
  onCriado: (u: Usuario) => void
  onCancelar: () => void
}) {
  const [nome, setNome] = useState('')
  const [email, setEmail] = useState('')
  const [senha, setSenha] = useState('')
  const [ver, setVer] = useState(false)
  const [salvando, setSalvando] = useState(false)
  const [erro, setErro] = useState<string | null>(null)
  const [tentou, setTentou] = useState(false)

  const erros = {
    nome: !nome.trim() ? 'Informe o nome.' : null,
    email: !email.trim() ? 'Informe o e-mail.' : !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email.trim()) ? 'E-mail inválido.' : null,
    senha: REQUISITOS.every((r) => r.ok(senha)) ? null : 'A senha não atende aos requisitos.',
  }

  const enviar = async (e: FormEvent) => {
    e.preventDefault()
    setTentou(true)
    if (erros.nome || erros.email || erros.senha) return
    setSalvando(true)
    setErro(null)
    try {
      onCriado(await criarUsuario(empresaId, { nome: nome.trim(), email: email.trim(), senha }))
    } catch (err) {
      setErro(err instanceof ApiError ? err.message : 'Não foi possível criar o acesso.')
      setSalvando(false)
    }
  }

  return (
    <form className="novo-acesso" onSubmit={enviar} noValidate>
      {erro && <Aviso tipo="error">{erro}</Aviso>}
      <div className="form-grid">
        <div className="field">
          <label className="field__label" htmlFor="na-nome">Nome</label>
          <input id="na-nome" className={`input${tentou && erros.nome ? ' input--erro' : ''}`} value={nome} onChange={(e) => setNome(e.target.value)} autoFocus disabled={salvando} />
          {tentou && erros.nome && <span className="field__erro">{erros.nome}</span>}
        </div>
        <div className="field">
          <label className="field__label" htmlFor="na-email">E-mail</label>
          <input id="na-email" type="email" autoComplete="off" className={`input${tentou && erros.email ? ' input--erro' : ''}`} value={email} onChange={(e) => setEmail(e.target.value)} disabled={salvando} />
          {tentou && erros.email && <span className="field__erro">{erros.email}</span>}
        </div>
        <div className="field span-2">
          <label className="field__label" htmlFor="na-senha">Senha inicial</label>
          <div className="input-senha">
            <input
              id="na-senha"
              type={ver ? 'text' : 'password'}
              autoComplete="new-password"
              className={`input${tentou && erros.senha ? ' input--erro' : ''}`}
              value={senha}
              onChange={(e) => setSenha(e.target.value)}
              disabled={salvando}
            />
            <button type="button" className="icon-btn" onClick={() => setVer((v) => !v)} aria-label={ver ? 'Ocultar senha' : 'Mostrar senha'}>
              {ver ? <EyeOff size={16} /> : <Eye size={16} />}
            </button>
          </div>
          <ul className="requisitos" aria-live="polite">
            {REQUISITOS.map((r) => {
              const ok = r.ok(senha)
              return (
                <li key={r.rotulo} className={ok ? 'ok' : ''}>
                  {ok ? <Check size={13} /> : <X size={13} />} {r.rotulo}
                </li>
              )
            })}
          </ul>
          {tentou && erros.senha && <span className="field__erro">{erros.senha}</span>}
          <span className="cell-sub">Informe a senha à pessoa por um canal seguro.</span>
        </div>
      </div>
      <div className="form-acoes">
        <button type="button" className="btn btn--secondary btn--sm" onClick={onCancelar} disabled={salvando}>Cancelar</button>
        <button type="submit" className="btn btn--primary btn--sm" disabled={salvando}>
          {salvando && <LoaderCircle size={14} className="spin" />} Criar acesso
        </button>
      </div>
    </form>
  )
}
