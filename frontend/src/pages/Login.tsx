import { useEffect, useState, type FormEvent } from 'react'
import { Link, Navigate, useLocation, useNavigate } from 'react-router-dom'
import { ArrowLeft, Check, Eye, EyeOff, LoaderCircle } from 'lucide-react'
import { ApiError } from '../api/client'
import { Logo } from '../components/layout/Logo'
import { Aviso } from '../components/ui'
import { inicioDoUsuario, useAuth } from '../state/contexts'
import '../styles/auth.css'

const EMAIL = /^[^\s@]+@[^\s@]+\.[^\s@]+$/

export function Login() {
  const { usuario, status, entrar } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const destino = (location.state as { de?: string } | null)?.de

  const [email, setEmail] = useState('')
  const [senha, setSenha] = useState('')
  const [verSenha, setVerSenha] = useState(false)
  const [enviando, setEnviando] = useState(false)
  const [erro, setErro] = useState<string | null>(null)
  const [tocado, setTocado] = useState({ email: false, senha: false })

  useEffect(() => {
    document.title = 'Entrar · TribIA'
    return () => {
      document.title = 'TribIA'
    }
  }, [])

  if (status === 'pronto' && usuario) {
    return <Navigate to={destino ?? inicioDoUsuario(usuario)} replace />
  }

  const erroEmail = !email.trim() ? 'Informe o e-mail.' : !EMAIL.test(email.trim()) ? 'E-mail inválido.' : null
  const erroSenha = !senha ? 'Informe a senha.' : null

  const enviar = async (e: FormEvent) => {
    e.preventDefault()
    setTocado({ email: true, senha: true })
    if (erroEmail || erroSenha) return
    setEnviando(true)
    setErro(null)
    try {
      const u = await entrar(email.trim(), senha)
      // usuário de empresa só pode voltar para rotas da própria empresa
      const permitido = destino && (u.papel === 'ADMIN' || destino.startsWith(inicioDoUsuario(u)))
      navigate(permitido ? destino : inicioDoUsuario(u), { replace: true })
    } catch (err) {
      setErro(err instanceof ApiError ? err.message : 'Não foi possível entrar. Tente novamente.')
      setEnviando(false)
    }
  }

  return (
    <div className="auth">
      <aside className="auth__marca">
        <Link to="/" className="auth__logo" aria-label="TribIA — página inicial">
          <Logo tamanho={36} />
        </Link>
        <div className="auth__mensagem">
          <h1>Bem-vindo de volta.</h1>
          <p>Acesse o ambiente da sua empresa para enviar notas fiscais, acompanhar análises e consultar relatórios.</p>
          <ul>
            <li><Check size={16} strokeWidth={2.5} /> Notas fiscais organizadas por empresa</li>
            <li><Check size={16} strokeWidth={2.5} /> Classificação tributária dos itens</li>
            <li><Check size={16} strokeWidth={2.5} /> Relatórios com apuração de PIS/Cofins</li>
          </ul>
        </div>
        <span className="auth__rodape">TribIA · Projeto universitário</span>
      </aside>

      <main className="auth__conteudo">
        <Link to="/" className="auth__voltar">
          <ArrowLeft size={16} /> Voltar ao site
        </Link>
        <div className="auth__caixa">
          <div className="auth__logo-mobile">
            <Logo tamanho={32} fundo="claro" />
          </div>
          <h2>Entrar no TribIA</h2>
          <p className="auth__sub">Use o e-mail e a senha fornecidos pelo escritório.</p>

          {erro && (
            <Aviso tipo="error" style={{ marginTop: 20 }}>
              <span role="alert">{erro}</span>
            </Aviso>
          )}

          <form className="auth__form" onSubmit={enviar} noValidate>
            <div className="field">
              <label className="field__label" htmlFor="login-email">E-mail</label>
              <input
                id="login-email"
                className={`input input--lg${tocado.email && erroEmail ? ' input--erro' : ''}`}
                type="email"
                autoComplete="email"
                autoFocus
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                onBlur={() => setTocado((t) => ({ ...t, email: true }))}
                aria-invalid={tocado.email && !!erroEmail}
                aria-describedby={tocado.email && erroEmail ? 'login-email-erro' : undefined}
                disabled={enviando}
              />
              {tocado.email && erroEmail && <span id="login-email-erro" className="field__erro">{erroEmail}</span>}
            </div>

            <div className="field">
              <label className="field__label" htmlFor="login-senha">Senha</label>
              <div className="input-senha">
                <input
                  id="login-senha"
                  className={`input input--lg${tocado.senha && erroSenha ? ' input--erro' : ''}`}
                  type={verSenha ? 'text' : 'password'}
                  autoComplete="current-password"
                  value={senha}
                  onChange={(e) => setSenha(e.target.value)}
                  onBlur={() => setTocado((t) => ({ ...t, senha: true }))}
                  aria-invalid={tocado.senha && !!erroSenha}
                  aria-describedby={tocado.senha && erroSenha ? 'login-senha-erro' : undefined}
                  disabled={enviando}
                />
                <button
                  type="button"
                  className="icon-btn"
                  onClick={() => setVerSenha((v) => !v)}
                  aria-label={verSenha ? 'Ocultar senha' : 'Mostrar senha'}
                >
                  {verSenha ? <EyeOff size={17} /> : <Eye size={17} />}
                </button>
              </div>
              {tocado.senha && erroSenha && <span id="login-senha-erro" className="field__erro">{erroSenha}</span>}
            </div>

            <button className="btn btn--primary auth__botao" type="submit" disabled={enviando || status !== 'pronto'}>
              {enviando && <LoaderCircle size={17} className="spin" />}
              {enviando ? 'Entrando...' : 'Entrar'}
            </button>
          </form>

          <p className="auth__ajuda">
            Não tem acesso ou esqueceu a senha? As contas são criadas e gerenciadas pelo administrador do escritório.
          </p>
        </div>
      </main>
    </div>
  )
}
