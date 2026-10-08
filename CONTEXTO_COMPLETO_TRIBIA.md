# TribIA — Contexto completo

> Documento central de continuidade (08/10/2026). Detalhes em `docs/contexto-projeto/`. Em conflito entre documentos,
> prevalece o código; divergências estão em [06](docs/contexto-projeto/06-BUGS-PENDENCIAS.md).

Estratégia vigente: [PLANO_MESTRE_TRIBIA.md](PLANO_MESTRE_TRIBIA.md), exatamente quatro etapas.
**Etapa 1 CONCLUÍDA COM RESSALVAS no ambiente isolado/caminhos avaliados:** B3/B4/cache
privado verdes; seis contratos estabilizados e fallback sem evidência/ambíguo mantém pendência.
120 testes locais direcionados, 3 contratos RTC real offline e 10 fluxos Chrome aprovados.
Suíte final: 220 testes, 201 passaram, 15 falhas fiscais preservadas, 0 erros, 4 ignorados.
Build/lint verdes. Etapa 2 não iniciada. Antes de uso real: confirmar revogação da chave,
avaliar histórico do cache e validar resultados tributários. Avisos de estimativa na UI/API.
[Execução, auditoria e limites](docs/contexto-projeto/CONCLUSAO-ETAPA-1-2026-10-08.md).

## Documentos detalhados

| # | Arquivo | Conteúdo |
|---|---|---|
| 00 | [LEIA-PRIMEIRO](docs/contexto-projeto/00-LEIA-PRIMEIRO.md) | ordem de leitura e regras |
| 01 | [VISAO-PRODUTO](docs/contexto-projeto/01-VISAO-PRODUTO.md) | produto, planejado x implementado x testado |
| 02 | [ARQUITETURA](docs/contexto-projeto/02-ARQUITETURA.md) | backend, frontend, segurança, persistência |
| 03 | [FRONTEND-BACKEND](docs/contexto-projeto/03-FRONTEND-BACKEND.md) | endpoints usados/não usados, contratos |
| 04 | [INTELIGENCIA-FISCAL](docs/contexto-projeto/04-INTELIGENCIA-FISCAL.md) | classificação, cálculo, IA, validações |
| 05 | [HISTORICO-DECISOES](docs/contexto-projeto/05-HISTORICO-DECISOES.md) | decisões e mudanças |
| 06 | [BUGS-PENDENCIAS](docs/contexto-projeto/06-BUGS-PENDENCIAS.md) | problemas com evidência |
| 07 | [TESTES-EXECUCAO](docs/contexto-projeto/07-TESTES-EXECUCAO.md) | comandos PowerShell e diagnóstico |
| 08 | [ESTADO-ATUAL](docs/contexto-projeto/08-ESTADO-ATUAL.md) | fotografia do repositório |
| 09 | [ROADMAP](docs/contexto-projeto/09-ROADMAP.md) | P0–P3 com critérios |

Documentos anteriores mantidos: [HANDOFF.md](HANDOFF.md), [PENDENCIAS.md](PENDENCIAS.md) (atualizados em
08/10/2026), [README.md](README.md) (parcialmente desatualizado), [frontend/README.md](frontend/README.md),
[frontend/docs/inteligencia-fiscal-api.md](frontend/docs/inteligencia-fiscal-api.md) e os seis arquivos do Hermes
(`AUDITORIA_*`, `MAPA_FUNCIONAL`, `INTEGRACAO_FRONT_BACK`, `FLUXO_FISCAL`, `PLANO_CORRECOES`), com erros conhecidos.

## O que estamos construindo e por quê

Aplicação para escritórios de contabilidade e suas empresas verem o efeito da reforma tributária: sobem as NF-e em
XML, cada item recebe **CST + cClassTrib** (não NCM) por XML → cache → Gemini → regra, itens incertos vão para
revisão humana, e o sistema compara PIS/Cofins de hoje com CBS/IBS/IS de 2027. Origem acadêmica/hackathon.

## Como funciona (resumo)

- **Backend** (`backend/`, Java 21, Spring Boot, H2 em arquivo): sessão em cookie + CSRF, papéis ADMIN/EMPRESA,
  `AcessoService` para isolar empresas; parser de NF-e; classificação; cálculo (calculadora RTC oficial ou
  simplificada); revisão; painel; relatório; CSV; profile `demo` com respostas gravadas da IA.
- **Frontend** (`frontend/`, React 19 + TS + Vite): landing, login, multiempresas, documentos, relatório da
  empresa, telas de Inteligência Fiscal (sem backend). Proxy `/api` → `:8090`.

## Já desenvolvido

Backend etapas 1–8 (leozinlsb); login/multiempresas/relatório por empresa e todo o frontend (pratalidev); mudanças
grandes em 8ba383d (H2 em arquivo, base 2027 sem tributos, devoluções, fallback por regra, retry Gemini).

## Decisões que não devem ser esquecidas

1. Classificador devolve CST+cClassTrib; **IA só escolhe de lista fechada** e benefício de anexo exige NCM oficial.
2. **Sem dados fictícios** apresentados como reais (Inteligência Fiscal só com backend real; rota de exemplo só em DEV).
3. Sem cadastro público; "remover" empresa = desativar.
4. Segredos fora do Git (`GEMINI_API_KEY`, `TRIBIA_ADMIN_SENHA`/`application-local.properties`).
5. Segurança não se enfraquece para passar teste; autenticação em teste = `@WithUserDetails` + `.with(csrf())`.
6. Regra fiscal só muda com validação; simplificações assumidas estão em `PENDENCIAS.md` (S1–S10) e devem ser ditas.
7. Interface sem jargão técnico para o usuário final.

## Problemas atuais (resumo; ver 06)

| ID | Problema |
|---|---|
| B3 | Guardas diretas e cache privado corrigidos nos caminhos testados; histórico eventualmente contaminado exige avaliação autorizada |
| B4 | **Corrigido e validado**: ativo/inativo, upload e reativação por HTTP e E2E Chrome |
| B5 | Front ignora `classificacao`/`calculo` do backend |
| B6 | 15 divergências S5: 13 conhecidas + CSV/cache-only antes mascaradas; fallback corrigido, expectativas fiscais preservadas |
| B7 | Seis contratos estabilizados, verdes; diagnóstico/decisões documentados |
| B8–B13 | README e ignore corrigidos, não commitados; ensaio demo sem autenticação e revogação da chave pendentes |

120 testes locais direcionados e 3 contratos RTC offline passaram; 10 fluxos E2E Chrome,
zero pageerrors em três perfis. Suíte final: **220 testes, 201 passaram, 15 falhas fiscais,
0 erros, 4 ignorados** (sem RTC: 198 passaram/7 ignorados). Build/lint verdes. Relatório
e comandos em [07](docs/contexto-projeto/07-TESTES-EXECUCAO.md).

## O que fazer agora

1. Antes de uso real: responsável confirma revogação da chave; autoriza triagem histórica do cache em cópia isolada.
2. Validar S5/exceções das tabelas na Etapa 3 com fundamentação/especialista; preservar avisos e gabaritos até decisão.
3. Só após conclusão e aprovação: Etapa 2, B5 e fluxo existente classificar→revisar→calcular→painel→CSV.
4. Etapas 3/4 seguem o plano mestre; não implementar Inteligência Fiscal sem necessidade/contrato/validação.

## Como continuar sem comprometer o trabalho anterior

- Estado do Git: branch `dev/prataliyann-hue`, HEAD `ca65483`; 9 arquivos já estavam modificados antes desta
  etapa e foram preservados. Agora também há correções P0.1 em produção, teste do seed e 2 novos arquivos de
  testes, além das atualizações pontuais de contexto. Revise `git diff`; **sem commit/push automático**.
- Não apague `backend/data/` sem avisar (contém empresas/usuários locais). Não reproduza segredos.
- Rode `mvnw test`: referência atual 220 testes / 15 falhas fiscais / 0 erros / 4 ignorados com RTC; 7 sem RTC.
- Execução, comandos e diagnóstico: [07](docs/contexto-projeto/07-TESTES-EXECUCAO.md).

## Lacunas de informação

Histórico integral das conversas anteriores; significado do JEV; Gemini real, revogação da chave,
histórico de dados reais, ensaio-demo.ps1 e atualidade dos CSVs embutidos. E2E Chrome/RTC offline
foram executados; não extrapolar para todas as telas/versões/operações tributárias.
