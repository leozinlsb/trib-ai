/**
 * SOMENTE DESENVOLVIMENTO. Tela para revisar o layout da análise sem o serviço real.
 * Carregada por import dinâmico atrás de `import.meta.env.DEV` (App.tsx): não entra no build de produção.
 * Os dados são genéricos e marcados como fictícios — não usam códigos NCM nem normas reais.
 */
import { useState } from 'react'
import type { AnaliseDetalhe, StatusAnalise } from '../../api/inteligenciaFiscal'
import { EstadoEmpresa } from '../../components/empresas/CabecalhoEmpresa'
import { Segmented } from '../../components/ui'
import { useEmpresa } from '../../hooks/useEmpresa'
import { VisaoAnalise } from './AnaliseFiscal'

const agora = new Date()
const ha = (min: number) => new Date(agora.getTime() - min * 60_000).toISOString()

function exemplo(clienteId: number, status: StatusAnalise): AnaliseDetalhe {
  const comResultado = status === 'CONCLUIDA' || status === 'AGUARDANDO_REVISAO'
  const historico = (
    ['AGUARDANDO', 'INTERPRETANDO', 'PESQUISANDO_NCM', 'AVALIANDO', 'VALIDANDO', 'GERANDO_RELATORIO', 'CONCLUIDA'] as StatusAnalise[]
  ).map((s, i) => ({ status: s, em: ha(30 - i * 4) }))
  const ate = status === 'AVALIANDO' ? 4 : status === 'INFORMACOES_INSUFICIENTES' ? 2 : 7
  return {
    id: 0,
    clienteId,
    mercadoria: 'Mercadoria de exemplo',
    ncmSugerida: comResultado ? '00000000' : null,
    status,
    criadaEm: ha(32),
    atualizadaEm: ha(2),
    relatorioDisponivel: comResultado,
    entrada: {
      nome: 'Mercadoria de exemplo',
      descricao: 'Texto de exemplo para a descrição detalhada informada pelo usuário.',
      composicao: 'Material de exemplo',
      finalidade: 'Finalidade de exemplo',
      ncmAtual: '00000000',
    },
    anexos: [{ nome: 'ficha-tecnica-exemplo.pdf', tamanho: 245_000, tipo: 'application/pdf' }],
    historico: [
      ...historico.slice(0, ate),
      ...(status === 'INFORMACOES_INSUFICIENTES' ? [{ status, em: ha(20) }] : []),
      ...(status === 'AGUARDANDO_REVISAO' ? [{ status, em: ha(1) }] : []),
    ],
    mensagem: status === 'INFORMACOES_INSUFICIENTES' ? 'Exemplo de mensagem pedindo dados adicionais sobre a mercadoria.' : undefined,
    ...(comResultado && {
      resultado: {
        ncm: '00000000',
        descricaoOficial: 'Descrição oficial do código NCM (texto de exemplo)',
        situacaoValidacao: status === 'AGUARDANDO_REVISAO' ? 'PENDENTE_REVISAO' : 'VALIDADO_VERIFICACOES',
        analisadaEm: ha(6),
      },
      fundamentacao: {
        caracteristicas: ['Característica identificada de exemplo', 'Outra característica de exemplo'],
        motivos: ['Motivo de exemplo que sustentaria a sugestão'],
        regrasConsideradas: ['Regra de exemplo considerada na análise'],
        observacoes: ['Observação de exemplo'],
        limitacoes: ['Limitação de exemplo: a análise depende das informações fornecidas'],
      },
      alternativas: [
        { ncm: '00000001', descricao: 'Descrição de exemplo da alternativa 1', avaliacao: 'Avaliação de exemplo', pontuacao: { valor: 0.5, escala: '0 a 1', significado: 'Texto de exemplo explicando o que a pontuação mede.' } },
        { ncm: '00000002', descricao: 'Descrição de exemplo da alternativa 2', avaliacao: 'Avaliação de exemplo', pontuacao: { valor: 0.2, escala: '0 a 1', significado: 'Texto de exemplo explicando o que a pontuação mede.' } },
      ],
      validacao: {
        situacao: status === 'AGUARDANDO_REVISAO' ? 'PENDENTE_REVISAO' : 'VALIDADO_VERIFICACOES',
        situacaoNcm: 'Situação de exemplo',
        vigencia: { inicio: '2000-01-01', fim: null },
        verificacoes: [
          { nome: 'Verificação de exemplo 1', resultado: 'OK' },
          { nome: 'Verificação de exemplo 2', resultado: 'ALERTA', detalhe: 'Detalhe de exemplo' },
          { nome: 'Verificação de exemplo 3', resultado: 'NAO_REALIZADA' },
        ],
        regrasAplicaveis: ['Regra aplicável de exemplo'],
        divergencias: [],
        pendencias: status === 'AGUARDANDO_REVISAO' ? ['Pendência de exemplo'] : [],
      },
      fontes: [{ titulo: 'Documento de referência (exemplo)', identificacao: 'Identificação de exemplo', versao: 'Versão de exemplo', trecho: 'Trecho de exemplo do fundamento.' }],
      relatorio: { disponivel: true },
    }),
  }
}

type Cenario = 'CONCLUIDA' | 'AVALIANDO' | 'INFORMACOES_INSUFICIENTES' | 'AGUARDANDO_REVISAO'

export default function ExemploAnalise() {
  const { id, empresa, carregando } = useEmpresa()
  const [cenario, setCenario] = useState<Cenario>('CONCLUIDA')
  if (!empresa) return <EstadoEmpresa carregando={carregando} />
  return (
    <>
      <div style={{ marginBottom: 12 }}>
        <Segmented<Cenario>
          rotulo="Cenário de exemplo"
          valor={cenario}
          onChange={setCenario}
          opcoes={[
            { valor: 'CONCLUIDA', rotulo: 'Concluída' },
            { valor: 'AGUARDANDO_REVISAO', rotulo: 'Em revisão' },
            { valor: 'AVALIANDO', rotulo: 'Em andamento' },
            { valor: 'INFORMACOES_INSUFICIENTES', rotulo: 'Informações insuficientes' },
          ]}
        />
      </div>
      <div className="faixa-exemplo" role="note">
        <strong>EXEMPLO FICTÍCIO</strong> · Tela de demonstração disponível só no ambiente de desenvolvimento. Os dados
        abaixo não são resultado de análise, não correspondem a nenhuma mercadoria real e não devem ser usados.
      </div>
      <VisaoAnalise analise={exemplo(id, cenario)} empresa={empresa} exemplo />
    </>
  )
}
