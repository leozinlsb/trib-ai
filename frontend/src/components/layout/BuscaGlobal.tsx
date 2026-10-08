import { useEffect, useId, useMemo, useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { Building2, FileText, Search } from 'lucide-react'
import { useAuth, useDados } from '../../state/contexts'
import { rotaEmpresa, rotaNota } from '../../lib/rotas'
import { capitalizar, fmtCnpj, fmtData, fmtMoeda, nomeCliente, normalizar, TIPO_LABEL, tituloNota } from '../../lib/format'

interface Resultado {
  chave: string
  tipo: 'cliente' | 'nota'
  titulo: string
  sub: string
  destino: string
}

/** Busca nos clientes e nas notas já carregados da API (nome, CNPJ, número, chave, contraparte). */
export function BuscaGlobal() {
  const { clientes, notas } = useDados()
  const { admin, usuario } = useAuth()
  const navigate = useNavigate()
  const [termo, setTermo] = useState('')
  const [aberto, setAberto] = useState(false)
  const [ativo, setAtivo] = useState(0)
  const ref = useRef<HTMLDivElement>(null)
  const listaId = useId()

  const resultados = useMemo<Resultado[]>(() => {
    const q = normalizar(termo.trim())
    if (q.length < 2) return []
    const qDig = q.replace(/\D/g, '')
    const bate = (...campos: (string | null | undefined | number)[]) =>
      campos.some((c) => {
        if (c == null) return false
        const s = normalizar(String(c))
        return s.includes(q) || (qDig.length >= 3 && s.replace(/\D/g, '').includes(qDig))
      })

    const porId = new Map(clientes.map((c) => [c.id, c]))
    const rc: Resultado[] = clientes
      .filter((c) => bate(c.razaoSocial, c.nomeFantasia, c.cnpj, c.setor, c.municipio))
      .slice(0, 4)
      .map((c) => ({
        chave: `c${c.id}`, tipo: 'cliente', titulo: nomeCliente(c),
        sub: `${fmtCnpj(c.cnpj)} · ${c.municipio ?? ''}`, destino: rotaEmpresa(c.id),
      }))
    const rn: Resultado[] = notas
      .filter((n) => bate(n.numero, n.chave, n.contraparteNome, n.contraparteCnpj))
      .slice(0, 6)
      .map((n) => ({
        chave: `n${n.id}`, tipo: 'nota', titulo: `${tituloNota(n)} — ${capitalizar(n.contraparteNome)}`,
        sub: `${TIPO_LABEL[n.tipo]} · ${nomeCliente(porId.get(n.clienteId))} · ${fmtData(n.dataEmissao)} · ${fmtMoeda(n.valorTotal)}`,
        destino: rotaNota(n.clienteId, n.id),
      }))
    return [...rc, ...rn]
  }, [termo, clientes, notas])

  useEffect(() => {
    const fora = (e: MouseEvent) => {
      if (ref.current && !ref.current.contains(e.target as Node)) setAberto(false)
    }
    document.addEventListener('mousedown', fora)
    return () => document.removeEventListener('mousedown', fora)
  }, [])

  const ir = (r: Resultado) => {
    navigate(r.destino)
    setAberto(false)
    setTermo('')
  }

  const verTodos = () => {
    const q = encodeURIComponent(termo.trim())
    navigate(admin ? `/dashboard/documentos?q=${q}` : `${rotaEmpresa(usuario!.clienteId!, 'documentos')}?q=${q}`)
    setAberto(false)
  }

  const onKey = (e: React.KeyboardEvent) => {
    if (e.key === 'ArrowDown') {
      e.preventDefault()
      setAtivo((a) => Math.min(a + 1, resultados.length - 1))
    } else if (e.key === 'ArrowUp') {
      e.preventDefault()
      setAtivo((a) => Math.max(a - 1, 0))
    } else if (e.key === 'Enter') {
      e.preventDefault()
      const r = resultados[ativo]
      if (r) ir(r)
      else if (termo.trim().length >= 2) verTodos()
    } else if (e.key === 'Escape') {
      setAberto(false)
    }
  }

  const mostrar = aberto && termo.trim().length >= 2
  const clientesR = resultados.filter((r) => r.tipo === 'cliente')
  const notasR = resultados.filter((r) => r.tipo === 'nota')

  return (
    <div className="search" ref={ref}>
      <label className="search__box">
        <Search size={18} aria-hidden="true" />
        <span className="sr-only">Buscar no sistema</span>
        <input
          type="search"
          placeholder="Buscar no sistema..."
          value={termo}
          onChange={(e) => {
            setTermo(e.target.value)
            setAtivo(0)
            setAberto(true)
          }}
          onFocus={() => setAberto(true)}
          onKeyDown={onKey}
          role="combobox"
          aria-expanded={mostrar}
          aria-controls={listaId}
          aria-autocomplete="list"
        />
      </label>
      {mostrar && (
        <div className="search__results" id={listaId} role="listbox">
          {resultados.length === 0 && (
            <div className="search__empty">Nenhum cliente ou nota encontrado para “{termo.trim()}”.</div>
          )}
          {clientesR.length > 0 && <div className="search__group">Clientes</div>}
          {clientesR.map((r) => (
            <Item key={r.chave} r={r} ativo={resultados.indexOf(r) === ativo} onClick={() => ir(r)} />
          ))}
          {notasR.length > 0 && <div className="search__group">Notas fiscais</div>}
          {notasR.map((r) => (
            <Item key={r.chave} r={r} ativo={resultados.indexOf(r) === ativo} onClick={() => ir(r)} />
          ))}
          {resultados.length > 0 && (
            <button className="search__item" onClick={verTodos} style={{ color: 'var(--navy-700)', fontWeight: 500 }}>
              Ver todas as notas com “{termo.trim()}”
            </button>
          )}
        </div>
      )}
    </div>
  )
}

function Item({ r, ativo, onClick }: { r: Resultado; ativo: boolean; onClick: () => void }) {
  const Icone = r.tipo === 'cliente' ? Building2 : FileText
  return (
    <button className="search__item" role="option" aria-selected={ativo} onClick={onClick}>
      <Icone size={16} color="var(--text-3)" style={{ flex: 'none' }} />
      <span style={{ minWidth: 0 }}>
        <span style={{ display: 'block' }}>{r.titulo}</span>
        <span className="search__item-sub">{r.sub}</span>
      </span>
    </button>
  )
}
