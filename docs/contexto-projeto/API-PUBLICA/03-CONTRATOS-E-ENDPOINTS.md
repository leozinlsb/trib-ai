# 03 — Contratos e endpoints (v1)

Base: `http://localhost:8090` (local). OpenAPI: `/v3/api-docs/publica-v1`; Swagger UI: `/swagger-ui.html` → documento
"API pública v1 (integradores)". O profile `prod` desliga o Swagger (decisão anterior mantida).

## Convenções

- **Autenticação:** `X-API-Key: tribia_<prefixo>_<segredo>` em toda chamada. A empresa vem da chave.
- **Formato:** JSON UTF-8; datas ISO-8601 em UTC (`Instant`). Campos desconhecidos no corpo são ignorados.
- **Rastreio:** `X-Request-Id` volta em toda resposta (o do integrador, se tiver 8–64 caracteres `[A-Za-z0-9._-]`).
- **Limite por minuto:** `X-RateLimit-Limit`, `X-RateLimit-Remaining`, `X-RateLimit-Reset` (segundos).
- **Cache:** respostas com `Cache-Control: no-store`.
- **Evolução:** v1 pode ganhar campos; remover/renomear/mudar significado exige `/api/v2`.

## Endpoints

| Método e rota | Escopo | Sucesso | Descrição |
|---|---|---|---|
| `POST /api/v1/analises` | `ANALISES_CRIAR` | 202 | Envia a mercadoria; responde com a análise criada (ou a repetida) |
| `GET /api/v1/analises/{id}` | `ANALISES_LER` | 200 | Status e resultado |
| `GET /api/v1/analises?referenciaExterna=&pagina=&tamanho=` | `ANALISES_LER` | 200 | Lista da empresa, mais recentes primeiro (tamanho 1–100) |
| `GET /api/v1/uso` | `ANALISES_LER` ou `NOTAS_LER` | 200 | Empresa da chave, escopos, limites, consumo do dia |
| `POST /api/v1/notas` | `NOTAS_ENVIAR` | 202 | XML da NF-e: importa, classifica e calcula 2027 (fase 1, ver [11](11-FASE-1-NOTAS-E-COMPARATIVO.md)) |
| `GET /api/v1/notas/{id}` | `NOTAS_LER` | 200 | Itens classificados, cálculo de 2027 e comparativo da nota |
| `GET /api/v1/notas` | `NOTAS_LER` | 200 | Notas enviadas pela API para a empresa |
| `GET /api/v1/comparativo` | `NOTAS_LER` | 200 | Comparativo hoje × 2027 da empresa (site + API) |
| `POST /api/v1/classificacoes` | `CLASSIFICAR` | 200 | Até 50 produtos (NCM + descrição) → CST/cClassTrib sugeridos (fase 2, ver [12](12-FASE-2-CLASSIFICADOR-E-CALCULADORA.md)) |
| `POST /api/v1/calculos/simular` | `CALCULAR` | 200 | Até 100 itens → CBS/IBS/IS de 2027 sem gravar nada (fase 2) |

Gestão (rotas **internas**, sessão de ADMIN + CSRF; não aceitam chave de API):

| Método e rota | Descrição |
|---|---|
| `POST /api/admin/chaves-api` | `{clienteId, nomeIntegrador, escopos?, validadeDias?, requisicoesPorMinuto?, cotaDiariaAnalises?, maxAnalisesSimultaneas?}` → 201 `{chave, aviso, dados}` (chave completa só aqui) |
| `GET /api/admin/chaves-api?clienteId=` | Lista sem segredo nem hash |
| `POST /api/admin/chaves-api/{id}/revogar` | Revogação definitiva e idempotente |

## POST /api/v1/analises

Cabeçalhos: `X-API-Key` (obrigatório), `Idempotency-Key` (recomendado; 1–100 `[A-Za-z0-9_.:-]`).

```json
{
  "referenciaExterna": "ERP-SKU-000123",
  "mercadoria": {
    "nome": "Sabonete de glicerina 90 g",
    "descricao": "Sabonete em barra de glicerina para higiene pessoal, embalado individualmente.",
    "composicao": "glicerina, óleo vegetal",
    "finalidade": "higiene pessoal",
    "caracteristicas": "barra de 90 g",
    "ncmInformada": "3401.11.90"
  }
}
```

| Campo | Regra (mesma da tela, `MercadoriaEntradaDto`) |
|---|---|
| `mercadoria.nome` | obrigatório, até 200 |
| `mercadoria.descricao` | obrigatório, 20–4000 |
| `composicao`, `finalidade`, `caracteristicas` | opcionais, até 2000 |
| `ncmInformada` | opcional, 8 dígitos com ou sem pontos |
| `referenciaExterna` | opcional, até 100, `[A-Za-z0-9._:/#-]` |

Não há campo de empresa: enviar `clienteId`/`cnpj` não tem efeito (testado). Anexos não são aceitos na v1
(ver [10](10-PENDENCIAS-E-EVOLUCAO.md)).

Resposta 202: corpo igual ao do GET; cabeçalhos `Location: /api/v1/analises/{id}` e `Idempotent-Replayed: true|false`.

## GET /api/v1/analises/{id} — corpo

```json
{
  "id": "3f1c2a9e-8d4b-4c11-9a57-2b6f0e7d1c34",
  "referenciaExterna": "ERP-SKU-000123",
  "status": "CONCLUIDA",
  "etapa": "CONCLUIDA",
  "finalizada": true,
  "criadaEm": "2026-10-09T07:30:00Z",
  "atualizadaEm": "2026-10-09T07:30:12Z",
  "mercadoria": {"nome": "...", "descricao": "...", "composicao": "...", "finalidade": null,
                 "caracteristicas": null, "ncmInformada": "34011190"},
  "resultado": {
    "natureza": "SUGESTAO_AUTOMATICA_VERIFICADA",
    "ncmSugerida": "34011190",
    "ncmSugeridaFormatada": "3401.11.90",
    "descricaoOficial": "Sabões ... -- De toucador ... --- Outros",
    "situacaoValidacao": "VALIDADO_VERIFICACOES",
    "analisadaEm": "2026-10-09T07:30:12Z",
    "fundamentacao": {"caracteristicasIdentificadas": [], "motivos": [], "regrasConsideradas": [],
                      "observacoes": [], "limitacoes": []},
    "alternativas": [{"ncm": "34011190", "ncmFormatada": "3401.11.90", "descricao": "...", "avaliacao": "...",
                      "pontuacaoCompatibilidade": null}],
    "validacao": {"situacao": "VALIDADO_VERIFICACOES", "situacaoNcm": "Consta da NCM vigente",
                  "vigencia": {"inicio": "2022-04-01", "fim": null},
                  "verificacoes": [{"nome": "Existência e vigência na NCM", "resultado": "OK", "detalhe": "..."}],
                  "regrasAplicaveis": [], "divergencias": [], "pendencias": []},
    "fontes": [{"titulo": "Nomenclatura Comum do Mercosul (NCM)", "identificacao": "...", "versao": "...",
                "trecho": null, "url": "..."}]
  },
  "revisaoHumana": {"situacao": "NAO_SOLICITADA", "decisao": null, "ncmDecidida": null, "observacao": null,
                    "revisadaEm": null, "totalRevisoes": 0},
  "erro": null,
  "mensagem": null,
  "avisos": ["Sugestão gerada por IA ... não é classificação fiscal definitiva ...",
             "A validade da NCM não define o tratamento tributário ..."]
}
```

Valores ilustrativos; a forma é a testada em `ApiPublicaV1Test`.

### Estados públicos (mapeamento da máquina existente, sem estado novo no motor)

| Público | Interno (`StatusAnalise`) | `finalizada` | `resultado` |
|---|---|---|---|
| `RECEBIDA` | `AGUARDANDO` | false | null |
| `EM_PROCESSAMENTO` | `INTERPRETANDO`, `PESQUISANDO_NCM`, `AVALIANDO`, `VALIDANDO`, `GERANDO_RELATORIO` | false | null |
| `CONCLUIDA` | `CONCLUIDA` | true | presente |
| `AGUARDANDO_REVISAO` | `AGUARDANDO_REVISAO` | true* | presente |
| `INFORMACOES_INSUFICIENTES` | `INFORMACOES_INSUFICIENTES` | true | null; `erro` presente |
| `FALHOU` | `FALHA` | true | null; `erro` presente |

\* Não muda sozinho; muda para `CONCLUIDA` quando uma pessoa revisa na plataforma.
`INFORMACOES_INSUFICIENTES` foi mantido como estado público próprio (a missão listava cinco) porque o motor já o
distingue e fundi-lo com `FALHOU` esconderia que o integrador precisa mandar mais dados (decisão D5).

### Distinções de natureza do resultado

| `resultado.natureza` | Significado | `revisaoHumana.situacao` |
|---|---|---|
| `SUGESTAO_AUTOMATICA_VERIFICADA` | IA + verificações automáticas sem pendência. **Ainda é sugestão** | `NAO_SOLICITADA` |
| `SUGESTAO_AUTOMATICA_PENDENTE_REVISAO` | IA com divergência/pendência (ex.: NCM informada diferente, código fora da NCM vigente, confiança baixa, trecho suspeito de instrução à IA) | `PENDENTE` |
| `DECISAO_REVISAO_HUMANA` | Uma pessoa da empresa decidiu na plataforma; `ncmSugerida` continua a da IA (evidência) e `revisaoHumana.ncmDecidida` traz a decisão | `REALIZADA` |

Sem resultado: `revisaoHumana.situacao = NAO_APLICAVEL`. Quem revisou (nome/e-mail) não sai na API pública.

## Erros (`application/problem+json`)

```json
{"type": "about:blank", "title": "Idempotency-Key já usada", "status": 409,
 "detail": "Esta Idempotency-Key já foi usada com outro conteúdo...", "instance": "/api/v1/analises",
 "codigo": "IDEMPOTENCIA_CONFLITO", "requestId": "0b8f...", "analiseId": "3f1c..."}
```

| HTTP | `codigo` | Quando |
|---|---|---|
| 400 | `DADOS_INVALIDOS` (+ `campos`) | Validação do corpo |
| 400 | `JSON_INVALIDO` | Corpo ilegível |
| 400 | `IDEMPOTENCY_KEY_INVALIDA` | Formato da Idempotency-Key |
| 400 | `PARAMETRO_INVALIDO` | Paginação fora do intervalo |
| 401 | `CHAVE_AUSENTE` / `CHAVE_INVALIDA` / `CHAVE_REVOGADA` / `CHAVE_EXPIRADA` | Autenticação (`WWW-Authenticate: ApiKey header="X-API-Key"` nas duas primeiras) |
| 403 | `ESCOPO_INSUFICIENTE` | Chave sem o escopo da rota |
| 403 | `EMPRESA_DESATIVADA` | Empresa da chave desativada |
| 404 | `ANALISE_NAO_ENCONTRADA` | Id inexistente, malformado **ou de outra empresa** (resposta idêntica) |
| 409 | `IDEMPOTENCIA_CONFLITO` (+ `analiseId`) | Mesma Idempotency-Key, corpo diferente |
| 415 | `TIPO_CONTEUDO_NAO_SUPORTADO` | Corpo que não é JSON |
| 429 | `LIMITE_REQUISICOES` | Limite por minuto da chave (`Retry-After`) |
| 429 | `COTA_DIARIA_EXCEDIDA` | Cota diária (`Retry-After` até a meia-noite de Brasília) |
| 429 | `LIMITE_ANALISES_SIMULTANEAS` | Análises da chave ainda em processamento |
| 429 | `MUITAS_FALHAS_AUTENTICACAO` | Muitas chaves ausentes/inválidas do mesmo IP no minuto |
| 500 | `ERRO_INTERNO` | Inesperado; sem detalhe técnico, com `requestId` |
| 503 | `SERVICO_OCUPADO` (+ `analiseId`) | Fila do servidor cheia; a solicitação fica como `FALHOU` e deve ser reenviada com nova Idempotency-Key |

Erros de **processamento** (IA fora do ar, resposta inválida, dados insuficientes) não são HTTP de erro: a análise
termina com `status` `FALHOU`/`INFORMACOES_INSUFICIENTES` e `erro: {codigo, mensagem, podeRepetir}`.
Rotas inexistentes sob `/api/v1` respondem com o erro padrão do Spring (sem stack trace), não com este formato.
