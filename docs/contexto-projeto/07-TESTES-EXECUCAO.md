# 07 — Testes e execução (Windows / PowerShell)

## Jeito mais simples (08/10/2026, noite)

```powershell
# na raiz do repositório; encontra o JDK 21 em %USERPROFILE%\.jdks\ sozinho
powershell -ExecutionPolicy Bypass -File .\iniciar-backend.ps1 -SkipRun                 # confere JDK e .env (sem valores)
powershell -ExecutionPolicy Bypass -File .\iniciar-backend.ps1 -Testes                  # suíte completa: 317 / 0 falhas / 8 ignorados
powershell -ExecutionPolicy Bypass -File .\iniciar-backend.ps1 -Testes -Filtro "JevHttpTest,AnaliseFiscalControllerTest*"
powershell -ExecutionPolicy Bypass -File .\iniciar-backend.ps1                          # sobe a API com o .env (atenção se a JEV estiver em HTTP)
```

Os testes são herméticos: nunca leem o `.env` nem o `application-local.properties` (o `pom.xml` aponta os imports para
arquivos inexistentes). Para um backend de E2E sem o `.env`, passe `--tribia.arquivo-local=x --tribia.arquivo-env-raiz=x
--tribia.arquivo-env-backend=x` (o antigo `--spring.config.import=` **não** isola).

## Validação mais recente — Inteligência Fiscal (08/10/2026, tarde)

Backend: **290 testes, 0 falhas, 0 erros, 7 ignorados**, em ordem normal e inversa
(`.\mvnw.cmd test "-Dsurefire.runOrder=reversealphabetical"`). Front: `npm run build`, `npm run lint` e `npm test` verdes.

E2E novo (indicadores, pagamento, recálculo, PDF sem resultado, isolamento), contra API isolada **sem o .env**
(a chave real do Gemini não pode ser carregada) e sem IA:

```powershell
# backend (JDK 21): banco em memória, sem .env, sem chave, JEV desligada
$env:GEMINI_API_KEY = ''; $env:JEV_API_KEY = ''
.\mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=demo "-Dspring-boot.run.arguments=--server.address=127.0.0.1 --server.port=8190 --tribia.arquivo-local=x --tribia.arquivo-env-raiz=x --tribia.arquivo-env-backend=x --spring.datasource.url=jdbc:h2:mem:e2e;DB_CLOSE_DELAY=-1 --tribia.admin.senha=senha-apenas-e2e-2026 --tribia.llm.api-key= --tribia.jev.modo=DESLIGADO --tribia.calculo.modo=SIMPLIFICADA"

# front (pasta frontend)
npm run build
$env:TRIBIA_BACKEND_URL = 'http://127.0.0.1:8190'; npx vite preview --port 15173 --host 127.0.0.1

# teste (pasta frontend; backend recém-iniciado: o roteiro parte do estado vazio)
$env:TRIBIA_E2E_ISOLADO = '1'; $env:TRIBIA_E2E_SENHA = 'senha-apenas-e2e-2026'
$env:TRIBIA_PLAYWRIGHT_MODULE = '<pasta>\node_modules\playwright'
node scripts/etapa5-e2e.mjs     # 7/7 em 08/10/2026
node scripts/etapa4-e2e.mjs     # regressão 11/11
```

Atualizar a tabela NCM oficial: na pasta backend, `node ferramentas/atualizar_ncm.mjs` (rede; API pública, sem
autenticação).
Detalhes e auditoria S5: [ENTREGA-INTELIGENCIA-FISCAL-2026-10-08.md](ENTREGA-INTELIGENCIA-FISCAL-2026-10-08.md).

## Registro anterior — execução autônoma da Etapa 1

**120 testes locais direcionados aprovados** (104 + 16 não sobrepostos), 3 contratos RTC
offline reais e 10 fluxos E2E Chrome aprovados, zero pageerrors. **Suíte final: 220 testes,
201 passaram, 15 falhas fiscais, 0 erros, 4 ignorados**. Sem RTC: 198 passaram/7 ignorados.
Build/lint e diff --check verdes. As 15 são 13 conhecidas + CSV/cache-only antes mascaradas;
seis contratos/fallback corrigidos. Gabaritos fiscais não alterados. Experimento causal
isolado com base antiga passou os 15, mas não é resultado da suíte padrão nem validação fiscal.
Etapa 1 concluída com ressalvas no ambiente isolado; Etapa 2 não iniciada. Comandos, scripts,
inventário individual, capturas, fontes e limitações: [relatório](CONCLUSAO-ETAPA-1-2026-10-08.md).

## Registro anterior — B3-CACHE corrigido, Etapa 1

**63 testes direcionados passaram**, 0 falhas/erros/ignorados. Suíte completa (repetida):
**195 testes, 167 passaram, 21 falhas, 0 erros, 7 ignorados**. São 13 de base fiscal,
2 fallback e 6 contratos anteriores. Regressão de cache e fixture/contrato de IA agora verdes.
Build/lint e `git diff --check` passaram. E2E visual/contratos externos não executados;
Etapa 1 avançada, não concluída. Comandos, inventário, cobertura e limites:
[CORRECAO-B3-CACHE-2026-10-08.md](CORRECAO-B3-CACHE-2026-10-08.md).

## Registro anterior — diagnóstico da Etapa 1, 08/10/2026

Resultado ampliado: **178 testes, 148 passaram, 23 falhas, 0 erros, 7 ignorados**.
22 falhas anteriores classificadas + nova regressão B3-CACHE (justificativa privada global).
Os 36 casos anteriores + 5 novos HTTP reais passaram antes de acrescentar a regressão.
Build/lint verdes; navegador indisponível, E2E visual não executado.
Comandos, inventário individual, experimentos de base/fallback, limites e roteiro manual:
[VALIDACAO-ETAPA-1-2026-10-08.md](VALIDACAO-ETAPA-1-2026-10-08.md).
Registros abaixo mantêm histórico; não usar 172/22 ou 36 verdes para declarar isolamento integral.

Pré-requisitos: **JDK 21** (`java -version`), **Node.js** (o repositório tem `package-lock.json`). Maven não precisa
ser instalado (wrapper). Python só para as ferramentas em `backend/ferramentas/`.

> Nesta máquina o `java` **não está no PATH**; a execução dos testes nesta fase usou um JDK 21 extraído em pasta
> temporária via `JAVA_HOME`. Ajuste `JAVA_HOME` para o seu JDK.

## Instalar e configurar

```powershell
# raiz do repositório
cd frontend; npm install; cd ..

# variáveis (só na sessão do terminal; nunca coloque valores reais em arquivos versionados)
$env:JAVA_HOME = "C:\caminho\do\jdk-21"
$env:TRIBIA_ADMIN_SENHA = "defina-uma-senha"     # ou tribia.admin.senha em backend\application-local.properties (gitignored)
$env:GEMINI_API_KEY = "..."                     # opcional; sem ela: cache/regra, sem IA
```

O admin só é criado se não existir ADMIN no banco (`backend\data\`). Não apague esse banco
para testar/trocar senha: contém empresas, acessos e notas. Use H2 memória em ambiente isolado,
com comandos do relatório; recuperação de acesso real exige procedimento autorizado.

## Subir

```powershell
# janela 1 (opcional): calculadora oficial; sem ela o cálculo cai no simplificado (modo AUTO)
cd backend; .\ferramentas\iniciar-calculadora.bat

# janela 2: API em http://localhost:8090 (Swagger /swagger-ui.html, H2 /h2-console)
cd backend; .\mvnw.cmd spring-boot:run

# janela 3: front em http://localhost:5173
cd frontend; npm run dev
```

Profile de apresentação: `.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=demo"`.

## Testes

```powershell
cd backend
.\mvnw.cmd test                                   # suíte completa (H2 em memória, sem IA real)
.\mvnw.cmd test "-Dtest=AcessoEmpresasTest"       # uma classe
# prova de causa B6 (não é correção): 
.\mvnw.cmd test "-Dtribia.calculo.excluir-tributos-da-base=false" "-Dtribia.classificacao.fallback-regra=false" `
  "-Dtest=ApuracaoControllerTest,ClassificacaoIaControllerTest,DashboardControllerTest,RelatorioControllerTest,RevisaoControllerTest,RoteiroDemoTest"
```

- Testes de contrato que dependem de serviços externos se **pulam** sem eles: `GeminiContratoTest` (exige
  `GEMINI_API_KEY`, **gasta cota — não rodar sem autorização**), `CalculadoraOficialContratoTest` e
  `SimplificadaVsOficialContratoTest` (precisam da calculadora no ar).
- Padrão de autenticação nos testes de controller: `@WithUserDetails("admin@tribia.local")` na classe e
  `.with(csrf())` nas requisições POST/PUT/DELETE/multipart. Para outros usuários use
  `user(UsuarioLogado.de(u).semSenha())` (ver `AcessoEmpresasTest`).
- Frontend: `npm run build` (tipos + build) e `npm run lint` **passaram na P0.1**; sem edição de arquivos do front.
  E2E visual ainda não executado nesta sessão; não há suíte de frontend no repositório.

### Resultado real P0.1 — 08/10/2026

- Direcionados: **36 testes, 0 falhas, 0 erros, 0 ignorados — BUILD SUCCESS**.
  AcessoEmpresas 12, IsolamentoEmpresas 14, SeedRunner 4, SeedRunnerAutorizacao 2, ClienteController 4.
- Suíte completa final: **172 testes, 22 falhas, 0 erros, 7 ignorados — BUILD FAILURE**.
  Falhas: Apuracao 4, ClassificacaoIa 2, Dashboard 7, Nota 2, Relatorio 1, Revisao 3, RoteiroDemo 1, GeminiClient 2.
  São os 15 casos fiscais B6/S5 e 7 contratos B7 já documentados; B3 deixou de falhar.
  Não foram alteradas regras fiscais nem expectativas desses testes. Use o total do resumo Maven: o XML
  externo de RegrasApuracaoTest informa 0 e não representa os 19 testes das classes JUnit aninhadas.
- Frontend: `npm run build` e `npm run lint` — **exit 0**; `git diff --check` sem problemas.
- Rodadas intermediárias: 16 testes iniciais verdes; primeira rodada ampliada teve 1 falha no teste novo
  (esperava `itensAfetados`, contrato real é `itensAtualizados`), corrigida. Suíte antes do reforço H2:
  171 testes, mesmas 22 falhas, 0 erros, 7 ignorados.

Comandos efetivamente usados (backend; JDK 21 disponível via JAVA_HOME):

```powershell
$env:GEMINI_API_KEY = ''
.\mvnw.cmd -o test '-Dtest=AcessoEmpresasTest,IsolamentoEmpresasTest,SeedRunnerTest,SeedRunnerAutorizacaoTest,ClienteControllerTest' '-Dtribia.admin.senha=senha-apenas-dos-testes'
.\mvnw.cmd -o test '-Dtribia.admin.senha=senha-apenas-dos-testes'
```

Senha acima é exclusivamente sintética para os testes, nunca credencial local. Importação local desabilitada,
bancos H2 em memória com UUID, LLM simulado/desabilitado; nenhum contrato pago foi executado. Os 7 ignorados
incluem os contratos externos e geradores opt-in. Nenhum reset do banco real ou execução do ensaio.

Cobertura: duas empresas com produtos idênticos, itens pendentes e cálculos persistidos; acesso cruzado nos
dois sentidos em leitura, upload, classificação, cálculo, pagamento, revisão e CSV/JSON; chamadas diretas
aos serviços; lista/indicadores só da própria empresa; ADMIN preservado; ativo/inativo, reativação e usuário
removido. Negativas comparam todas as colunas de 8 tabelas e verificam zero chamadas LLM/calculadoras.
Operações autorizadas de lote/revisão e IA simulada verificam que registros da outra empresa não mudam.
Seed restaura contexto inclusive em falha simulada (log ERROR esperado). Console H2: filtro testado; MockMvc
não monta o servlet, então ADMIN chega ao 404 de recurso enquanto anônimo/EMPRESA recebem 401/403.

## Verificar autenticação na API (sem destruir dados)

```powershell
$s = New-Object Microsoft.PowerShell.Commands.WebRequestSession
Invoke-WebRequest http://localhost:8090/api/auth/csrf -WebSession $s -UseBasicParsing | Out-Null
$x = ($s.Cookies.GetCookies("http://localhost:8090") | ? Name -eq XSRF-TOKEN).Value
$h = @{ "X-XSRF-TOKEN" = $x }
Invoke-RestMethod http://localhost:8090/api/auth/login -Method Post -WebSession $s -Headers $h `
  -ContentType "application/json" -Body '{"email":"admin@tribia.local","senha":"<sua-senha>"}'
Invoke-RestMethod http://localhost:8090/api/clientes -WebSession $s
```

Sem login: `401`. POST sem `X-XSRF-TOKEN`: `403`. Para testar isolamento: crie um acesso de empresa
(`POST /api/clientes/{id}/usuarios`, admin), faça login com ele e tente dashboard de outra empresa: **404**
desde P0.1. Use empresas fictícias em ambiente isolado para não alterar dados reais.

## Logs e diagnóstico

- Log da API no terminal; senha gerada do admin aparece **uma vez** se `TRIBIA_ADMIN_SENHA` não estiver definida.
- Relatório dos testes: `backend\target\surefire-reports\`.
- B4 corrigido: lista traz `ativo`. Nota "Pendente" apesar de classificação persistida: B5 ainda aberto.
- IA retornando itens pendentes: ver aviso na resposta de `/classificar`; sem chave → `NAO_CONFIGURADO`;
  429/503 → espera e troca de modelo; 401/403 do Gemini → chave inválida.
- Porta 8080/8081/8082/80 ocupadas pela calculadora; a API usa 8090, o front 5173.
- Erros 403 após inatividade: sessão expirada (8 h) ou CSRF ausente; recarregue a página.
- Demo exige ADMIN; `ensaio-demo.ps1` não faz login nem CSRF (B9) e não foi executado.
  Console H2 também exige sessão ADMIN; não deve ser habilitado em produção.
