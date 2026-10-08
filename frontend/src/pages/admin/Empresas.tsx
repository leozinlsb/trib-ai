import { useMemo, useState } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import { ArrowRight, Pencil, Plus, Power, RotateCcw, Search } from 'lucide-react'
import { desativarCliente, reativarCliente } from '../../api/tribia'
import type { Cliente } from '../../api/types'
import { EmpresaForm } from '../../components/empresas/EmpresaForm'
import { Badge, Card, Carregando, Confirmacao, ErroEstado, Modal, Segmented, Vazio } from '../../components/ui'
import { fmtCnpj, fmtNumero, nomeCliente, normalizar, REGIME_LABEL } from '../../lib/format'
import { rotaEmpresa } from '../../lib/rotas'
import { useDados, useToast } from '../../state/contexts'

type Filtro = 'ativas' | 'desativadas' | 'todas'

/** Cadastro, edição, desativação e acesso às empresas (somente administrador). */
export function Empresas() {
  const { clientes, notas, status, erro, recarregar } = useDados()
  const { mostrar } = useToast()
  const navigate = useNavigate()
  const [params, setParams] = useSearchParams()
  const [busca, setBusca] = useState('')
  const [filtro, setFiltro] = useState<Filtro>('ativas')
  const [editando, setEditando] = useState<Cliente | null>(null)
  const [desativando, setDesativando] = useState<Cliente | null>(null)
  const nova = params.get('nova') === '1'

  const qtdNotas = useMemo(() => {
    const m = new Map<number, number>()
    notas.forEach((n) => m.set(n.clienteId, (m.get(n.clienteId) ?? 0) + 1))
    return m
  }, [notas])

  const lista = useMemo(() => {
    const q = normalizar(busca.trim())
    const qDig = q.replace(/\D/g, '')
    return clientes
      .filter((c) => (filtro === 'todas' ? true : filtro === 'ativas' ? c.ativo : !c.ativo))
      .filter((c) => {
        if (!q) return true
        const texto = normalizar(`${c.razaoSocial} ${c.nomeFantasia ?? ''} ${c.responsavel ?? ''}`)
        return texto.includes(q) || (qDig.length >= 3 && c.cnpj.includes(qDig))
      })
      .sort((a, b) => nomeCliente(a).localeCompare(nomeCliente(b)))
  }, [clientes, busca, filtro])

  const fecharNova = () => {
    const p = new URLSearchParams(params)
    p.delete('nova')
    setParams(p, { replace: true })
  }

  const reativar = async (c: Cliente) => {
    try {
      await reativarCliente(c.id)
      await recarregar()
      mostrar({ tipo: 'sucesso', titulo: 'Empresa reativada', texto: nomeCliente(c) })
    } catch (e) {
      mostrar({ tipo: 'erro', titulo: 'Não foi possível reativar', texto: e instanceof Error ? e.message : undefined })
    }
  }

  const desativadas = clientes.filter((c) => !c.ativo).length

  return (
    <>
      <div className="page-head">
        <div>
          <h1>Empresas</h1>
          <p>Cadastre e gerencie as empresas atendidas pelo escritório</p>
        </div>
        <div className="page-head__actions">
          <button className="btn btn--primary" onClick={() => setParams({ nova: '1' })}>
            <Plus size={19} strokeWidth={2.2} /> Nova empresa
          </button>
        </div>
      </div>

      <Card corpo="nenhum">
        <div className="filters">
          <div className="field field--grow">
            <label className="field__label" htmlFor="emp-busca">Buscar</label>
            <div className="input-icone">
              <Search size={16} aria-hidden="true" />
              <input
                id="emp-busca"
                className="input"
                type="search"
                placeholder="Nome, CNPJ ou responsável"
                value={busca}
                onChange={(e) => setBusca(e.target.value)}
              />
            </div>
          </div>
          <div className="field" style={{ width: 'auto' }}>
            <span className="field__label">Situação</span>
            <Segmented<Filtro>
              rotulo="Situação"
              valor={filtro}
              onChange={setFiltro}
              opcoes={[
                { valor: 'ativas', rotulo: 'Ativas' },
                { valor: 'desativadas', rotulo: `Desativadas${desativadas ? ` (${desativadas})` : ''}` },
                { valor: 'todas', rotulo: 'Todas' },
              ]}
            />
          </div>
        </div>

        {status === 'carregando' ? (
          <Carregando linhas={4} />
        ) : status === 'erro' ? (
          <ErroEstado mensagem={erro ?? ''} onTentar={recarregar} />
        ) : lista.length === 0 ? (
          <Vazio titulo={clientes.length ? 'Nenhuma empresa encontrada' : 'Nenhuma empresa cadastrada'}>
            {clientes.length ? 'Ajuste a busca ou a situação.' : 'Use “Nova empresa” para cadastrar a primeira.'}
          </Vazio>
        ) : (
          <div className="table-wrap">
            <table className="table table--compact">
              <thead>
                <tr>
                  <th>Empresa</th>
                  <th>CNPJ</th>
                  <th>Regime</th>
                  <th>Responsável</th>
                  <th className="right">Notas</th>
                  <th>Situação</th>
                  <th className="right">Ações</th>
                </tr>
              </thead>
              <tbody>
                {lista.map((c) => (
                  <tr key={c.id}>
                    <td>
                      <Link to={rotaEmpresa(c.id)} className="cell-main">{nomeCliente(c)}</Link>
                      {c.nomeFantasia && <div className="cell-sub">{c.razaoSocial}</div>}
                    </td>
                    <td className="num">{fmtCnpj(c.cnpj)}</td>
                    <td>{REGIME_LABEL[c.regime]}</td>
                    <td>
                      {c.responsavel ?? <span className="muted">—</span>}
                      {c.email && <div className="cell-sub">{c.email}</div>}
                    </td>
                    <td className="right num">{fmtNumero(qtdNotas.get(c.id) ?? 0)}</td>
                    <td>{c.ativo ? <Badge cor="green" sm>Ativa</Badge> : <Badge cor="gray" sm>Desativada</Badge>}</td>
                    <td className="right">
                      <div className="acoes-linha">
                        <button className="icon-btn" onClick={() => setEditando(c)} aria-label={`Editar ${nomeCliente(c)}`} title="Editar">
                          <Pencil size={16} />
                        </button>
                        {c.ativo ? (
                          <button className="icon-btn" onClick={() => setDesativando(c)} aria-label={`Desativar ${nomeCliente(c)}`} title="Desativar">
                            <Power size={16} />
                          </button>
                        ) : (
                          <button className="icon-btn" onClick={() => reativar(c)} aria-label={`Reativar ${nomeCliente(c)}`} title="Reativar">
                            <RotateCcw size={16} />
                          </button>
                        )}
                        <Link to={rotaEmpresa(c.id)} className="btn btn--secondary btn--sm">
                          Abrir <ArrowRight size={14} />
                        </Link>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </Card>

      {(nova || editando) && (
        <Modal
          titulo={editando ? `Editar ${nomeCliente(editando)}` : 'Nova empresa'}
          sub={editando ? undefined : 'Campos com * são obrigatórios. Depois você poderá criar os acessos dos usuários.'}
          onFechar={() => (editando ? setEditando(null) : fecharNova())}
          largura={720}
        >
          <EmpresaForm
            empresa={editando ?? undefined}
            cnpjBloqueado={!!editando && (qtdNotas.get(editando.id) ?? 0) > 0}
            onCancelar={() => (editando ? setEditando(null) : fecharNova())}
            onSalvo={async (c) => {
              await recarregar()
              if (editando) {
                setEditando(null)
                mostrar({ tipo: 'sucesso', titulo: 'Alterações salvas', texto: nomeCliente(c) })
              } else {
                mostrar({ tipo: 'sucesso', titulo: 'Empresa cadastrada', texto: 'Agora crie o acesso dos usuários da empresa.' })
                navigate(rotaEmpresa(c.id, 'configuracoes'))
              }
            }}
          />
        </Modal>
      )}

      {desativando && (
        <Confirmacao
          titulo={`Desativar ${nomeCliente(desativando)}?`}
          rotulo="Desativar empresa"
          onFechar={() => setDesativando(null)}
          onConfirmar={async () => {
            await desativarCliente(desativando.id)
            await recarregar()
            mostrar({ tipo: 'sucesso', titulo: 'Empresa desativada', texto: 'Você pode reativá-la quando quiser.' })
            setDesativando(null)
          }}
        >
          <p>A empresa deixa de receber notas e os usuários dela perdem o acesso enquanto estiver desativada.</p>
          <p style={{ marginTop: 10 }}>
            <strong>Nada é apagado:</strong> os {fmtNumero(qtdNotas.get(desativando.id) ?? 0)} documento(s), os relatórios
            e os acessos são mantidos, e a empresa pode ser reativada a qualquer momento.
          </p>
        </Confirmacao>
      )}
    </>
  )
}
