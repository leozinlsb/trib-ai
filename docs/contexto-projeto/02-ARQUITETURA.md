# 02 — Arquitetura técnica

Tudo abaixo foi conferido no código em 08/10/2026 (branch `dev/prataliyann-hue`).

## Visão geral

```
Navegador ──/api (proxy Vite :5173)──▶ Spring Boot :8090 ──▶ H2 em arquivo (backend/data/tribia)
                                         ├─▶ Calculadora RTC oficial :8080 (opcional; fallback simplificado)
                                         └─▶ Google Gemini (opcional; cache/regra/respostas gravadas como fallback)
```

A calculadora oficial da Receita ocupa as portas 8080, 8081, 8082 e 80 (por isso a API usa 8090).

## Backend (`backend/`)

- **Linguagem/framework:** Java 21, Spring Boot 3.5.6 (`backend/pom.xml`), Maven Wrapper. Spring Security, Spring
  Data JPA, Hibernate, springdoc (Swagger em `/swagger-ui.html`), `spring-security-test` para testes.
- **Pacote raiz:** `backend/src/main/java/br/com/tribia/`

| Pacote | Conteúdo |
|---|---|
| `controller/` | `AuthController`, `ClienteController`, `NotaController`, `ApuracaoController`, `RevisaoController`, `RelatorioController`, `UsuarioController`, `DemoController` (só com profile demo) |
| `service/` | `ClienteService`, `NotaService`, `ParserNfeService`, `AuthService`, `UsuarioService`, `DashboardService`, `RelatorioService`; subpacotes `classificacao/`, `calculo/`, `apuracao/`, `painel/`, `tabelas/`, `nfe/`, `demo/` |
| `client/llm/` | `GeminiClient` (HTTP), `LlmClient` (interface), `LlmException` |
| `client/calculadora/` | `CalculadoraOficialClient`, `CalculadoraSimplificadaClient`, `CalculadoraClient`, DTOs |
| `model/` | entidades e enums (abaixo) |
| `repository/` | um repositório Spring Data por entidade |
| `security/` | `AcessoService`, `UsuarioLogado`, `UsuarioDetailsService`, `SpaCsrfTokenRequestHandler` |
| `config/` | `SecurityConfig`, `CorsConfig`, `AdminSeeder`, `SeedRunner`, `*Properties`, `OpenApiConfig`, `RestClientConfig` |
| `exception/` | `ApiExceptionHandler` (ProblemDetail), `ApiException`, `NotaRejeitadaException`, `RecursoNaoEncontradoException` |

### Serviços e responsabilidades

- `NotaService` + `ParserNfeService`: lê o XML, valida (modelo 55, `finNFe` 1/2/4, `tpNF` 0/1, chave de acesso x
  dados), define `Operacao` (VENDA, COMPRA, DEVOLUCAO_DE_VENDA, DEVOLUCAO_DE_COMPRA), grava `Nota` + `Item`.
  Duplicidade: restrição única `(cliente_id, chave)`. **Importar não classifica.**
- `service/classificacao/ClassificacaoService`: XML → cache privado da empresa → catálogo SEED → IA → (respostas gravadas no demo) → regra
  oficial do NCM. Detalhes em [04](04-INTELIGENCIA-FISCAL.md).
- `service/classificacao/RevisaoService` + `CriterioRevisao`: fila de revisão (item sem classificação, não aceito ou
  confiança < `tribia.revisao.confianca-minima` = 0,70); correções gravam no cache como validadas e recalculam.
- `service/calculo/CalculoService`: três fases (ler, calcular fora de transação, gravar); modo AUTO/OFICIAL/SIMPLIFICADA.
- `service/apuracao/RegrasApuracao`: regras puras de PIS/Cofins de hoje, base de 2027 e cálculo simplificado.
- `DashboardService`: agrega em memória a cada requisição (lista com indicadores, painel por cliente).
- `RelatorioService` / `RelatorioCsvService`: relatório JSON por empresa; CSV pt-BR (`;`, vírgula decimal, BOM).
- `service/tabelas/*`: carregam os CSVs oficiais de `resources/dados-oficiais/` (cClassTrib, NCM aplicável, IS).

### Entidades (`model/`)

`Cliente` (empresa; `ativo`, `fabricante`, regime) ← `Nota` (tipo, operação, finalidade, chave, competência,
`pagamentoConfirmado`) ← `Item` (NCM, CFOP, valores, PIS/Cofins, `creditavel`, grupo IBS/CBS destacado)
→ `Classificacao` (1:1: cst, cClassTrib, regime, confiança, origem, `aceita`, `revisada`) e `Calculo` (1:1: vCbs,
vIbsUf, vIbsMun, vIs, impostoHoje, imposto2027, `simulado`); `ClassificacaoCache` (chave com namespace
EMPRESA ou CATALOGO + hash de NCM/descrição; schema legado preservado); `RegistroRevisao` (trilha: ACEITE/CORRECAO/CREDITAVEL, antes/depois); `Usuario` (papel ADMIN ou
EMPRESA; `cliente` obrigatório só para EMPRESA).
Enums: `Regime` (LUCRO_REAL, LUCRO_PRESUMIDO, ...), `RegimeTributario` (INTEGRAL, REDUZIDA, ALIQUOTA_ZERO,
SEM_INCIDENCIA, OUTRO), `OrigemClassificacao` (XML, CACHE, IA, REGRA, …), `Natureza`, `TipoNota`, `Papel`.

### Persistência

H2 **em arquivo**: `jdbc:h2:file:./data/tribia` (relativo à pasta de execução, normalmente `backend/data/`),
`ddl-auto=update`, `data.sql` com `MERGE ... KEY(cnpj)` recria os 3 clientes de demo a cada partida. Console H2 em
`/h2-console` (somente ADMIN; exceção de CSRF preexistente preservada). A pasta `backend/data/` **não** estava no `.gitignore`; foi adicionada nesta
fase (alteração ainda não commitada). Nos testes: `src/test/resources/config/application.properties` usa H2 em
memória com UUID por contexto e `ddl-auto=create-drop`, e `tribia.llm.api-key=` vazio.

### Autenticação, sessão e autorização

- `SecurityConfig`: sessão em cookie `JSESSIONID` HttpOnly (timeout 8 h, SameSite=lax); CSRF por
  `CookieCsrfTokenRepository.withHttpOnlyFalse()` (cookie `XSRF-TOKEN`, header `X-XSRF-TOKEN`) com
  `SpaCsrfTokenRequestHandler`; público: `/api/auth/login`, `/api/auth/csrf`, `/api/auth/me`; `/api/**` exige
  login; 401/403 em `ProblemDetail`; BCrypt; form login e HTTP Basic desligados.
- Login (`AuthController`): troca o id de sessão (anti-fixação).
- `AdminSeeder`: cria o administrador **só se não existir ADMIN**; senha de `tribia.admin.senha`
  (`TRIBIA_ADMIN_SENHA` ou `application-local.properties`); sem senha, gera aleatória e a mostra uma vez no log.
- `AcessoService`: `atual()`, `exigirAdmin()`, `clienteAcessivel(id)` (404 para empresa alheia; 403 se desativada),
  `clientesVisiveis()`, `exigirAcessoNota()`, `notaAcessivel(id)`, `itemAcessivel(id)`.
- **P0.1:** checagens nos serviços, antes das consultas agregadas, mutações, lote, IA/calculadoras e gravação.
  Repositórios não têm filtro automático de tenant: novos consumidores devem passar pela política central.
  Lista só agrega empresas visíveis e inclui `ativo`. Carga genérica é ADMIN e somente SEED curado;
  revisão identifica item acessível e grava/valida cache privado da empresa proprietária.
- **B3-CACHE corrigido:** IA/MANUAL privados; consulta primeiro chave EMPRESA, depois catálogo e
  legado somente SEED. Sem novas colunas ou migração destrutiva. Legado IA/MANUAL/desconhecido
  sem dono ignorado, não atribuído/apagado. Classificações históricas já copiadas não alteradas;
  avaliar eventual ocorrência somente em ambiente autorizado antes de uso real.
  Ver [correção e testes](CORRECAO-B3-CACHE-2026-10-08.md). Etapa 1 ainda não concluída.
- **Seed:** ADMIN persistido inicializado antes da carga; principal sem senha em contexto temporário, restaurado
  em `finally`, sem sessão HTTP. `SeedService` e `ClassificacoesSeedLoader` exigem ADMIN em chamadas diretas.
- **Demo:** checklist global e reinicialização são ADMIN; habilitação por propriedade continua obrigatória.

### Integrações externas

| Integração | Config (`application.properties`) | Observação |
|---|---|---|
| Gemini | `tribia.llm.*`; chave via `GEMINI_API_KEY` | modelos `gemini-3.5-flash`, `gemini-3.5-flash-lite` |
| Calculadora RTC | `tribia.calculadora.url=http://localhost:8080/api` | iniciar com `backend/ferramentas/iniciar-calculadora.bat` |

### Configuração (principais chaves)

`tribia.seed.enabled/calcular`, `tribia.aliquotas.hoje.*` (PIS/Cofins), `tribia.aliquotas.ano2027.*` (CBS 9,43%,
IBS 0,05+0,05, `reducao-cbs-transicao=0.0`), `tribia.calculo.modo|data-fato-gerador|excluir-tributos-da-base|
credito-fornecedor-simples`, `tribia.classificacao.fallback-regra|codigos-por-adquirente`,
`tribia.revisao.confianca-minima`, `tribia.cors.origens`, `tribia.demo.*` (profile `demo`).
Arquivo local fora do Git: `backend/application-local.properties` (importado por `spring.config.import`).

## Frontend (`frontend/`)

- **Stack:** React 19, TypeScript 5.8, Vite 6, react-router-dom 7, lucide-react, fonte Inter; CSS próprio em
  `src/styles/` (sem Tailwind). **Sem biblioteca de testes** no `package.json` (scripts: dev, build, lint, preview).
- **Rotas** (`src/App.tsx`, `src/lib/rotas.ts`): `/` landing; `/login`; `/entrar`; `/dashboard` (admin: visão
  geral); `/dashboard/empresas`, `/dashboard/documentos`, `/dashboard/relatorios`, `/dashboard/configuracoes`,
  `/dashboard/inteligencia-fiscal` (admin); `/dashboard/empresas/:id[/documentos[/:nota]|/analises|/configuracoes|
  /inteligencia-fiscal[/nova|/:analise]]` (admin e a própria empresa). Guardas: `RotaProtegida`, `SoAdmin`,
  `AmbienteEmpresa` (proteção só de navegação; o servidor é quem deve garantir).
- **Estado** (`src/state/`): `AuthProvider` (sessão), `DadosProvider` (empresas e notas), `AtividadeProvider`
  (envios/notificações), `PreferenciasProvider` (localStorage via `lib/storage.ts`), `ToastProvider`.
- **API** (`src/api/`): `client.ts` (fetch com cookie, CSRF lido do cookie `XSRF-TOKEN`, evento de sessão expirada),
  `tribia.ts` (endpoints), `types.ts` (DTOs), `inteligenciaFiscal.ts` (contrato proposto).
- **Telas:** Landing, Login, VisaoGeral, Empresas, Documentos, NotaDetalhe, Relatorios, Configuracoes (admin e
  empresa), InicioEmpresa, AnalisesRelatorios (relatório, classificação dos itens, relacionamentos/grafo),
  Inteligência Fiscal (histórico, nova análise, acompanhamento, resultado), `ExemploAnalise` (só DEV).
- **Proxy:** `vite.config.ts` repassa `/api` para `TRIBIA_BACKEND_URL` (padrão `http://localhost:8090`).
- **Elementos sem comportamento real:** telas de Inteligência Fiscal (sem backend); a classificação exibida em
  `AnalisesRelatorios`/`NotaDetalhe` lê o grupo IBS/CBS vindo do XML, não a classificação do backend (B5).
