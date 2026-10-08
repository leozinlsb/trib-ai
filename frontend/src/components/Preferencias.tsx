import { Card } from './ui'
import { nomeCliente } from '../lib/format'
import { useAuth, useDados, usePreferencias } from '../state/contexts'

/** Preferências desta interface (salvas no navegador). */
export function CardPreferencias() {
  const { prefs, definir } = usePreferencias()
  const { clientes } = useDados()
  const { admin } = useAuth()
  return (
    <Card titulo="Preferências">
      <div className="stack" style={{ gap: 14 }}>
        {admin && (
          <div className="field">
            <label className="field__label" htmlFor="p-cli">Empresa sugerida ao enviar notas</label>
            <select
              id="p-cli"
              className="select"
              value={prefs.clientePadraoId ?? ''}
              onChange={(e) => definir({ clientePadraoId: e.target.value ? Number(e.target.value) : null })}
            >
              <option value="">Primeira da lista</option>
              {clientes.filter((c) => c.ativo).map((c) => (
                <option key={c.id} value={c.id}>{nomeCliente(c)}</option>
              ))}
            </select>
          </div>
        )}
        <div className="field">
          <label className="field__label" htmlFor="p-per">Período dos gráficos</label>
          <select id="p-per" className="select" value={prefs.periodoGrafico} onChange={(e) => definir({ periodoGrafico: e.target.value as 'semanal' | 'mensal' })}>
            <option value="semanal">Semanal</option>
            <option value="mensal">Mensal</option>
          </select>
        </div>
        <div className="field">
          <label className="field__label" htmlFor="p-pp">Itens por página nas tabelas</label>
          <select id="p-pp" className="select" value={prefs.itensPorPagina} onChange={(e) => definir({ itensPorPagina: Number(e.target.value) })}>
            {[10, 20, 50].map((n) => <option key={n} value={n}>{n}</option>)}
          </select>
        </div>
      </div>
    </Card>
  )
}

/** Dados do usuário da sessão. */
export function CardConta() {
  const { usuario, admin } = useAuth()
  if (!usuario) return null
  return (
    <Card titulo="Sua conta">
      <dl className="dl" style={{ gridTemplateColumns: '1fr 1fr' }}>
        <div><dt>Nome</dt><dd>{usuario.nome}</dd></div>
        <div><dt>E-mail</dt><dd>{usuario.email}</dd></div>
        <div><dt>Perfil</dt><dd>{admin ? 'Administrador do escritório' : 'Usuário da empresa'}</dd></div>
        {!admin && <div><dt>Empresa</dt><dd>{usuario.clienteNome}</dd></div>}
      </dl>
      <p className="cell-sub" style={{ marginTop: 14 }}>
        Para alterar nome, e-mail ou senha, fale com o administrador do escritório.
      </p>
    </Card>
  )
}
