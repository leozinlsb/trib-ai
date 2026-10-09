import { useState } from 'react'
import { UserCheck } from 'lucide-react'
import { revisarAnalise, type AnaliseDetalhe } from '../../api/inteligenciaFiscal'
import { fmtNcm } from '../../lib/fiscal'
import { fmtDataHora } from '../../lib/format'
import { useToast } from '../../state/contexts'
import { Aviso, Card, Confirmacao } from '../ui'

const OUTRA = 'outra'

/**
 * Revisão humana da análise: a pessoa aceita a NCM sugerida, escolhe uma alternativa ou informa outra (que precisa
 * constar da NCM vigente; o servidor confere). Trocar a sugestão exige justificativa. O resultado automático e as
 * revisões anteriores continuam visíveis como evidência.
 */
export function RevisaoAnalise({ analise, onRevisada }: { analise: AnaliseDetalhe; onRevisada: (a: AnaliseDetalhe) => void }) {
  const { mostrar } = useToast()
  const sugerida = analise.resultado?.ncm ?? ''
  const opcoes = analise.alternativas?.map((a) => a.ncm) ?? (sugerida ? [sugerida] : [])
  const [escolha, setEscolha] = useState(sugerida)
  const [outra, setOutra] = useState('')
  const [observacao, setObservacao] = useState('')
  const [confirmando, setConfirmando] = useState(false)
  const revisoes = analise.revisoes ?? []
  const ultima = revisoes[revisoes.length - 1]

  const ncm = (escolha === OUTRA ? outra : escolha).replace(/\D/g, '')
  const alterou = ncm !== sugerida
  const valido = ncm.length === 8 && (!alterou || observacao.trim().length > 0)

  return (
    <Card
      titulo="Revisão humana"
      sub="Registre a NCM que a empresa vai adotar. É um registro interno, não uma decisão da Receita Federal."
    >
      {ultima && (
        <Aviso tipo="info" style={{ marginBottom: 14 }}>
          <strong>{ultima.decisao === 'ACEITA' ? 'Sugestão aceita' : `NCM alterada para ${fmtNcm(ultima.ncm)}`}</strong>
          {' '}por {ultima.revisadaPor} em {fmtDataHora(ultima.revisadaEm)}.
          {ultima.observacao && <> Justificativa: {ultima.observacao}</>}
        </Aviso>
      )}
      {revisoes.length > 1 && (
        <ul className="lista-icone" style={{ marginBottom: 14, fontSize: 13 }}>
          {revisoes.slice(0, -1).reverse().map((r, i) => (
            <li key={i}>
              <span>{fmtDataHora(r.revisadaEm)} · {r.decisao === 'ACEITA' ? 'aceitou' : 'alterou para'} {fmtNcm(r.ncm)} · {r.revisadaPor}</span>
            </li>
          ))}
        </ul>
      )}

      <fieldset className="field" style={{ border: 0, padding: 0, margin: 0 }}>
        <legend className="field__label">{ultima ? 'Registrar nova revisão' : 'NCM decidida'}</legend>
        {opcoes.map((n) => (
          <label key={n} style={{ display: 'flex', gap: 8, alignItems: 'center', margin: '6px 0' }}>
            <input type="radio" name="ncm-revisao" value={n} checked={escolha === n} onChange={() => setEscolha(n)} />
            <span className="num">{fmtNcm(n)}</span>
            {n === sugerida && <span className="cell-sub">(sugestão da análise)</span>}
          </label>
        ))}
        <label style={{ display: 'flex', gap: 8, alignItems: 'center', margin: '6px 0' }}>
          <input type="radio" name="ncm-revisao" value={OUTRA} checked={escolha === OUTRA} onChange={() => setEscolha(OUTRA)} />
          <span>Outra NCM</span>
          {escolha === OUTRA && (
            <input
              className="input"
              style={{ maxWidth: 160 }}
              inputMode="numeric"
              placeholder="0000.00.00"
              aria-label="Outra NCM"
              value={outra}
              onChange={(e) => setOutra(e.target.value)}
            />
          )}
        </label>
      </fieldset>

      <div className="field" style={{ marginTop: 10 }}>
        <label className="field__label" htmlFor="obs-revisao">
          Justificativa {alterou ? '(obrigatória ao trocar a sugestão)' : '(opcional)'}
        </label>
        <textarea
          id="obs-revisao"
          className="input"
          rows={3}
          maxLength={1000}
          value={observacao}
          onChange={(e) => setObservacao(e.target.value)}
          placeholder="Ex.: conferido com a ficha técnica e a Nota 1 do Capítulo 34."
        />
      </div>

      <div className="form-acoes" style={{ marginTop: 12 }}>
        <button className="btn btn--primary" disabled={!valido} onClick={() => setConfirmando(true)}>
          <UserCheck size={16} /> {alterou ? 'Registrar alteração' : 'Aceitar sugestão'}
        </button>
      </div>

      {confirmando && (
        <Confirmacao
          titulo={alterou ? `Registrar a NCM ${fmtNcm(ncm)}?` : `Aceitar a sugestão ${fmtNcm(ncm)}?`}
          rotulo="Registrar revisão"
          perigo={false}
          onFechar={() => setConfirmando(false)}
          onConfirmar={async () => {
            const atualizada = await revisarAnalise(analise.id, { ncm, observacao: observacao.trim() || undefined })
            onRevisada(atualizada)
            setConfirmando(false)
            setObservacao('')
            mostrar({ tipo: 'sucesso', titulo: 'Revisão registrada' })
          }}
        >
          <p>
            A revisão fica registrada com seu nome e a data, junto com a sugestão automática original. A análise passa a
            constar como concluída. Confira a NCM no cadastro da empresa antes de emitir notas.
          </p>
        </Confirmacao>
      )}
    </Card>
  )
}
