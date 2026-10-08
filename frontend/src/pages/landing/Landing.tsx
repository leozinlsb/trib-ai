import { useEffect, useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import {
  ArrowLeftRight, ArrowRight, Check, FileUp, LayoutDashboard, ListChecks, Menu, Search, Share2, ShieldCheck, X,
} from 'lucide-react'
import { Logo } from '../../components/layout/Logo'
import '../../styles/landing.css'

const NAV = [
  { href: '#inicio', rotulo: 'Início' },
  { href: '#funcionalidades', rotulo: 'Funcionalidades' },
  { href: '#como-funciona', rotulo: 'Como funciona' },
  { href: '#sobre', rotulo: 'Sobre a plataforma' },
]

/** Somente o que o sistema faz hoje. */
const RECURSOS = [
  {
    icone: FileUp,
    titulo: 'Importação de NF-e',
    texto: 'Envie um ou vários XMLs de uma vez. Cada nota é lida e validada automaticamente, com o motivo de qualquer recusa.',
  },
  {
    icone: ArrowLeftRight,
    titulo: 'Entradas e saídas',
    texto: 'O TribIA identifica se cada nota é uma compra ou uma venda do cliente e organiza tudo por competência.',
  },
  {
    icone: ListChecks,
    titulo: 'Classificação tributária',
    texto: 'Acompanhe quais itens já têm CST e cClassTrib da reforma tributária e quais ainda estão pendentes.',
  },
  {
    icone: Share2,
    titulo: 'Rede de relacionamentos',
    texto: 'Visualize os fornecedores e clientes finais ligados a cada empresa e o valor movimentado entre eles.',
  },
  {
    icone: LayoutDashboard,
    titulo: 'Painel de indicadores',
    texto: 'Volume de notas por semana ou mês, documentos recentes e indicadores de cada cliente em uma só tela.',
  },
  {
    icone: Search,
    titulo: 'Busca e histórico',
    texto: 'Encontre qualquer nota pelo número, chave de acesso, CNPJ ou nome da contraparte.',
  },
]

const PASSOS = [
  {
    titulo: 'Envie seus documentos',
    texto: 'Selecione o cliente e envie os XMLs das notas fiscais, um ou vários de uma vez.',
  },
  {
    titulo: 'O TribIA processa as informações',
    texto: 'Cada nota é lida, validada, identificada como entrada ou saída e organizada por competência.',
  },
  {
    titulo: 'Explore resultados e relacionamentos',
    texto: 'Acompanhe indicadores, a classificação tributária dos itens e a rede de fornecedores e clientes.',
  },
]

const SOBRE = [
  { titulo: 'Notas fiscais eletrônicas (NF-e)', texto: 'Modelo 55, layout 4.00, com os tributos de cada item.' },
  { titulo: 'Lucro Real e Lucro Presumido', texto: 'Clientes organizados com regime, setor e município.' },
  { titulo: 'Organização por competência', texto: 'Entradas e saídas agrupadas pelo mês de emissão.' },
  { titulo: 'Validação na importação', texto: 'Notas duplicadas, de outro cliente ou fora do padrão são recusadas com o motivo.' },
]

/** Revela os elementos .lp-revela quando entram na tela. */
function useRevelar() {
  const raiz = useRef<HTMLDivElement>(null)
  useEffect(() => {
    const els = raiz.current?.querySelectorAll('.lp-revela') ?? []
    if (!('IntersectionObserver' in window)) {
      els.forEach((el) => el.classList.add('is-visivel'))
      return
    }
    const io = new IntersectionObserver(
      (entradas) =>
        entradas.forEach((e) => {
          if (e.isIntersecting) {
            e.target.classList.add('is-visivel')
            io.unobserve(e.target)
          }
        }),
      { threshold: 0.12, rootMargin: '0px 0px -40px 0px' },
    )
    els.forEach((el) => io.observe(el))
    return () => io.disconnect()
  }, [])
  return raiz
}

export function Landing() {
  const raiz = useRevelar()
  const [rolado, setRolado] = useState(false)
  const [menu, setMenu] = useState(false)

  useEffect(() => {
    document.title = 'TribIA — Transforme documentos em inteligência'
    const onScroll = () => setRolado(window.scrollY > 8)
    onScroll()
    window.addEventListener('scroll', onScroll, { passive: true })
    return () => {
      window.removeEventListener('scroll', onScroll)
      document.title = 'TribIA'
    }
  }, [])

  return (
    <div className="lp" ref={raiz}>
      <header className={`lp-header${rolado ? ' is-rolado' : ''}`}>
        <div className="lp-container lp-header__inner">
          <a href="#inicio" className="lp-header__logo" aria-label="TribIA — início">
            <Logo tamanho={32} fundo="claro" />
          </a>
          <nav className="lp-nav" aria-label="Seções">
            {NAV.map((n) => (
              <a key={n.href} href={n.href}>{n.rotulo}</a>
            ))}
          </nav>
          <div className="lp-header__acoes">
            <Link to="/login" className="lp-btn lp-btn--fantasma">Entrar</Link>
            <Link to="/entrar" className="lp-btn lp-btn--primario lp-btn--sm">Começar agora</Link>
          </div>
          <button
            className="icon-btn lp-header__menu"
            onClick={() => setMenu((m) => !m)}
            aria-expanded={menu}
            aria-controls="lp-menu"
            aria-label={menu ? 'Fechar menu' : 'Abrir menu'}
          >
            {menu ? <X size={20} /> : <Menu size={20} />}
          </button>
        </div>
        <div className="lp-mobile" id="lp-menu" hidden={!menu}>
          {NAV.map((n) => (
            <a key={n.href} href={n.href} className="lp-mobile__link" onClick={() => setMenu(false)}>
              {n.rotulo}
            </a>
          ))}
          <div className="lp-mobile__acoes">
            <Link to="/login" className="lp-btn lp-btn--secundario lp-btn--sm">Entrar</Link>
            <Link to="/entrar" className="lp-btn lp-btn--primario lp-btn--sm">Começar agora</Link>
          </div>
        </div>
      </header>

      <main>
        {/* ---------- Hero ---------- */}
        <section id="inicio" className="lp-hero">
          <div className="lp-container lp-hero__grid">
            <div className="lp-revela">
              <span className="lp-selo">
                <span className="lp-selo__ponto" aria-hidden="true" />
                Reforma tributária · NF-e
              </span>
              <h1>
                Transforme documentos em <em>inteligência.</em>
              </h1>
              <p className="lp-hero__sub">
                O TribIA utiliza inteligência artificial para analisar documentos, identificar informações relevantes e
                transformar dados em conhecimento de forma organizada e eficiente.
              </p>
              <div className="lp-hero__ctas">
                <Link to="/entrar" className="lp-btn lp-btn--primario">
                  Começar agora <ArrowRight size={17} />
                </Link>
                <a href="#funcionalidades" className="lp-btn lp-btn--secundario">
                  Conhecer funcionalidades
                </a>
              </div>
              <ul className="lp-hero__fatos">
                <li><Check size={16} strokeWidth={2.5} /> XML de NF-e</li>
                <li><Check size={16} strokeWidth={2.5} /> Entradas e saídas por competência</li>
                <li><Check size={16} strokeWidth={2.5} /> CST e cClassTrib</li>
              </ul>
            </div>

            <div className="lp-previa lp-revela" style={{ transitionDelay: '120ms' }}>
              <div className="lp-janela">
                <div className="lp-janela__barra" aria-hidden="true">
                  <span />
                  <span />
                  <span />
                  <div className="lp-janela__url">tribia · Painel Geral</div>
                </div>
                <img
                  src="/landing/painel.jpg"
                  width={1920}
                  height={1200}
                  alt="Painel Geral do TribIA com indicadores, volume de notas, rede de relacionamentos e documentos recentes"
                  fetchPriority="high"
                />
              </div>
              <div className="lp-flutuante lp-flutuante--kpi" aria-hidden="true">
                <img src="/landing/kpi-documentos.jpg" alt="" loading="lazy" />
              </div>
              <div className="lp-flutuante lp-flutuante--rede" aria-hidden="true">
                <img src="/landing/rede.jpg" alt="" loading="lazy" />
              </div>
            </div>
          </div>
        </section>

        {/* ---------- Funcionalidades ---------- */}
        <section id="funcionalidades" className="lp-secao">
          <div className="lp-container">
            <div className="lp-titulo lp-titulo--centro lp-revela">
              <span className="lp-sobretitulo">Funcionalidades</span>
              <h2>Do XML à visão completa de cada cliente</h2>
              <p>Tudo o que o escritório precisa para organizar e acompanhar as notas fiscais dos clientes.</p>
            </div>
            <div className="lp-recursos lp-revela">
              {RECURSOS.map(({ icone: Icone, titulo, texto }) => (
                <article key={titulo} className="lp-recurso">
                  <span className="lp-recurso__icone" aria-hidden="true">
                    <Icone size={20} strokeWidth={1.9} />
                  </span>
                  <h3>{titulo}</h3>
                  <p>{texto}</p>
                </article>
              ))}
            </div>
          </div>
        </section>

        {/* ---------- Como funciona ---------- */}
        <section id="como-funciona" className="lp-secao lp-secao--cinza">
          <div className="lp-container">
            <div className="lp-titulo lp-titulo--centro lp-revela">
              <span className="lp-sobretitulo">Como funciona</span>
              <h2>Três passos, do envio à análise</h2>
            </div>
            <ol className="lp-passos lp-revela">
              {PASSOS.map((p, i) => (
                <li key={p.titulo} className="lp-passo">
                  <div className="lp-passo__num">{i + 1}</div>
                  <h3>{p.titulo}</h3>
                  <p>{p.texto}</p>
                </li>
              ))}
            </ol>
          </div>
        </section>

        {/* ---------- Sobre ---------- */}
        <section id="sobre" className="lp-secao">
          <div className="lp-container lp-sobre">
            <div className="lp-titulo lp-revela">
              <span className="lp-sobretitulo">Sobre a plataforma</span>
              <h2>Feito para a rotina dos escritórios de contabilidade</h2>
              <p>
                O TribIA reúne as notas fiscais dos clientes do escritório em um só lugar e ajuda a acompanhar a
                classificação tributária dos itens na transição para a reforma tributária, com CBS, IBS e Imposto
                Seletivo.
              </p>
              <p>O TribIA é um projeto desenvolvido em ambiente universitário.</p>
            </div>
            <ul className="lp-sobre__lista lp-revela">
              {SOBRE.map((s) => (
                <li key={s.titulo}>
                  <ShieldCheck size={20} />
                  <div>
                    <strong>{s.titulo}</strong>
                    <span>{s.texto}</span>
                  </div>
                </li>
              ))}
            </ul>
          </div>
        </section>

        {/* ---------- Chamada final ---------- */}
        <section className="lp-secao" style={{ paddingTop: 0 }}>
          <div className="lp-container">
            <div className="lp-cta lp-revela">
              <div>
                <h2>Uma nova forma de compreender seus dados.</h2>
                <p>
                  Centralize documentos, explore informações e aproveite o potencial da inteligência artificial em uma
                  única plataforma.
                </p>
              </div>
              <Link to="/entrar" className="lp-btn lp-btn--verde">
                Acessar TribIA <ArrowRight size={17} />
              </Link>
            </div>
          </div>
        </section>
      </main>

      <footer className="lp-footer">
        <div className="lp-container">
          <div className="lp-footer__topo">
            <a href="#inicio" className="lp-header__logo" aria-label="TribIA — início">
              <Logo tamanho={28} fundo="claro" />
            </a>
            <nav aria-label="Rodapé">
              {NAV.map((n) => (
                <a key={n.href} href={n.href}>{n.rotulo}</a>
              ))}
              <Link to="/entrar">Acessar o sistema</Link>
            </nav>
          </div>
          <div className="lp-footer__base">
            <span>© {new Date().getFullYear()} TribIA</span>
            <span>Projeto universitário</span>
          </div>
        </div>
      </footer>
    </div>
  )
}
