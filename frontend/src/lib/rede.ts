import type { Cliente, NotaResumo } from '../api/types'
import { capitalizar, nomeCliente } from './format'

export type PapelNo = 'cliente' | 'contraparte'

export interface No {
  id: string
  /** CNPJ/CPF (somente dígitos) quando conhecido */
  documento: string | null
  nome: string
  papel: PapelNo
  clienteId?: number
  /** vendeu para algum cliente (fornecedor) */
  fornecedor: boolean
  /** comprou de algum cliente (cliente final) */
  comprador: boolean
  notas: number
  valor: number
}

export interface Aresta {
  id: string
  origem: string
  destino: string
  notas: number
  valor: number
  entradas: number
  saidas: number
  notaIds: number[]
}

export interface Rede {
  nos: No[]
  arestas: Aresta[]
}

/**
 * Rede real a partir das notas: cada cliente do escritório liga-se às contrapartes das suas notas
 * (fornecedores nas entradas, clientes finais nas saídas). Se a contraparte for outro cliente do
 * escritório, a ligação é feita entre os dois clientes.
 */
export function montarRede(clientes: Cliente[], notas: NotaResumo[]): Rede {
  const nos = new Map<string, No>()
  const arestas = new Map<string, Aresta>()
  const porCnpj = new Map(clientes.map((c) => [c.cnpj, c]))

  for (const c of clientes) {
    nos.set(`c${c.id}`, {
      id: `c${c.id}`, documento: c.cnpj, nome: nomeCliente(c), papel: 'cliente', clienteId: c.id,
      fornecedor: false, comprador: false, notas: 0, valor: 0,
    })
  }

  for (const n of notas) {
    const origem = `c${n.clienteId}`
    if (!nos.has(origem)) continue
    const doc = n.contraparteCnpj
    const outroCliente = doc ? porCnpj.get(doc) : undefined
    const destino = outroCliente ? `c${outroCliente.id}` : doc ? `p${doc}` : `n${n.contraparteNome ?? n.id}`

    if (!nos.has(destino)) {
      nos.set(destino, {
        id: destino, documento: doc, nome: capitalizar(n.contraparteNome) || 'Sem identificação',
        papel: 'contraparte', fornecedor: false, comprador: false, notas: 0, valor: 0,
      })
    }
    const alvo = nos.get(destino)!
    if (n.tipo === 'ENTRADA') alvo.fornecedor = true
    else alvo.comprador = true
    alvo.notas += 1
    alvo.valor += n.valorTotal
    const cli = nos.get(origem)!
    cli.notas += 1
    cli.valor += n.valorTotal

    const chave = [origem, destino].sort().join('|')
    const a = arestas.get(chave) ?? {
      id: chave, origem, destino, notas: 0, valor: 0, entradas: 0, saidas: 0, notaIds: [],
    }
    a.notas += 1
    a.valor += n.valorTotal
    if (n.tipo === 'ENTRADA') a.entradas += 1
    else a.saidas += 1
    a.notaIds.push(n.id)
    arestas.set(chave, a)
  }

  return { nos: [...nos.values()], arestas: [...arestas.values()] }
}

export function papelNo(n: No) {
  if (n.papel === 'cliente') return 'Cliente do escritório'
  if (n.fornecedor && n.comprador) return 'Fornecedor e cliente final'
  return n.fornecedor ? 'Fornecedor' : 'Cliente final'
}

export interface Posicao {
  x: number
  y: number
  /** direção "para fora" do cluster, em radianos (posiciona rótulos) */
  ang: number
}

export interface Orbita {
  /** id do cliente no centro da órbita */
  id: string
  x: number
  y: number
  r: number
}

export interface Layout {
  pos: Map<string, Posicao>
  orbitas: Orbita[]
}

/** Abertura deixada embaixo de cada cliente, onde fica o rótulo dele (rad). */
const ABERTURA = (70 * Math.PI) / 180

/**
 * Layout em constelação, determinístico: cada cliente do escritório fica no centro de uma órbita e as
 * contrapartes exclusivas dele se distribuem nela, deixando uma abertura embaixo para o rótulo.
 * Contrapartes ligadas a mais de um cliente ficam entre eles. O desenho é encaixado na área com margem.
 */
export function layoutConstelacao(rede: Rede, largura: number, altura: number, margem: number): Layout {
  const porId = new Map(rede.nos.map((n) => [n.id, n]))
  const hubs = rede.nos.filter((n) => n.papel === 'cliente')
  const ligacoes = new Map<string, Set<string>>() // contraparte -> clientes
  for (const a of rede.arestas) {
    if (porId.get(a.destino)?.papel !== 'contraparte') continue
    ligacoes.set(a.destino, (ligacoes.get(a.destino) ?? new Set()).add(a.origem))
  }

  // 1. clientes em círculo, afastados o bastante para as órbitas (raio 1) não se tocarem
  const ORBITA = 1
  const n = hubs.length
  const R = n <= 1 ? 0 : (ORBITA * 1.5) / Math.sin(Math.PI / n)
  const bruto = new Map<string, Posicao>()
  hubs.forEach((h, i) => {
    const ang = -Math.PI / 2 + (i / Math.max(n, 1)) * Math.PI * 2
    bruto.set(h.id, { x: Math.cos(ang) * R, y: Math.sin(ang) * R, ang })
  })

  // 2. satélites: contrapartes de um único cliente, na órbita dele
  const compartilhadas: string[] = []
  const satelites = new Map<string, string[]>()
  for (const [id, cs] of ligacoes) {
    if (cs.size === 1) {
      const [c] = cs
      satelites.set(c!, [...(satelites.get(c!) ?? []), id])
    } else {
      compartilhadas.push(id)
    }
  }
  const inicio = Math.PI / 2 + ABERTURA / 2
  const arco = Math.PI * 2 - ABERTURA
  for (const h of hubs) {
    const sats = satelites.get(h.id) ?? []
    const c = bruto.get(h.id)!
    sats.forEach((id, j) => {
      const ang = sats.length === 1 ? -Math.PI / 2 : inicio + (arco * (j + 0.5)) / sats.length
      bruto.set(id, { x: c.x + Math.cos(ang) * ORBITA, y: c.y + Math.sin(ang) * ORBITA, ang })
    })
  }

  // 3. contrapartes de vários clientes: no meio deles
  compartilhadas.forEach((id, j) => {
    const cs = [...ligacoes.get(id)!].map((c) => bruto.get(c)!)
    const mx = cs.reduce((s, p) => s + p.x, 0) / cs.length
    const my = cs.reduce((s, p) => s + p.y, 0) / cs.length
    const off = compartilhadas.length > 1 ? 0.35 : 0
    const a = (j / compartilhadas.length) * Math.PI * 2
    bruto.set(id, { x: mx + Math.cos(a) * off, y: my + Math.sin(a) * off, ang: a - Math.PI / 2 })
  })

  for (const no of rede.nos) if (!bruto.has(no.id)) bruto.set(no.id, { x: 0, y: 0, ang: -Math.PI / 2 })

  // 4. encaixe na área, considerando a órbita inteira de cada cliente
  const xs: number[] = []
  const ys: number[] = []
  for (const p of bruto.values()) {
    xs.push(p.x)
    ys.push(p.y)
  }
  for (const h of hubs) {
    const p = bruto.get(h.id)!
    xs.push(p.x - ORBITA, p.x + ORBITA)
    ys.push(p.y - ORBITA, p.y + ORBITA)
  }
  const minX = Math.min(...xs)
  const minY = Math.min(...ys)
  const bw = Math.max(Math.max(...xs) - minX, 0.01)
  const bh = Math.max(Math.max(...ys) - minY, 0.01)
  const s = Math.min((largura - margem * 2) / bw, (altura - margem * 2) / bh)
  const offX = (largura - bw * s) / 2 - minX * s
  const offY = (altura - bh * s) / 2 - minY * s

  const pos = new Map<string, Posicao>()
  for (const [id, p] of bruto) pos.set(id, { x: offX + p.x * s, y: offY + p.y * s, ang: p.ang })
  const orbitas = hubs.map((h) => ({ id: h.id, ...pos.get(h.id)!, r: ORBITA * s }))
  return { pos, orbitas }
}
