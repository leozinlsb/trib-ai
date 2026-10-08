import { useEffect, useState } from 'react'
import { Outlet, useLocation } from 'react-router-dom'
import { Sidebar } from './Sidebar'
import { Topbar } from './Topbar'

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
          <Outlet />
        </main>
      </div>
    </div>
  )
}
