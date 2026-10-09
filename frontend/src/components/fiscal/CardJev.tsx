import { useEffect, useState } from 'react'
import { PlugZap } from 'lucide-react'
import { request } from '../../api/client'
import type { Pontuacao } from '../../api/inteligenciaFiscal'
import { fmtNcm } from '../../lib/fiscal'
import { Aviso, Badge, Card, Carregando, Confirmacao, ErroEstado } from '../ui'

interface StatusJev {
  modo: 'DESLIGADO' | 'HTTP' | 'SIMULADO'
  url: string
  modelo: string
  chaveConfigurada: boolean
  ativaNasAnalises: boolean
  observacao: string
}

interface ResultadoTeste {
  conectou: boolean
  modelosDisponiveis: string[]
  modeloConfiguradoDisponivel: boolean
  modeloQueRespondeu: string | null
  pontuacoes: Record<string, Pontuacao>
  separouCandidatas: boolean | null
  tokensEntrada: number | null
  tokensSaida: number | null
  milissegundos: number
  avisos: string[]
}

/**
 * Estado da JEV AI no servidor e teste de conexão (somente administrador). A chave fica no servidor e nunca chega
 * ao navegador; o teste usa uma mercadoria sintética e é cobrado pela TypeSafe, por isso pede confirmação.
 */
export function CardJev() {
  const [status, setStatus] = useState<StatusJev | null>(null)
  const [erro, setErro] = useState<string | null>(null)
  const [confirmando, setConfirmando] = useState(false)
  const [resultado, setResultado] = useState<ResultadoTeste | null>(null)

  useEffect(() => {
    const ctrl = new AbortController()
    request<StatusJev>('/api/admin/jev/status', { signal: ctrl.signal })
      .then(setStatus)
      .catch((e) => {
        if (e instanceof DOMException && e.name === 'AbortError') return
        setErro(e instanceof Error ? e.message : 'Falha ao consultar a JEV AI.')
      })
    return () => ctrl.abort()
  }, [])

  return (
    <Card titulo="JEV AI" sub="Pontuação de compatibilidade das NCMs sugeridas na Inteligência Fiscal.">
      {erro ? <ErroEstado mensagem={erro} /> : !status ? <Carregando linhas={3} /> : (
        <>
          <dl className="dl">
            <div>
              <dt>Situação nas análises</dt>
              <dd>{status.ativaNasAnalises ? <Badge cor="green" sm>Ativa</Badge> : <Badge cor="gray" sm>{status.modo === 'SIMULADO' ? 'Simulada' : 'Desligada'}</Badge>}</dd>
            </div>
            <div><dt>Chave no servidor</dt><dd>{status.chaveConfigurada ? 'Configurada' : 'Não configurada'}</dd></div>
            <div><dt>Modelo</dt><dd className="num">{status.modelo}</dd></div>
            <div><dt>Endereço</dt><dd className="num">{status.url}</dd></div>
          </dl>
          <p className="cell-sub" style={{ margin: '10px 0' }}>{status.observacao}</p>
          <button className="btn btn--secondary btn--sm" disabled={!status.chaveConfigurada} onClick={() => setConfirmando(true)}>
            <PlugZap size={15} /> Testar conexão
          </button>
        </>
      )}

      {resultado && (
        <div style={{ marginTop: 14 }}>
          <Aviso tipo={resultado.avisos.length ? 'warn' : 'info'}>
            Conectou: modelo {resultado.modeloQueRespondeu ?? '—'} em {resultado.milissegundos} ms
            {resultado.tokensEntrada != null && <> · {resultado.tokensEntrada} tokens de entrada</>}.
            {' '}{resultado.separouCandidatas ? 'A JEV distinguiu a candidata compatível da incompatível.' : ''}
            {resultado.avisos.map((a, i) => <div key={i}>{a}</div>)}
          </Aviso>
          <ul className="lista-icone" style={{ fontSize: 13, marginTop: 8 }}>
            {Object.entries(resultado.pontuacoes).map(([ncm, p]) => (
              <li key={ncm}><span className="num">{fmtNcm(ncm)}</span>: {p.valor.toLocaleString('pt-BR')} (escala {p.escala})</li>
            ))}
          </ul>
        </div>
      )}

      {confirmando && (
        <Confirmacao
          titulo="Testar a conexão com a JEV AI?"
          rotulo="Testar (usa créditos)"
          perigo={false}
          onFechar={() => setConfirmando(false)}
          onConfirmar={async () => {
            const r = await request<ResultadoTeste>('/api/admin/jev/teste?confirmarCusto=true', { method: 'POST' })
            setResultado(r)
            setConfirmando(false)
          }}
        >
          <p>
            O servidor lista os modelos da conta e envia <strong>uma</strong> avaliação com uma mercadoria fictícia
            (sabonete) e duas NCMs: uma compatível e uma incompatível. A TypeSafe cobra por token (poucas centenas
            de tokens neste teste). Nenhum dado de empresa é enviado.
          </p>
        </Confirmacao>
      )}
    </Card>
  )
}
