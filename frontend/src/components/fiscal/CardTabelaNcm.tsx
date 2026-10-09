import { useEffect, useState } from 'react'
import { request } from '../../api/client'
import { fmtData } from '../../lib/format'
import { Aviso, Badge, Card, Carregando, ErroEstado } from '../ui'

interface SituacaoNcm {
  fonte: string
  situacao: string
  ato: string
  extraidoEm: string
  idadeDias: number | null
  limiteDias: number
  desatualizada: boolean
  comoAtualizar: string
}

/** Versão e idade da tabela NCM vigente usada pela Inteligência Fiscal (somente administrador). */
export function CardTabelaNcm() {
  const [s, setS] = useState<SituacaoNcm | null>(null)
  const [erro, setErro] = useState<string | null>(null)

  useEffect(() => {
    const ctrl = new AbortController()
    request<SituacaoNcm>('/api/admin/tabelas/ncm', { signal: ctrl.signal })
      .then(setS)
      .catch((e) => {
        if (e instanceof DOMException && e.name === 'AbortError') return
        setErro(e instanceof Error ? e.message : 'Falha ao consultar a tabela NCM.')
      })
    return () => ctrl.abort()
  }, [])

  return (
    <Card titulo="Tabela NCM" sub="Base oficial usada para conferir existência e vigência das NCMs sugeridas.">
      {erro ? <ErroEstado mensagem={erro} /> : !s ? <Carregando linhas={3} /> : (
        <>
          <dl className="dl">
            <div><dt>Situação</dt><dd>{s.desatualizada ? <Badge cor="amber" sm>Atualizar</Badge> : <Badge cor="green" sm>Em dia</Badge>}</dd></div>
            <div><dt>Versão</dt><dd>{s.situacao} · {s.ato}</dd></div>
            <div><dt>Extraída em</dt><dd>{fmtData(s.extraidoEm)}{s.idadeDias != null && ` (${s.idadeDias} dias)`}</dd></div>
            <div><dt>Fonte</dt><dd>{s.fonte}</dd></div>
          </dl>
          {s.desatualizada && (
            <Aviso tipo="warn" style={{ marginTop: 10 }}>
              A tabela passou de {s.limiteDias} dias e pode não refletir alterações recentes da NCM. {s.comoAtualizar}
            </Aviso>
          )}
        </>
      )}
    </Card>
  )
}
