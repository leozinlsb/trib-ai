import { lazy, Suspense, type ReactNode } from 'react'
import { BrowserRouter, Link, Navigate, Outlet, Route, Routes, useLocation, useParams } from 'react-router-dom'
import { LoaderCircle } from 'lucide-react'
import { AppLayout } from './components/layout/AppLayout'
import { Vazio } from './components/ui'
import { Configuracoes } from './pages/admin/Configuracoes'
import { Documentos } from './pages/Documentos'
import { Empresas } from './pages/admin/Empresas'
import { Relatorios } from './pages/admin/Relatorios'
import { VisaoGeral } from './pages/admin/VisaoGeral'
import { AnalisesRelatorios } from './pages/empresa/AnalisesRelatorios'
import { ConfiguracoesEmpresa } from './pages/empresa/ConfiguracoesEmpresa'
import { InicioEmpresa } from './pages/empresa/InicioEmpresa'
import { Revisao } from './pages/empresa/Revisao'
import { Landing } from './pages/landing/Landing'
import { Login } from './pages/Login'
import { NotaDetalhe } from './pages/NotaDetalhe'
import { AnaliseFiscal } from './pages/fiscal/AnaliseFiscal'
import { InteligenciaFiscal } from './pages/fiscal/InteligenciaFiscal'
import { InteligenciaFiscalAdmin } from './pages/fiscal/InteligenciaFiscalAdmin'
import { NovaAnalise } from './pages/fiscal/NovaAnalise'
import { AtividadeProvider } from './state/AtividadeProvider'
import { AuthProvider } from './state/AuthProvider'
import { inicioDoUsuario, useAuth, useDados } from './state/contexts'
import { DadosProvider } from './state/DadosProvider'
import { PreferenciasProvider } from './state/PreferenciasProvider'
import { ToastProvider } from './state/ToastProvider'
import { rotaNota } from './lib/rotas'

/** Tela de exemplo da Inteligência Fiscal: só existe em desenvolvimento (fica fora do build de produção). */
const ExemploAnalise = import.meta.env.DEV ? lazy(() => import('./pages/fiscal/ExemploAnalise')) : null

function Carregando() {
  return (
    <div style={{ minHeight: '100vh', display: 'grid', placeItems: 'center', color: 'var(--text-3)' }}>
      <LoaderCircle size={28} className="spin" aria-label="Carregando" />
    </div>
  )
}

/** Exige sessão; sem ela, vai ao login e volta para cá depois. */
function RotaProtegida({ children }: { children: ReactNode }) {
  const { usuario, status } = useAuth()
  const location = useLocation()
  if (status === 'verificando') return <Carregando />
  if (!usuario) return <Navigate to="/login" replace state={{ de: location.pathname + location.search }} />
  return children
}

/** Área interna: os dados só são carregados depois do login. */
function Sistema() {
  return (
    <RotaProtegida>
      <DadosProvider>
        <AtividadeProvider>
          <AppLayout />
        </AtividadeProvider>
      </DadosProvider>
    </RotaProtegida>
  )
}

/** Telas administrativas: usuário de empresa volta para a própria empresa. */
function SoAdmin() {
  const { usuario, admin } = useAuth()
  return admin ? <Outlet /> : <Navigate to={inicioDoUsuario(usuario)} replace />
}

/** Usuário de empresa só navega na própria empresa (o servidor também recusa as demais). */
function AmbienteEmpresa() {
  const { usuario, admin } = useAuth()
  const { empresaId } = useParams()
  if (!admin && Number(empresaId) !== usuario?.clienteId) return <Navigate to={inicioDoUsuario(usuario)} replace />
  return <Outlet />
}

function Inicio() {
  const { usuario, admin } = useAuth()
  return admin ? <VisaoGeral /> : <Navigate to={inicioDoUsuario(usuario)} replace />
}

/** Endereço antigo de nota (/dashboard/documentos/:id): leva ao ambiente da empresa dona da nota. */
function NotaAntiga() {
  const { id } = useParams()
  const { notas, status } = useDados()
  const nota = notas.find((n) => n.id === Number(id))
  if (status === 'carregando') return null
  return nota ? <Navigate to={rotaNota(nota.clienteId, nota.id)} replace /> : <Navigate to="/dashboard/documentos" replace />
}

function NaoEncontrada() {
  return (
    <div className="card">
      <Vazio titulo="Página não encontrada">
        <Link to="/dashboard">Voltar ao início</Link>
      </Vazio>
    </div>
  )
}

function AcessarSistema() {
  const { usuario, status } = useAuth()
  if (status === 'verificando') return <Carregando />
  return <Navigate to={usuario ? inicioDoUsuario(usuario) : '/login'} replace />
}

export default function App() {
  return (
    <BrowserRouter>
      <ToastProvider>
        <PreferenciasProvider>
          <AuthProvider>
            <Routes>
              <Route index element={<Landing />} />
              <Route path="login" element={<Login />} />
              {/* contas são criadas pelo administrador: não há cadastro público */}
              <Route path="cadastro" element={<Navigate to="/login" replace />} />
              <Route path="entrar" element={<AcessarSistema />} />

              <Route path="dashboard" element={<Sistema />}>
                <Route index element={<Inicio />} />

                <Route element={<SoAdmin />}>
                  <Route path="empresas" element={<Empresas />} />
                  <Route path="documentos" element={<Documentos />} />
                  <Route path="documentos/:id" element={<NotaAntiga />} />
                  <Route path="inteligencia-fiscal" element={<InteligenciaFiscalAdmin />} />
                  <Route path="relatorios" element={<Relatorios />} />
                  <Route path="configuracoes" element={<Configuracoes />} />
                  {['analises', 'redes', 'processos', 'historico'].map((p) => (
                    <Route key={p} path={p} element={<Navigate to="/dashboard" replace />} />
                  ))}
                </Route>

                <Route path="empresas/:empresaId" element={<AmbienteEmpresa />}>
                  <Route index element={<InicioEmpresa />} />
                  <Route path="documentos" element={<Documentos />} />
                  <Route path="documentos/:id" element={<NotaDetalhe />} />
                  <Route path="revisao" element={<Revisao />} />
                  <Route path="inteligencia-fiscal" element={<InteligenciaFiscal />} />
                  <Route path="inteligencia-fiscal/nova" element={<NovaAnalise />} />
                  {ExemploAnalise && (
                    <Route
                      path="inteligencia-fiscal/exemplo"
                      element={<Suspense fallback={null}><ExemploAnalise /></Suspense>}
                    />
                  )}
                  <Route path="inteligencia-fiscal/:analiseId" element={<AnaliseFiscal />} />
                  <Route path="analises" element={<AnalisesRelatorios />} />
                  <Route path="configuracoes" element={<ConfiguracoesEmpresa />} />
                </Route>

                <Route path="*" element={<NaoEncontrada />} />
              </Route>

              <Route path="*" element={<Navigate to="/" replace />} />
            </Routes>
          </AuthProvider>
        </PreferenciasProvider>
      </ToastProvider>
    </BrowserRouter>
  )
}
