import { useState } from 'react'
import { Power, RotateCcw } from 'lucide-react'
import { desativarCliente, reativarCliente } from '../../api/tribia'
import { AcessosEmpresa } from '../../components/empresas/AcessosEmpresa'
import { CabecalhoEmpresa, EstadoEmpresa } from '../../components/empresas/CabecalhoEmpresa'
import { EmpresaForm } from '../../components/empresas/EmpresaForm'
import { CardConta, CardPreferencias } from '../../components/Preferencias'
import { Badge, Card, Confirmacao } from '../../components/ui'
import { useEmpresa } from '../../hooks/useEmpresa'
import { fmtCnpj, REGIME_LABEL } from '../../lib/format'
import { useAuth, useDados, useToast } from '../../state/contexts'

/**
 * Configurações da empresa. Administrador: edita os dados, gerencia acessos e desativa/reativa.
 * Usuário da empresa: consulta os dados cadastrais e ajusta as próprias preferências.
 */
export function ConfiguracoesEmpresa() {
  const { empresa, notas, carregando } = useEmpresa()
  const { admin } = useAuth()
  const { recarregar } = useDados()
  const { mostrar } = useToast()
  const [desativando, setDesativando] = useState(false)

  if (!empresa) return <EstadoEmpresa carregando={carregando} />

  return (
    <>
      <CabecalhoEmpresa empresa={empresa} secao="Configurações" />

      {admin ? (
        <>
          <Card titulo="Dados cadastrais" sub="Campos com * são obrigatórios." className="mb-gap">
            <EmpresaForm
              key={empresa.id}
              idForm="form-config-empresa"
              empresa={empresa}
              cnpjBloqueado={notas.length > 0}
              onSalvo={async () => {
                await recarregar()
                mostrar({ tipo: 'sucesso', titulo: 'Alterações salvas' })
              }}
            />
          </Card>

          <div className="mb-gap">
            <AcessosEmpresa empresa={empresa} />
          </div>

          <Card titulo={empresa.ativo ? 'Desativar empresa' : 'Empresa desativada'}>
            <div className="zona-acao">
              <p>
                {empresa.ativo
                  ? 'A empresa deixa de receber notas e os usuários dela perdem o acesso. Documentos, relatórios e acessos são mantidos e você pode reativá-la depois.'
                  : 'A empresa não recebe notas e os usuários dela estão sem acesso. Os dados continuam disponíveis.'}
              </p>
              {empresa.ativo ? (
                <button className="btn btn--secondary" onClick={() => setDesativando(true)}>
                  <Power size={16} /> Desativar
                </button>
              ) : (
                <button
                  className="btn btn--primary"
                  onClick={async () => {
                    try {
                      await reativarCliente(empresa.id)
                      await recarregar()
                      mostrar({ tipo: 'sucesso', titulo: 'Empresa reativada' })
                    } catch (e) {
                      mostrar({ tipo: 'erro', titulo: 'Não foi possível reativar', texto: e instanceof Error ? e.message : undefined })
                    }
                  }}
                >
                  <RotateCcw size={16} /> Reativar
                </button>
              )}
            </div>
          </Card>

          {desativando && (
            <Confirmacao
              titulo="Desativar esta empresa?"
              rotulo="Desativar empresa"
              onFechar={() => setDesativando(false)}
              onConfirmar={async () => {
                await desativarCliente(empresa.id)
                await recarregar()
                mostrar({ tipo: 'sucesso', titulo: 'Empresa desativada' })
                setDesativando(false)
              }}
            >
              <p>A empresa deixa de receber notas e os usuários dela perdem o acesso enquanto estiver desativada.</p>
              <p style={{ marginTop: 10 }}>
                <strong>Nada é apagado:</strong> os {notas.length} documento(s), os relatórios e os acessos são mantidos.
              </p>
            </Confirmacao>
          )}
        </>
      ) : (
        <>
          <Card titulo="Dados cadastrais" sub="Para alterar, fale com o escritório." className="mb-gap">
            <dl className="dl">
              <div><dt>Razão social</dt><dd>{empresa.razaoSocial}</dd></div>
              <div><dt>Nome fantasia</dt><dd>{empresa.nomeFantasia ?? '—'}</dd></div>
              <div><dt>CNPJ</dt><dd className="num">{fmtCnpj(empresa.cnpj)}</dd></div>
              <div><dt>Regime</dt><dd>{REGIME_LABEL[empresa.regime]}</dd></div>
              <div><dt>Setor</dt><dd>{empresa.setor ?? '—'}</dd></div>
              <div><dt>Município</dt><dd>{empresa.municipio ? `${empresa.municipio}/${empresa.uf ?? ''}` : '—'}</dd></div>
              <div><dt>Responsável</dt><dd>{empresa.responsavel ?? '—'}</dd></div>
              <div><dt>Contato</dt><dd>{[empresa.email, empresa.telefone].filter(Boolean).join(' · ') || '—'}</dd></div>
              <div><dt>Situação</dt><dd>{empresa.ativo ? <Badge cor="green" sm>Ativa</Badge> : <Badge cor="gray" sm>Desativada</Badge>}</dd></div>
            </dl>
          </Card>
          <div className="grid-2">
            <CardConta />
            <CardPreferencias />
          </div>
        </>
      )}
    </>
  )
}
