# HANDOFF — TribIA: Contexto Completo do Projeto

> **Gerado em:** 08/10/2026  
> **Finalidade:** Passagem de contexto para outra IA/sessão continuar o desenvolvimento.  
> Leia este arquivo antes de qualquer outra coisa.

---

## Atualização mais recente — Etapa 2 concluída, Etapa 3 revisada, Etapa 4 iniciada (08/10/2026)

Responsável aprovou o fechamento da Etapa 2 e o início da Etapa 4 (ver `PLANO_MESTRE_TRIBIA.md`).

- **Decisão S5 aprovada:** base 2027 sem ICMS/PIS/Cofins. Os 15 valores esperados foram atualizados e
  conferidos à mão. **Backend: 226 testes / 0 falhas / 0 erros / 7 ignorados** (após R2), nas duas ordens.
  Novos resultados: Distribuidora +255,45%, Farmácia −32,63%, Loja −28,24% (antes −1,35%). Front: `npm run build`, `npm run lint` e `npm test` (8/8) verdes.
- **`.env` na raiz** (fora do Git) agora é lido pelo backend (`spring.config.import`); variável de ambiente
  tem prioridade; testes continuam com a chave vazia (conferido). O1 resolvido pelo responsável.
- **Ensaio da demo** (`backend/ferramentas/ensaio-demo.ps1`) entra como ADMIN com CSRF. Executado 3/3 verde
  contra API isolada (porta 8190, H2 em memória, senha sintética, sem IA real, modo SIMPLIFICADA).
  Números do ensaio (com a base atual): Distribuidora hoje R$ 264,58 → 2027 R$ 378,44, 9 pendentes.
- **Revisão da Etapa 3 (motor de alertas):** coerente com o backend (mesma lista de códigos por adquirente,
  mesma base, opções completas da tabela). Achados R1 e R2 (alertas de compra) em `PENDENCIAS.md`.
  Testes novos: `frontend/scripts/alertas.test.ts` (`npm test`, 10 testes, Node 22.6+, sem dependências).
- **R2 resolvido (padrão C):** `CalculoService` calcula também com o código destacado na nota quando a compra foi
  corrigida e usa o menor crédito (`tribia.calculo.credito-compra-divergente=MENOR|NOTA|REVISAO`); a revisão
  devolve aviso. **R1 resolvido no front:** compras viram "efeito no preço" (fora do total de oportunidades).
  Testes: `CreditoCompraDivergenteTest` (6). Regra a confirmar com especialista (ver PENDENCIAS, R2).
- **Ensaio real** (calculadora oficial + Gemini com a chave nova do `.env`): 1/1 verde, mesmos números do
  simplificado; IA 8/8 em 11,7 s; azeite ficou integral (trava V6 confirmada com o modelo real).
- **E2E Etapa 4** (`frontend/scripts/etapa4-e2e.mjs`, Playwright fora do repo + Chrome local): 11 fluxos, 0 erros
  JavaScript; capturas em `backend/target/etapa4-e2e`. Comando no cabeçalho do script.
- **Segurança do deploy:** `application-prod.properties` (ativado pelo Dockerfile): console H2, Swagger e
  `/api/demo` desligados (demo só com `TRIBIA_DEMO_HABILITADO=true`), cookie Secure + forward headers, senha do
  admin obrigatória (12+ caracteres, senão não sobe). `LimiteTentativasLogin`: 5 falhas no mesmo e-mail → 429 por
  15 min. Dockerfile roda como usuário sem privilégios. Testes: `PerfilProducaoTest`, `LimiteTentativasLoginTest`.
  Suíte: 233 / 0 falhas / 7 ignorados nas duas ordens. `docker build` NÃO executado (Docker Desktop parado).
- **Roteiro da apresentação:** `docs/ROTEIRO_DEMO.md` (preparação, passos com números, plano B, ressalvas).
- Próximo: `docker build` e deploy de demonstração; ensaio final com quem apresenta.

## Registro anterior — Etapa 3 iniciada: motor de alertas (branch `dev/nicolau`, 08/10/2026)

**Etapa 3 EM ANDAMENTO.** Motor de alertas no front, sobre dados e endpoints existentes; nenhuma regra
fiscal do backend mudou. Build/lint não verificados no ambiente de desenvolvimento (npm bloqueado);
checagem de tipos com stubs e teste do motor com a tabela oficial do repositório passaram.

- `lib/alertas.ts` (motor puro), `hooks/useAlertas.ts`, tela `/dashboard/empresas/:id/alertas` (menu
  "Alertas") e faixa "Alertas fiscais" no início da empresa.
- Alertas: benefício não aplicado (nota integral, lista oficial associa o NCM a redução/alíquota zero),
  benefício fora da lista do NCM, par CST/cClassTrib inválido (o backend descartou o código da nota),
  divergência confirmada na revisão, fornecedor do Simples (crédito limitado), nota sem grupo IBS/CBS e
  itens aguardando revisão. Códigos por adquirente (mesma lista do backend) não viram sugestão.
- "Valor em jogo" é estimativa: base sem ICMS/PIS/Cofins × alíquota de referência (mediana dos cálculos
  integrais da empresa ou CBS 9,43% + IBS 0,1%) × diferença de carga. A tela diz isso.
- Ação "Corrigir" aplica o código escolhido via `PUT /api/itens/{id}/classificacao` e recalcula; o
  alerta vira "divergência confirmada" (corrigir a nota na origem).
- Limitação conhecida: o backend aceita o cClassTrib do XML com confiança 1 sem conferir a lista do NCM;
  o alerta cobre isso na tela, mas o comparativo 2027 usa o código da nota até alguém corrigir.
- XMLs de demonstração fora do repositório: `xml-teste/demo_alertas_*.xml` (erros propositais).

## Registro anterior — Etapa 2 no front (branch `dev/nicolau`, 08/10/2026)

**Etapa 2 EM ANDAMENTO — integração front × back. Build/lint NÃO verificados no ambiente de
desenvolvimento (npm bloqueado); rodar `npm run build` e `npm run lint` antes do commit.**
Somente frontend alterado; nenhuma regra fiscal, teste, backend ou configuração de segurança mudou.

- Front passou a chamar endpoints que já existiam e não eram usados:
  `POST /api/notas/{id}/classificar` (calcula 2027 junto), `GET /api/clientes/{id}/dashboard`,
  `GET /api/clientes/{id}/revisao`, `GET /api/classificacoes/opcoes`, `PUT /api/itens/{id}/classificacao`.
- **B5 resolvido no front:** `Item` agora tipa `classificacao` e `calculo`; `itemClassificado`
  considera a classificação persistida (antes só o XML → "0,0% · 0 de 23").
- Upload: após importar, as notas são classificadas e calculadas em segundo plano (toast com resumo).
- Detalhe da nota: coluna "Classificação (reforma)" (CST · cClassTrib, regime, origem, confiança),
  colunas Hoje × 2027, KPIs PIS/Cofins × CBS/IBS/IS e botão "Processar nota".
- Início da empresa: card "Comparativo 2027" (dashboard do back), próximo passo "Processar N notas".
- Nova tela **Revisão** (`/dashboard/empresas/:id/revisao`): aceitar, corrigir (opções por NCM)
  e marcar uso e consumo; recalcula as notas afetadas.
- Inteligência Fiscal oculta do menu por padrão (endpoints não existem no back); reativar com
  `VITE_INTELIGENCIA_FISCAL=true`.
- Roteiro manual de 7 testes aprovado pelo Nicolau em 08/10 (XMLs fictícios 77101/1101).
- Relatório (back): `RelatorioService` passa a contar classificações persistidas (B6). Não compilado
  no ambiente de desenvolvimento; rodar `.\mvnw.cmd test`.
- Deploy de demonstração preparado: `frontend/vercel.json` (rewrite `/api` → backend, fallback SPA)
  e `backend/Dockerfile` (Render). Mesmo domínio para o navegador por causa do cookie de sessão/CSRF;
  no backend definir `TRIBIA_ADMIN_SENHA` (12+ caracteres). CORS não é necessário: o rewrite deixa tudo na mesma origem. H2 do
  container zera a cada deploy (seed recria as empresas fictícias); calculadora RTC não sobe lá
  (cálculo cai no modo simplificado, com aviso).

## Registro anterior — execução autônoma da Etapa 1 (08/10/2026)

**Etapa 1 CONCLUÍDA COM RESSALVAS no ambiente isolado/caminhos avaliados. Etapa 2 não iniciada.**
Não é liberação de produção: chave antiga sem revogação comprovada, histórico do cache não
auditado em dados reais e resultados fiscais ainda não certificados.

- Baseline reproduzido: 195 testes / 167 aprovados / 21 falhas / 0 erros / 7 ignorados.
- Seis contratos confirmados no código e corrigidos nos testes (dashboard, CSV, escopo de
  notas, retry Gemini). Assert numéricos antigos preservados em testes separados.
- Fallback: sem associação/ambíguo não inventa integral; Optional vazio mantém pendência.
  Sugestão única REGRA permanece 0,40/aceita=false, sem cache. 13 novos casos unitários.
- API e AppLayout avisam que comparativos são estimativas, não apuração definitiva.
- 120 testes locais direcionados verdes em seleções não sobrepostas (104 + 16).
- E2E Chrome/Playwright isolado: 10 fluxos, zero pageerrors, três perfis, login/logout,
  seleção/lista, XML válido/inválido, notas, acesso cruzado, desativação e reativação.
  Harness novo `frontend/scripts/etapa1-e2e.mjs`; capturas em backend/target/etapa1-e2e.
- Calculadora oficial **real offline**: 3 contratos aprovados; distribuição pública app
  1.5.4-082a5001 / base V0059 (30/09/2026), só em pasta temporária e loopback. Sem custo/API paga.
- **Suíte final padrão com RTC: 220 testes, 201 aprovados, 15 falhas fiscais, 0 erros, 4 ignorados.**
  Sem RTC: 198 aprovados e 7 ignorados. Os 15 são 13 conhecidos + CSV/cache-only antes
  mascarados; auditoria individual no relatório. Experimento causal em H2 com base antiga
  passa os 15; não é solução, não altera configuração fiscal padrão nem aprova gabarito.
- Build/lint e diff --check verdes. IA real/geradores não executados. Sem commits/pushes,
  regras fiscais, dados reais, limpeza de cache/banco ou alterações locais sobrescritas.
- README corrigido pontualmente (persistência, riscos, escopo de demonstração).

Fontes oficiais consultadas, limitações, arquivos, comandos e plano de revisão histórica:
`docs/contexto-projeto/CONCLUSAO-ETAPA-1-2026-10-08.md`. O1 exige confirmação pelo responsável
sem registrar chave. Próximo: aprovação para Etapa 2/B5 em ambiente isolado; uso real exige
resolver O1/histórico e validar resultados. Servidores temporários encerrados, artefatos preservados.

## Registro anterior — B3-CACHE corrigido (08/10/2026)

Continuamos na **Etapa 1, avançada e não concluída**; Etapa 2 não iniciada.
Após autorização para o próximo passo, IA/correções/aceites usam chave privada da empresa
da nota/item autorizado. Catálogo público somente SEED curado; legado sem dono IA/MANUAL/
desconhecido ignorado e preservado. Namespace + SHA-256 na coluna existente; nenhuma migração
destrutiva, alteração de schema, regra fiscal, dados reais, chamada paga ou commit/push.

- **63 testes direcionados passaram**, incluindo duas empresas, negativas sem efeitos,
  cache/aceite privados, legado preservado e cinco casos HTTP reais com sessão/CSRF.
- **Suíte completa: 195 testes, 167 passaram, 21 falhas, 0 erros, 7 ignorados**.
  B3-CACHE verde e uma das 22 falhas históricas resolvida: XML da fixture de cache corrigido
  (nNF/cDV) e contrato ajustado a reuso somente na própria empresa. Valores fiscais intactos.
- Remanescentes: 13 de base fiscal, 2 fallback, 6 contratos. Build/lint passaram;
  E2E visual e contratos externos pendentes. Sem regressões novas identificadas na suíte.
- Histórico possivelmente copiado pelo cache antigo não foi limpo nem inferido: antes de uso
  real exige avaliação autorizada e eventual plano auditável. Binário antigo reintroduz o risco.
- Próximo incremento recomendado: contratos remanescentes e validação visual, ainda Etapa 1;
  validar S5/fallback antes de mudar expectativas. Não avançar à 2 automaticamente.

Arquivos, impactos, comandos, resultados e limites em
`docs/contexto-projeto/CORRECAO-B3-CACHE-2026-10-08.md`. `PLANO_MESTRE_TRIBIA.md` e contexto atualizados.

## Registro anterior — plano mestre e validação da Etapa 1 (08/10/2026)

Leia primeiro `PLANO_MESTRE_TRIBIA.md` e `AGENTS.md`; os registros abaixo preservam histórico.
Há exatamente quatro etapas principais. **Etapa 1 bloqueada; Etapa 2 não iniciada.**

- Reproduzidos 172 testes / 22 falhas / 0 erros / 7 ignorados antes dos testes novos.
- Criado `FluxoHttpEtapa1Test`: cinco casos com Tomcat real, porta aleatória/loopback,
  H2 exclusivo, login BCrypt, cookies/CSRF reais, duas empresas, upload e servlet H2.
  Os 36 testes anteriores + 5 HTTP passaram (41/41) antes da nova regressão de cache.
- Novo teste `IsolamentoEmpresasTest.justificativaManualPrivadaNaoDeveVazarPeloCacheGlobal`
  **falha legitimamente**: revisão MANUAL da empresa A grava texto no cache global;
  classificação autorizada da nota B copia esse texto e a validação aceita=true.
  B3 corrigiu guardas diretas, mas isolamento integral não está encerrado (**B3-CACHE**).
- Suíte ampliada: **178 testes, 148 passaram, 23 falharam, 0 erros, 7 ignorados**.
  São 22 falhas anteriores + 1 defeito antes não coberto. Frontend build/lint passaram.
- As 22 anteriores: 13 divergências de base fiscal, 2 de fallback por regra e 7
  contratos/fixtures. Experimentos de flags somente na linha de comando confirmam causas;
  nenhuma expectativa fiscal foi alterada. Veja inventário individual no relatório.
- Navegador conectado indisponível: nenhum E2E visual alegado; roteiro manual isolado registrado.
- Neste ciclo, apenas testes e documentação alterados. Produção P0.1 local preservada;
  sem commit/push, IA paga, segredo exposto, banco real ou regras fiscais alterados.

Relatório reproduzível: `docs/contexto-projeto/VALIDACAO-ETAPA-1-2026-10-08.md`.
Próximo: aprovação para cache privado por empresa / catálogo público curado; avaliar legado
sem proprietário sem apagar nem atribuir dados arbitrariamente. Não avançar à Etapa 2.

## 1. Visão Geral do Produto

**TribIA** é uma aplicação web para escritórios de contabilidade e empresas gerenciarem o impacto da **Reforma Tributária brasileira (LC 214/2025)** em seus produtos.

**Fluxo principal:**
1. O usuário faz upload de XMLs de Notas Fiscais Eletrônicas (NF-e)
2. A IA (Google Gemini) classifica cada item nas regras tributárias de 2027 (CST + cClassTrib)
3. O sistema calcula o imposto atual (PIS/Cofins) e o de 2027 (CBS/IBS/IS)
4. O painel exibe a comparação e o relatório pode ser exportado como CSV

**Repositório:** `https://github.com/leozinlsb/trib-ai`  
**Branch principal:** `main`

---

## 2. Stack Tecnológica

### Backend (`/backend`)
| Componente | Tecnologia |
|---|---|
| Linguagem | Java 21 |
| Framework | Spring Boot 3 |
| Segurança | Spring Security (sessão em cookie HttpOnly + CSRF) |
| Banco de dados | H2 (arquivo em `backend/data/tribia`) |
| IA | Google Gemini API (`gemini-3.5-flash`) |
| Build | Maven Wrapper (`mvnw.cmd`) |
| API Docs | Springdoc / Swagger UI |

### Frontend (`/frontend`)
| Componente | Tecnologia |
|---|---|
| Framework | React 19 + TypeScript |
| Build tool | Vite |
| Estilos | CSS puro (sem Tailwind) — tokens em `src/styles/` |
| Comunicação | `fetch` nativo com suporte a CSRF (header `X-XSRF-TOKEN`) |

---

## 3. Histórico Relevante desta Sessão de Desenvolvimento

### O que foi feito (em ordem cronológica):

1. **O backend já estava desenvolvido (`main`)** com as etapas 1 a 8:
   - Upload e parsing de XMLs de NF-e
   - Classificação por IA (Gemini)
   - Cálculo PIS/Cofins atual vs CBS/IBS/IS 2027
   - Revisão manual de classificações
   - Painel de apuração + exportação CSV
   - Profile `demo` com respostas gravadas da IA

2. **O frontend chegou via Pull Request** (branch `dev/prataliyann-hue`) e foi **mergeado no `main`** nesta sessão.

3. **Conflitos de merge resolvidos manualmente** nos arquivos:
   - `Cliente.java` — manteve lógica local + adicionou campo `ativo` do PR
   - `ClienteDto.java` — idem
   - `ClienteController.java` — manteve lógica local + adicionou endpoints de usuários/acessos do PR
   - `NotaController.java` — idem
   - `RelatorioController.java` — manteve lógica local (o PR tinha versão mais simples)
   - `application.properties` — manteve configuração local + adicionou propriedades de segurança do PR

4. **Spring Security foi adicionado pelo PR** à dependência do projeto. O backend agora exige autenticação em todas as rotas `/api/**`.

5. **Testes de compilação corrigidos** após o merge.

6. **Commit do merge** realizado e push para `origin/main` feito com sucesso.

---

## 4. Estado Atual (08/10/2026)

### Funcionando
- Backend compilando e iniciando sem erros
- Frontend com dependências instaladas (`npm install` executado)
- Integração: Vite (`/api`) repassa chamadas para `localhost:8090`
- Spring Security ativo: login obrigatório em `/api/**`
- H2 Database em arquivo: dados persistem entre reinícios do backend

### Atualização P0.1 autorizada — 08/10/2026

B3/B4 implementados nesta sessão, sem commit/push e preservando as alterações locais anteriores.
Autorização central em `AcessoService` resolve a empresa da nota e do item pelo banco. Serviços de clientes,
notas, classificação, cálculo, revisão, painel, relatórios e usuários verificam acesso antes de consultar dados
ou alterar registros. Listas são filtradas antes da agregação; lote verifica empresa e cada nota; revisão de
idênticos continua limitada à mesma empresa. Classificação e cálculo revalidam nas fases de leitura, chamada
externa e gravação. Na P0.1 inicial o cache global foi preservado; no incremento B3-CACHE passou
a privado por empresa (IA/revisão/aceite), com catálogo público somente SEED e legado inseguro ignorado.

Dependências/impactos validados antes da alteração: (1) política central e principal persistido; (2) lista/DTO
compatíveis com os tipos e filtros do frontend; (3) operações por nota antes de IA/calculadoras; (4) revisão,
consultas agregadas, CSV/JSON e lote; (5) demo e inicialização sem usuário HTTP; (6) regressão e memória persistente.
O seed precisa de ADMIN: inicialização usa administrador persistido, principal sem senha, contexto temporário
restaurado em `finally`, sem sessão HTTP nem liberação para chamadas anônimas.

`/api/demo/status` é checklist global e `/reiniciar` apaga dados de demonstração e recarrega seed; não há consumidor
no frontend. Ambos exigem ADMIN, além da propriedade de habilitação existente; o script de ensaio ainda precisa
de login ADMIN + CSRF e NÃO foi executado. Console H2 antes público também foi limitado a ADMIN para não permitir
contornar a API pelo banco. Sessão, login/logout, política de CSRF e exceção preexistente do console preservados.
Nenhuma regra fiscal, migração, dado real ou arquivo do frontend foi alterado.

Verificação: 36 testes direcionados passaram (AcessoEmpresas 12, IsolamentoEmpresas 14, SeedRunner 4,
SeedRunnerAutorizacao 2, ClienteController 4). Duas empresas, tentativas cruzadas nos dois sentidos, chamadas
diretas aos serviços, ativo/inativo, usuário removido, exportações, lote, revisão de idênticos e demo negada.
Comparações de todas as colunas de 8 tabelas antes/depois e mocks/spies confirmam ausência de mudanças e de
chamadas a IA/calculadoras nas negativas. IA positiva simulada e cálculo simplificado preservam a outra empresa.
Seed restaurado mesmo em falha simulada. O teste do console verifica o filtro (MockMvc não monta o servlet H2).

Suíte completa final: **172 testes, 22 falhas B6/B7, 0 erros, 7 ignorados**; não foi declarada verde.
Build e lint do frontend passaram. E2E visual de lista/upload não executado nesta sessão; contrato `ativo`
testado na API e frontend compilado. Banco de teste H2 em memória; configuração local não carregada; chave
Gemini vazia; nenhuma chamada paga. Comandos e resultado final da suíte em `docs/contexto-projeto/07-TESTES-EXECUCAO.md`.

### Testes do backend — histórico anterior à P0.1 (auditoria de estabilização)

**156 testes: 23 falhas, 0 erros, 7 ignorados** (antes: 40 falhas + 1 erro). Os 401/403 foram resolvidos só nos
testes, com `@WithUserDetails("admin@tribia.local")` na classe e `.with(csrf())` nas requisições POST/PUT/multipart
(mesmo padrão de `NotaControllerTest`). `@WithMockUser` **não serve**: o `AcessoService` espera um `UsuarioLogado`
de verdade. Nenhuma das 23 falhas restantes é de autenticação — o detalhe, com a causa comprovada de cada uma,
está em `PENDENCIAS.md` › "Testes do backend". Resumo:

- 15 dependem de duas regras fiscais ligadas em 8ba383d (base de 2027 sem ICMS/PIS/Cofins e sugestão por regra
  sem IA). Provado rodando com `-Dtribia.calculo.excluir-tributos-da-base=false
  -Dtribia.classificacao.fallback-regra=false`: as 15 passam. Não atualizar os números esperados antes de validar
  a regra (PENDENCIAS S5).
- 7 são testes desatualizados por mudanças intencionais de 8ba383d (CSV com coluna "Operação", `sujeitoIs` fora de
  `porRegime`, validação da chave de acesso, devoluções aceitas, nova tentativa no mesmo modelo do Gemini).
- 1 é bug real de segurança (B3, abaixo).

### Histórico do problema de isolamento (B3) — corrigido na P0.1 acima

Usuário de uma empresa consegue ler e alterar dados de outras. Comprovado com `curl` e pelo teste
`AcessoEmpresasTest.empresaSoEnxergaAPropria`. Rotas sem `AcessoService`: tudo em `ApuracaoController` e
`RevisaoController`, `/api/clientes/{id}/dashboard`, `GET /api/clientes` (lista todas), `/api/notas/{id}/resumo`,
os dois CSVs e `/api/demo/*`. Essas evidências são anteriores à correção autorizada; testes atuais de isolamento verdes.

### Front x back

- B4 corrigido: `GET /api/clientes` devolve `ativo` booleano e somente empresas visíveis; tipo já existia no front.
- O front ainda não usa classificar/calcular, revisão, painel 2027, resumo da nota nem os CSVs (lista em
  PENDENCIAS T19). Integrar **depois** de B3.

### Funcionalidade Parcial: Inteligência Fiscal

O frontend tem telas de **Inteligência Fiscal** completas, mas **os endpoints do backend não existem ainda**. As telas mostram "Análise fiscal ainda não disponível". O contrato proposto está em:
- `frontend/docs/inteligencia-fiscal-api.md`
- `frontend/src/api/inteligenciaFiscal.ts`

---

## 5. Como Inicializar o Ambiente

### Backend (novo terminal PowerShell, na pasta do repositório)
```powershell
cd backend
$env:TRIBIA_ADMIN_SENHA="defina-uma-senha"   # ou tribia.admin.senha em backend/application-local.properties (fora do Git)
$env:GEMINI_API_KEY="sua-chave-aqui"          # opcional; sem chave, a classificação usa cache/regra
.\mvnw.cmd spring-boot:run
```

O administrador só é criado quando ainda não existe no banco (`backend/data/`, fora do Git). Mudar a senha
depois não tem efeito sobre um banco já criado.

### Frontend (novo terminal PowerShell, na pasta do repositório)
```powershell
cd frontend
npm run dev
```

### URLs de Acesso
- Frontend: http://localhost:5173
- Backend API: http://localhost:8090
- Swagger: http://localhost:8090/swagger-ui.html
- H2 Console: http://localhost:8090/h2-console (JDBC: `jdbc:h2:file:./data/tribia`, user: `sa`, senha: vazia)

### Login Inicial
- **Email:** `admin@tribia.local`
- **Senha:** a definida em `TRIBIA_ADMIN_SENHA` / `application-local.properties` quando o banco foi criado

---

## 6. Próximas Tarefas (em ordem de prioridade)

1. **[ETAPA 1] Estabilizar seis contratos remanescentes** — confirmar devoluções, retry Gemini,
   CSV e `sujeitoIs` antes de atualizar testes. B3/B4/cache privado corrigidos nos caminhos testados.
2. **[ETAPA 1] Concluir validação visual isolada e avaliar histórico antes de uso real** —
   não limpar automaticamente classificações potencialmente copiadas pelo cache antigo.
3. **[ALTA / ETAPA 1] Validar a regra da base de 2027 (S5)** com fonte legal/profissional antes
   de mudar os 13 testes numéricos; decidir separadamente os 2 testes de fallback por regra.
4. **[ETAPA 2, NÃO AUTORIZADA AINDA] Integrar o front às rotas existentes** (T19 e B5),
   somente após concluir Etapa 1 e nova aprovação.
5. **[MÉDIA] Testar fluxo completo** — Login → criar empresa → upload XML → classificação → painel → CSV.
6. **[DEPOIS] Inteligência Fiscal** — fora desta fase. Contrato em `frontend/docs/inteligencia-fiscal-api.md`.
7. **[BAIXA] Revogar chave Gemini exposta** — Ver `PENDENCIAS.md` item O1.

Feito: `backend/application-local.properties` (fora do Git) e `backend/data/` no `.gitignore`.

---

## 7. Notas de Domínio

- **CST** = Código de Situação Tributária (tipo de tributação do item)
- **cClassTrib** = Código de Classificação Tributária (regime do item em 2027)
- **CBS** = Contribuição sobre Bens e Serviços (substitui PIS/Cofins em 2027)
- **IBS** = Imposto sobre Bens e Serviços (substitui ICMS/ISS em 2027)
- **IS** = Imposto Seletivo (tributação extra sobre produtos prejudiciais)
- **Seed** = dados de demonstração carregados na inicialização (3 empresas com notas reais)
- **Profile `demo`** = ativa respostas gravadas da IA para apresentações offline

---

*Fim do HANDOFF. Em caso de dúvida sobre decisões técnicas, consulte o `git log` e o `PENDENCIAS.md`.*
