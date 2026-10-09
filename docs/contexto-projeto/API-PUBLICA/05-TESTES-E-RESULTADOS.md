# 05 — Testes e resultados

Executados em 09/10/2026, máquina local (Windows 10, JDK Temurin 21.0.12, Node 24.20), **sem chaves reais**:
o `LlmClient` (Gemini) é simulado nos testes e a JEV fica `DESLIGADO`. Nenhuma chamada paga.

## Suíte do backend

| Execução | Casos (`<testcase>` nos relatórios) | Falhas | Erros | Ignorados |
|---|---|---|---|---|
| Base, antes de qualquer alteração (commit `52519e5`) | 318 | 0 | 0 | 8 |
| 1ª completa após a implementação | 353 | **1** | 0 | 8 |
| Após a correção | 353 (Maven: 352) | 0 | 0 | 8 |
| Ordem inversa (`-Dsurefire.runOrder=reversealphabetical`) | Maven: 352 | 0 | 0 | 8 |

Os 8 ignorados são os de antes (contratos da calculadora RTC, Gemini real e geradores opt-in).

**Falha encontrada e corrigida (não mascarada):** `AnaliseFiscalControllerTest$Fluxo.listaFiltraPaginaEIndicadores`
contou análises a mais na empresa 1. Causa: o Spring reaproveitou o mesmo contexto (e o mesmo H2 em memória) entre
esse teste (`@Transactional`, faz rollback) e os testes novos (sem `@Transactional`, confirmam de verdade). Correção
nos testes novos: propriedade marcadora `tribia.teste.contexto=api-publica`, que dá a eles contexto e banco próprios.
O teste existente não foi alterado.

## Testes novos (35)

`backend/src/test/java/br/com/tribia/apipublica/`:

| Classe | Casos | Cobre |
|---|---|---|
| `ApiPublicaUnidadeTest` | 7 | Formato/unicidade/hash/prefixo das chaves; segredo errado; escopos; limitador por janela com relógio fixo; todo `StatusAnalise` mapeado e `finalizada` coerente; normalização para idempotência; erro 500 sem detalhe técnico |
| `ApiPublicaV1Test$Autenticacao` | 9 | Sem chave, malformada, segredo errado, prefixo inexistente (mesma resposta); X-Request-Id; revogada; expirada; empresa desativada; escopos mínimos; sessão de ADMIN não abre `/api/v1` e chave não abre rotas internas; sem cookie/sessão |
| `ApiPublicaV1Test$FluxoComMotorFiscal` | 6 | Fluxo completo com o motor real (NCM vigente, fundamentação, alternativas, fontes) e visibilidade na plataforma; divergência → revisão → revisão humana pela plataforma refletida na API; falha da IA sem vazar detalhe; informações insuficientes; 7 tipos de entrada inválida sem gravar nem chamar IA; listagem por referência e consumo |
| `ApiPublicaV1Test$Multiempresa` | 3 | A vê, outra chave de A vê, B recebe 404 idêntico ao inexistente e lista vazia; ids manipulados; empresa no corpo ignorada |
| `ApiPublicaV1Test$Idempotencia` | 4 | Repetição (inclusive com formatação diferente) sem nova chamada à IA; corpo diferente → 409; chaves diferentes não compartilham; **8 requisições simultâneas** → 1 análise, 1 chamada à IA, 7 repetidas |
| `ApiPublicaV1Test$ConsumoEAssincrono` | 1 | Pool assíncrono real com a IA segurada: limite de simultâneas (429), conclusão, cota diária (429 + Retry-After), repetição não consome cota, `/uso` |
| `ApiPublicaV1Test$LimitesDeRequisicao` | 2 | 429 por minuto com `Retry-After`/`X-RateLimit-*`, sem afetar outra chave; 429 após falhas de autenticação do mesmo IP, sem afetar outro IP |
| `ApiPublicaV1Test$GestaoDeChaves` | 3 | Emissão por ADMIN, hash gravado, uso, listagem sem segredo/hash, revogação, **log inteiro capturado sem a chave**; EMPRESA 403, sem login 401, sem CSRF 403, escopo desconhecido e validade acima do máximo 400; OpenAPI `publica-v1` com esquema `X-API-Key` e `interna` sem `/api/v1` |

**Mutação de controle (isolamento):** removido temporariamente o filtro de empresa de
`SolicitacaoApiRepository.buscarDaEmpresa` → `Multiempresa` falhou em 2 casos ("404 esperado, 200 recebido").
Filtro restaurado; arquivo conferido.

## Ensaio ao vivo (servidor real, 09/10/2026)

Backend em `:8091`, H2 em memória, `.env`/`application-local.properties` desviados para caminhos inexistentes, IA e
JEV desligadas, senha de ADMIN sintética gerada na hora (fora do repositório).

| Passo | Resultado |
|---|---|
| `/swagger-ui.html` | 302 → `/swagger-ui/index.html`; `swagger-config` lista "API pública v1 (integradores)" e "Plataforma (interna)" |
| `/v3/api-docs/publica-v1` | 200; paths `/api/v1/analises`, `/api/v1/analises/{id}`, `/api/v1/uso`; esquema `chaveApi` (`X-API-Key`, header) |
| `emitir-chave-local.mjs 1` | Chave emitida para a empresa 1, escopos completos, validade 365 dias |
| `cliente-tribia.mjs` | 202 `RECEBIDA` → `FALHOU`: "A IA não está configurada neste ambiente (GEMINI_API_KEY)..." (esperado sem IA); exit 2 |
| Mesma Idempotency-Key | Mesmo id, "repetição idempotente"; contador do dia continuou 1 |
| Mesma Idempotency-Key, outro produto | 409 `IDEMPOTENCIA_CONFLITO` com `requestId`; exit 1 |
| Sem chave / chave errada | 401 `CHAVE_AUSENTE` / `CHAVE_INVALIDA` |
| Chave da empresa 2 consultando id da empresa 1 | 404 `ANALISE_NAO_ENCONTRADA`, mesmo `detail` do id inexistente; listagem vazia |
| Chave em `/api/clientes` | 401 (rota interna) |
| Corpo inválido | 400 `DADOS_INVALIDOS` com `campos` |
| Rota inexistente `/api/v1/nada` | 404 no formato padrão do Spring, sem stack trace |
| Revogação pelo ADMIN | 200 `REVOGADA`; uso seguinte 401 `CHAVE_REVOGADA`; listagem sem segredo/hash; `ultimoUsoEm` preenchido |
| Log do servidor | 0 ocorrências do segredo da chave; só o prefixo; 0 `ERROR`/exceção (1 WARN esperado: IA não configurada) |
| `backend/data/tribia.mv.db` (banco real) | Não modificado (data 08/10 22:47) |

## Frontend

Sem alteração de código. `npm test`: 10 / 10 aprovados. Build/lint/E2E não executados nesta missão (nenhum arquivo do
front mudou e nenhuma rota usada por ele mudou de contrato).

## Não executado

- Análise com **Gemini real** (custo/cota; sem autorização) — o resultado completo está coberto pelos testes com IA
  simulada e pelo motor real de validação de NCM.
- JEV real; teste de carga; execução com várias instâncias da API.
