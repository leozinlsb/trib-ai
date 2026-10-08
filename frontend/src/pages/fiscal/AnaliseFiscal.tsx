import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { ChevronRight, Download, FileText, Plus, RefreshCw } from 'lucide-react'
import { API_URL, ApiError } from '../../api/client'
import { detalharAnalise, emAndamento, servicoIndisponivel, type AnaliseDetalhe } from '../../api/inteligenciaFiscal'
import type { Cliente } from '../../api/types'
import { CabecalhoEmpresa, EstadoEmpresa } from '../../components/empresas/CabecalhoEmpresa'
import { BadgeStatus, ServicoIndisponivel } from '../../components/fiscal/Comum'
import { EtapasProcessamento } from '../../components/fiscal/Etapas'
import { RevisaoAnalise } from '../../components/fiscal/Revisao'
import { Alternativas, CardResultado, Fontes, Fundamentacao, Validacao } from '../../components/fiscal/Resultado'
import { Aviso, Card, Carregando, ErroEstado, Vazio } from '../../components/ui'
import { useEmpresa } from '../../hooks/useEmpresa'
import { fmtNcm, STATUS } from '../../lib/fiscal'
import { fmtBytes, fmtDataHora } from '../../lib/format'
import { rotaAnaliseFiscal } from '../../lib/rotas'

/** Intervalo de atualização enquanto a análise está em andamento (o servidor informa só o status). */
const INTERVALO_MS = 4000

type Estado =
  | { tipo: 'carregando' }
  | { tipo: 'ok'; analise: AnaliseDetalhe }
  | { tipo: 'indisponivel' }
  | { tipo: 'nao-encontrada'; msg: string }
  | { tipo: 'erro'; msg: string }

/**
 * Uma análise: acompanhamento enquanto processa e resultado quando conclui. As atualizações chegam por
 * consulta periódica (polling). Se o backend passar a oferecer eventos (SSE), basta trocar o efeito abaixo.
 */
export function AnaliseFiscal() {
  const { id: empresaId, empresa, carregando } = useEmpresa()
  const { analiseId } = useParams()
  const [estado, setEstado] = useState<Estado>({ tipo: 'carregando' })
  const [tentativa, setTentativa] = useState(0)

  useEffect(() => {
    const ctrl = new AbortController()
    let timer: number | undefined
    const carregar = () => {
      detalharAnalise(Number(analiseId), ctrl.signal)
        .then((analise) => {
          setEstado({ tipo: 'ok', analise })
          if (emAndamento(analise.status)) timer = window.setTimeout(carregar, INTERVALO_MS)
        })
        .catch((e) => {
          if (e instanceof DOMException && e.name === 'AbortError') return
          if (servicoIndisponivel(e)) setEstado({ tipo: 'indisponivel' })
          else if (e instanceof ApiError && e.status === 404) setEstado({ tipo: 'nao-encontrada', msg: e.message })
          else setEstado({ tipo: 'erro', msg: e instanceof Error ? e.message : 'Falha ao carregar a análise.' })
        })
    }
    carregar()
    return () => {
      ctrl.abort()
      window.clearTimeout(timer)
    }
  }, [analiseId, tentativa])

  if (!empresa) return <EstadoEmpresa carregando={carregando} />

  const trilha = (nome?: string) => (
    <nav className="breadcrumb" aria-label="Trilha">
      <Link to={rotaAnaliseFiscal(empresaId)}>Inteligência Fiscal</Link>
      <ChevronRight size={14} />
      <span>{nome ?? `Análise ${analiseId}`}</span>
    </nav>
  )

  if (estado.tipo !== 'ok') {
    return (
      <>
        {trilha()}
        <CabecalhoEmpresa empresa={empresa} secao="Análise fiscal" />
        {estado.tipo === 'carregando' ? (
          <div className="card"><Carregando linhas={6} /></div>
        ) : estado.tipo === 'indisponivel' ? (
          <ServicoIndisponivel />
        ) : estado.tipo === 'nao-encontrada' ? (
          <div className="card">
            <Vazio titulo="Análise não encontrada">
              <Link to={rotaAnaliseFiscal(empresaId)}>Voltar às análises</Link>
            </Vazio>
          </div>
        ) : (
          <div className="card"><ErroEstado mensagem={estado.msg} onTentar={() => setTentativa((t) => t + 1)} /></div>
        )}
      </>
    )
  }

  return (
    <>
      {trilha(estado.analise.mercadoria)}
      <VisaoAnalise analise={estado.analise} empresa={empresa} onRevisada={(a) => setEstado({ tipo: 'ok', analise: a })} />
    </>
  )
}

/** Apresentação de uma análise (usada também pela tela de exemplo em desenvolvimento). */
export function VisaoAnalise({ analise, empresa, exemplo, onRevisada }: {
  analise: AnaliseDetalhe
  empresa: Cliente
  exemplo?: boolean
  /** revisão registrada: a página troca a análise pela versão devolvida pelo servidor */
  onRevisada?: (a: AnaliseDetalhe) => void
}) {
  const andamento = emAndamento(analise.status)
  const temResultado = !!analise.resultado
  const empresaId = empresa.id

  return (
    <>
      <CabecalhoEmpresa
        empresa={empresa}
        secao={analise.mercadoria}
        sub={<>Análise fiscal · solicitada em {fmtDataHora(analise.criadaEm)} · <BadgeStatus status={analise.status} sm /></>}
        acoes={
          <>
            {analise.relatorio?.downloadUrl && !exemplo && (
              <a href={`${API_URL}${analise.relatorio.downloadUrl}`} className="btn btn--secondary" download>
                <Download size={16} /> Baixar relatório
              </a>
            )}
            <Link to={rotaAnaliseFiscal(empresaId, 'nova')} className="btn btn--verde">
              <Plus size={18} /> Nova análise
            </Link>
          </>
        }
      />

      {/* acompanhamento: em andamento ou sem resultado (falha / informações insuficientes) */}
      {(andamento || !temResultado) && (
        <div className="grid-side">
          <Card
            titulo={andamento ? 'Acompanhamento da análise' : STATUS[analise.status].rotulo}
            sub={andamento ? 'Esta tela se atualiza sozinha. Você pode sair e voltar depois.' : undefined}
            acoes={andamento ? <span className="atualizando"><RefreshCw size={13} className="spin" /> atualizando</span> : undefined}
          >
            <EtapasProcessamento analise={analise} />
            {analise.status === 'INFORMACOES_INSUFICIENTES' && (
              <div className="form-acoes" style={{ marginTop: 16 }}>
                <Link to={rotaAnaliseFiscal(empresaId, 'nova')} state={{ entrada: analise.entrada }} className="btn btn--primary">
                  Complementar e analisar de novo
                </Link>
              </div>
            )}
            {analise.status === 'FALHA' && (
              <div className="form-acoes" style={{ marginTop: 16 }}>
                <Link to={rotaAnaliseFiscal(empresaId, 'nova')} state={{ entrada: analise.entrada }} className="btn btn--primary">
                  Tentar novamente
                </Link>
              </div>
            )}
          </Card>
          <DadosInformados analise={analise} />
        </div>
      )}

      {temResultado && (
        <div className="stack" id="relatorio">
          {analise.status === 'AGUARDANDO_REVISAO' && (
            <Aviso tipo="warn">
              <strong>Aguardando revisão especializada.</strong> {analise.mensagem ?? 'O resultado abaixo ainda precisa ser conferido por um especialista.'}
            </Aviso>
          )}
          <CardResultado analise={analise} />
          {!exemplo && onRevisada && <RevisaoAnalise analise={analise} onRevisada={onRevisada} />}
          <div className="grid-2">
            <Fundamentacao analise={analise} />
            <Validacao analise={analise} />
          </div>
          <Alternativas analise={analise} />
          <div className="grid-side">
            <Fontes analise={analise} />
            <div className="stack">
              <DadosInformados analise={analise} />
              <Card titulo="Etapas da análise">
                <EtapasProcessamento analise={analise} />
              </Card>
            </div>
          </div>
        </div>
      )}
    </>
  )
}

function DadosInformados({ analise }: { analise: AnaliseDetalhe }) {
  const e = analise.entrada
  return (
    <Card titulo="Dados informados">
      <dl className="dl dl--coluna">
        <div><dt>Descrição</dt><dd>{e.descricao}</dd></div>
        {e.composicao && <div><dt>Composição</dt><dd>{e.composicao}</dd></div>}
        {e.finalidade && <div><dt>Finalidade</dt><dd>{e.finalidade}</dd></div>}
        {e.caracteristicas && <div><dt>Características técnicas</dt><dd>{e.caracteristicas}</dd></div>}
        <div><dt>NCM atual</dt><dd className="num">{e.ncmAtual ? fmtNcm(e.ncmAtual) : 'Não informada'}</dd></div>
        <div>
          <dt>Documentos</dt>
          <dd>
            {analise.anexos.length === 0 ? 'Nenhum' : (
              <ul className="lista-icone">
                {analise.anexos.map((a, i) => <li key={i}><FileText size={14} color="var(--muted)" /> <span>{a.nome} · {fmtBytes(a.tamanho)}</span></li>)}
              </ul>
            )}
          </dd>
        </div>
      </dl>
    </Card>
  )
}
