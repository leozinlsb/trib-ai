# Inteligência Fiscal: contrato de API proposto

**Situação:** nenhum destes endpoints existe no backend ainda. O frontend já os consome
(`src/api/inteligenciaFiscal.ts`). Enquanto o servidor responder 404 de rota inexistente, as telas mostram
"Análise fiscal ainda não disponível" e não simulam resultados.

Toda a lógica fica no backend: chamadas ao Gemini e ao JEV, interpretação da mercadoria, busca NCM, validação
fiscal, leitura dos anexos, geração do relatório, persistência e controle de acesso. O navegador não recebe
chaves nem chama esses serviços.

## Regras gerais

- **Autenticação e acesso:** valem as regras atuais (sessão + CSRF). Cada endpoint deve aplicar o
  `AcessoService`. Usuário de empresa só acessa as análises da própria empresa: análise de outra empresa
  devolve 404. Administrador acessa todas.
- **Erros:** no formato `ProblemDetail`, como o resto da API. Para "análise não encontrada", use o título
  `Recurso não encontrado` (`RecursoNaoEncontradoException`). O front usa esse título para distinguir um
  recurso inexistente de uma rota que ainda não existe.
- **Datas:** ISO 8601. **NCM:** 8 dígitos, só números.

## Endpoints

### `GET /api/clientes/{clienteId}/analises-fiscais`

Histórico paginado da empresa.

Parâmetros opcionais:

| Parâmetro | Formato | Descrição |
|---|---|---|
| `q` | texto | Busca no nome da mercadoria ou na NCM |
| `status` | `StatusAnalise` | Filtra pelo status |
| `de`, `ate` | `AAAA-MM-DD` | Período, pela data de criação |
| `pagina` | número | Começa em 0 |
| `tamanho` | número | Itens por página |

Resposta `200`:
```json
{ "itens": [AnaliseResumo], "total": 42, "pagina": 0, "tamanho": 10 }
```

### `GET /api/clientes/{clienteId}/analises-fiscais/indicadores`

```json
{ "total": 42, "concluidas": 30, "emProcessamento": 2, "aguardandoRevisao": 3 }
```

### `POST /api/clientes/{clienteId}/analises-fiscais`

Multipart. Cria a análise e processa de forma **assíncrona**.

- Parte `dados` (`application/json`): `MercadoriaEntrada`.
- Partes `arquivos` (opcionais): até 10 arquivos de até 10 MB.
  - Formatos aceitos pelo front: pdf, png, jpg, jpeg, webp, doc, docx, xls, xlsx, txt.

Resposta `202` com `AnaliseResumo`, status `AGUARDANDO`. Erros: `400` (dados inválidos), `409` (empresa
desativada), `413` (arquivo grande).

### `GET /api/analises-fiscais/{id}`

`AnaliseDetalhe`. O front consulta este endpoint a cada 4 s enquanto o status for uma etapa em andamento.
Se o backend preferir eventos, a alternativa é `GET /api/analises-fiscais/{id}/eventos` com
`text/event-stream`, emitindo o novo `status` a cada mudança. Nesse caso, basta trocar o efeito de polling
em `pages/fiscal/AnaliseFiscal.tsx`.

### `GET /api/analises-fiscais/{id}/relatorio` (opcional)

Arquivo do relatório (PDF), se o backend gerar. Quando existir, preencher `relatorio.downloadUrl` no detalhe.
O botão "Baixar relatório" só aparece com essa URL.

## Tipos

```ts
type StatusAnalise =
  | 'AGUARDANDO' | 'INTERPRETANDO' | 'PESQUISANDO_NCM' | 'AVALIANDO' | 'VALIDANDO'
  | 'GERANDO_RELATORIO' | 'CONCLUIDA'                          // fluxo normal, nesta ordem
  | 'FALHA' | 'INFORMACOES_INSUFICIENTES' | 'AGUARDANDO_REVISAO' // situações especiais

type SituacaoValidacao =
  'VALIDADO_VERIFICACOES' | 'PENDENTE_REVISAO' | 'INFORMACOES_INSUFICIENTES' | 'INCONSISTENCIA'

interface MercadoriaEntrada {
  nome: string; descricao: string
  composicao?: string; finalidade?: string; caracteristicas?: string; ncmAtual?: string
}

interface AnaliseResumo {
  id: number; clienteId: number; mercadoria: string; ncmSugerida: string | null
  status: StatusAnalise; criadaEm: string; atualizadaEm: string; relatorioDisponivel: boolean
}

interface AnaliseDetalhe extends AnaliseResumo {
  entrada: MercadoriaEntrada
  anexos: { nome: string; tamanho: number; tipo: string }[]
  historico: { status: StatusAnalise; em: string }[]  // etapas percorridas (para o acompanhamento)
  resultado?: { ncm: string; descricaoOficial: string; situacaoValidacao: SituacaoValidacao; analisadaEm: string }
  fundamentacao?: {
    caracteristicas: string[]; motivos: string[]; regrasConsideradas: string[]
    observacoes: string[]; limitacoes: string[]
  }
  alternativas?: {
    ncm: string; descricao: string; avaliacao: string
    pontuacao?: { valor: number; escala: string; significado: string }  // JEV
  }[]
  validacao?: {
    situacao: SituacaoValidacao; situacaoNcm: string
    vigencia?: { inicio?: string; fim?: string | null }
    verificacoes: { nome: string; resultado: 'OK' | 'ALERTA' | 'FALHA' | 'NAO_REALIZADA'; detalhe?: string }[]
    regrasAplicaveis: string[]; divergencias: string[]; pendencias: string[]
  }
  fontes?: { titulo: string; identificacao?: string; versao?: string; trecho?: string; url?: string }[]
  mensagem?: string                                    // motivo da falha ou dados que faltam
  relatorio?: { disponivel: boolean; downloadUrl?: string }
}
```

## Como o front apresenta os dados

- **Pontuação do JEV:** aparece como valor + escala ("0,82 (escala 0 a 1)"), com o texto de `significado`,
  e o aviso de que não é probabilidade de acerto. O front nunca converte em porcentagem.
- **Validação:** `VALIDADO_VERIFICACOES` aparece como "Validado pelas verificações disponíveis", sempre com o
  aviso de que não equivale a aprovação da Receita Federal.
- **Fontes:** aparecem exatamente como vierem; o front não cria referências.
- **Acompanhamento:** sem barra de porcentagem. O front mostra a sequência de etapas a partir de `status`
  e `historico`.
- **Ambiente de desenvolvimento:** a rota `/dashboard/empresas/:id/inteligencia-fiscal/exemplo` mostra dados
  fictícios marcados como tal, para revisar o layout. Ela não existe no build de produção.
