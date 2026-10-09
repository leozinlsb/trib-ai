# 02 — Implementação

Estado em 09/10/2026: **implementado e testado** (ver [05-TESTES-E-RESULTADOS.md](05-TESTES-E-RESULTADOS.md)).

## Arquivos novos (backend)

Pacote `backend/src/main/java/br/com/tribia/apipublica/`:

| Arquivo | Responsabilidade |
|---|---|
| `ApiPublicaProperties.java` | Limites padrão `tribia.api-publica.*` (por minuto, cota diária, simultâneas, falhas de autenticação por IP, validade máxima) |
| `model/EscopoApi.java` | `ANALISES_CRIAR`, `ANALISES_LER` |
| `model/ChaveApi.java` | Tabela `chave_api`: prefixo público, SHA-256 da chave, integrador, **empresa fixa**, escopos, validade, revogação, último uso, limites próprios opcionais |
| `model/SolicitacaoApi.java` | Tabela `solicitacao_api`: UUID público, chave, empresa, `AnaliseFiscal` (1:1), Idempotency-Key + hash do corpo, referência externa. Única `(chave_id, idempotency_key)` |
| `repository/ChaveApiRepository.java` | Busca por prefixo (com empresa), listagem do admin, registro de último uso (no máx. 1 escrita/min) |
| `repository/SolicitacaoApiRepository.java` | Toda leitura pública filtra por **id público + empresa**; contagens de cota e de análises em andamento |
| `seguranca/ChavesApi.java` | Geração (`tribia_<12 hex>_<43 base64url>`, 256 bits no segredo, `SecureRandom`), SHA-256, comparação em tempo constante |
| `seguranca/FiltroRequestId.java` | `X-Request-Id` (aceita o do integrador se seguro, senão UUID), MDC `requestId` |
| `seguranca/FiltroChaveApi.java` | Autenticação por `X-API-Key`, revogação, validade, empresa ativa, limite por minuto, limite de falhas por IP |
| `seguranca/LimitadorRequisicoes.java` | Janela fixa de 1 min em memória (`ConcurrentHashMap.compute`) |
| `seguranca/IntegradorAutenticado.java` | Principal da requisição (chave, empresa, escopos, limites) e o token do Spring Security |
| `seguranca/RespostaErroPublica.java` | ProblemDetail com `codigo` e `requestId` (filtros e handler) |
| `seguranca/ApiPublicaSecurityConfig.java` | `SecurityFilterChain` `@Order(1)` só para `/api/v1/**`: stateless, sem CSRF/sessão/form/basic; escopo por rota |
| `servico/ApiPublicaService.java` | Criação (idempotência, trava por chave, cota, simultâneas, transação única, despacho após commit), consulta, listagem, uso |
| `servico/MapeadorAnalisePublica.java` | `AnaliseFiscal` + `ResultadoAnaliseFiscal` → contrato público; não inventa campos |
| `servico/ChaveApiService.java` | Emissão/listagem/revogação (só ADMIN, conferido no serviço) |
| `web/ApiPublicaAnaliseController.java` | `POST/GET /api/v1/analises`, `GET /api/v1/analises/{id}`, `GET /api/v1/uso` + anotações OpenAPI |
| `web/ApiPublicaExceptionHandler.java` | Erros padronizados dos controllers públicos (precedência sobre o handler da plataforma, só para eles) |
| `web/ApiPublicaException.java` | Erro com status, código, extras e cabeçalhos (Retry-After) |
| `web/ChaveApiAdminController.java` | `/api/admin/chaves-api` (POST, GET, POST `/{id}/revogar`) na cadeia da plataforma (sessão + CSRF) |
| `dto/ApiPublicaDtos.java` | Contrato público v1 (entrada, saída, estados) com `@Schema` |
| `dto/ChaveApiDtos.java` | Formulário e respostas da gestão de chaves |

## Arquivos alterados (backend)

| Arquivo | Mudança | Impacto |
|---|---|---|
| `service/fiscal/AnaliseFiscalService.java` | `iniciar` dividido em `registrarNova` (grava na transação de quem chama) e `despachar` (fila; 503 se cheia) + `detalheAutorizado` | Comportamento da plataforma igual (testes da Inteligência Fiscal verdes). Os métodos novos não conferem acesso: o chamador autoriza (documentado no Javadoc) |
| `config/OpenApiConfig.java` | Grupos `interna` e `publica-v1`; esquema `chaveApi` (header `X-API-Key`) só no grupo público | Swagger ganha seletor de documento. Profile `prod` continua com Swagger desligado |
| `resources/application.properties` | Bloco `tribia.api-publica.*` | Só padrões; nenhuma chave/segredo |

Nada foi alterado em: frontend, `ProcessadorAnaliseFiscal`, `PesquisaNcmIa`, `ValidadorNcm`, JEV, regras fiscais, cálculo,
`SecurityConfig` da plataforma, `AcessoService`, autenticação de usuários.

## Persistência

`ddl-auto=update` cria as tabelas `chave_api` e `solicitacao_api` na próxima inicialização (só acrescenta; nada é
apagado). Não há ferramenta de migração no projeto (sem Flyway/Liquibase); manter o padrão existente foi decisão
consciente (ver [09-DECISOES-ARQUITETURAIS.md](09-DECISOES-ARQUITETURAIS.md), D8).

## Exemplos e ferramentas

| Arquivo | O que faz |
|---|---|
| `exemplos/api-publica/cliente-tribia.mjs` | Cliente de integração (Node 18+, sem dependências): lê URL e chave do ambiente, envia, acompanha com timeout e backoff, trata 429/503 com Retry-After e a mesma Idempotency-Key, imprime o resultado |
| `exemplos/api-publica/emitir-chave-local.mjs` | Login de ADMIN + CSRF num backend local e emissão da chave (imprime a chave só no stdout, uma vez) |
| `exemplos/api-publica/produto-exemplo.json` | Produto fictício (detergente) com NCM informada desatualizada, para mostrar divergência |

## Checkpoints de execução

| Fase | Situação |
|---|---|
| 1–2 Contexto e arquitetura | Concluído ([01](01-CONTEXTO-E-ARQUITETURA.md)) |
| 3 Contratos | Concluído ([03](03-CONTRATOS-E-ENDPOINTS.md)) |
| 4–5 Chaves, segurança, empresas | Concluído ([04](04-SEGURANCA-E-MULTITENANCY.md)) |
| 6–7 Criação e consulta | Concluído |
| 8 Idempotência | Concluído (inclui concorrência) |
| 9 Consumo | Concluído (minuto em memória; cota e simultâneas no banco) |
| 10 Erros | Concluído |
| 11 OpenAPI | Concluído (`/v3/api-docs/publica-v1`) |
| 12–13 Cliente e demo | Concluído ([06](06-GUIA-DE-USO.md), [08](08-ROTEIRO-DEMO.md)) |
| 14–15 Testes e correções | Concluído ([05](05-TESTES-E-RESULTADOS.md)) |
| 16–17 Documentação e commit | Ver [07-HANDOFF-FINAL.md](07-HANDOFF-FINAL.md) |
