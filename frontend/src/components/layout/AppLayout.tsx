import { useEffect, useState } from 'react'
import { Outlet, useLocation } from 'react-router-dom'
import { Sidebar } from './Sidebar'
import { Topbar } from './Topbar'
import { Aviso } from '../ui'

export function AppLayout() {
  const [menuAberto, setMenuAberto] = useState(false)
  const { pathname } = useLocation()

  useEffect(() => {
    window.scrollTo(0, 0)
  }, [pathname])

  return (
    <div className={`app${menuAberto ? ' nav-open' : ''}`}>
      <a href="#conteudo" className="sr-only">Pular para o conteúdo</a>
      <Sidebar onNavegar={() => setMenuAberto(false)} />
      <div className="backdrop" onClick={() => setMenuAberto(false)} aria-hidden="true" />
      <div className="main">
        <Topbar onMenu={() => setMenuAberto(true)} />
        <main className="content" id="conteudo">
          <div role="note" aria-label="Limitação dos resultados tributários">
            <Aviso tipo="warn" style={{ marginBottom: 16 }}>
              Resultados tributários são estimativas e exigem revisão profissional. Classificações automáticas
              não substituem validação fiscal; não utilize comparativos ou relatórios como apuração definitiva.
            </Aviso>
          </div>
          <Outlet />
        </main>
      </div>
    </div>
  )
}
