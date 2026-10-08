import { useState } from 'react'
import { RefreshCw } from 'lucide-react'
import { recalcularEmpresa } from '../../api/tribia'
import type { Cliente } from '../../api/types'
import { fmtNumero } from '../../lib/format'
import { useDados, useToast } from '../../state/contexts'
import { Card, Confirmacao } from '../ui'

/**
 * Recalcula 2027 em todas as notas da empresa (POST /api/clientes/{id}/calcular). Serve para refazer os números
 * depois de a calculadora oficial voltar ou de uma mudança de configuração; não reclassifica nada.
 */
export function RecalcularEmpresa({ empresa, totalNotas }: { empresa: Cliente; totalNotas: number }) {
  const { recarregar, marcarAlteracaoFiscal, atualizarDetalhes, notas } = useDados()
  const { mostrar } = useToast()
  const [confirmando, setConfirmando] = useState(false)

  return (
    <>
      <Card titulo="Recalcular 2027" className="mb-gap">
        <div className="zona-acao">
          <p>
            Refaz o cálculo de CBS/IBS/IS de 2027 em todas as notas da empresa com a classificação atual. Use depois de
            a calculadora oficial voltar ao ar ou de mudar a configuração de cálculo. Itens sem classificação
            continuam fora do cálculo.
          </p>
          <button className="btn btn--secondary" disabled={totalNotas === 0} onClick={() => setConfirmando(true)}>
            <RefreshCw size={16} /> Recalcular todas as notas
          </button>
        </div>
      </Card>

      {confirmando && (
        <Confirmacao
          titulo="Recalcular todas as notas?"
          rotulo="Recalcular"
          perigo={false}
          onFechar={() => setConfirmando(false)}
          onConfirmar={async () => {
            const r = await recalcularEmpresa(empresa.id)
            await atualizarDetalhes(notas.filter((n) => n.clienteId === empresa.id).map((n) => n.id))
            marcarAlteracaoFiscal()
            await recarregar()
            mostrar({
              tipo: 'sucesso',
              titulo: `${fmtNumero(r.notas)} nota(s) recalculada(s)`,
              texto: r.itensPendentes > 0
                ? `${fmtNumero(r.itensPendentes)} item(ns) sem classificação ficaram fora do cálculo.`
                : r.avisos[0],
            })
            setConfirmando(false)
          }}
        >
          <p>
            Os valores de 2027 das {fmtNumero(totalNotas)} nota(s) serão substituídos pelo novo cálculo. Classificações
            e revisões não mudam. Pode levar alguns segundos.
          </p>
        </Confirmacao>
      )}
    </>
  )
}
