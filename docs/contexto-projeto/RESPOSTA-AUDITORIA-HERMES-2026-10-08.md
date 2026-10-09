# Resposta à auditoria do Hermes (TESTEJEV.md) — 08/10/2026, noite

Auditoria recebida pela responsável (resumo colado na conversa). Cada item foi conferido **executando código e testes**,
não só lendo. Branch `dev/prataliyann-hue`, sem commit desta etapa.

> O relatório que o Hermes diz ter entregue, `docs/contexto-projeto/AUDITORIA-TESTES-GEMINI-JEV.md`, **não existe no
> repositório**. Existe `docs/AUDITORIA_HERMES_INTELIGENCIA_FISCAL.md`, que é um guia de como auditar (não um relatório
> com resultados). O Hermes não executou testes (falha do Maven), então os achados vieram só de leitura.

## 1. Itens apontados pelo Hermes

| Item (severidade do Hermes) | Veredito | Evidência | O que foi feito |
|---|---|---|---|
| P1 — Falha do ambiente de testes (Maven wrapper) | **Procedente, com causa no projeto** | Não há JDK instalado na máquina; `iniciar-backend.ps1` e o guia apontavam para um JDK numa pasta temporária de sessão (`AppData\Local\Temp\...\scratchpad\jdk`), inacessível ou apagável para outra ferramenta | JDK Temurin 21 LTS copiado para `%USERPROFILE%\.jdks\temurin-21.0.12.1` (pasta padrão do usuário, sem administrador). Script detecta sozinho e ganhou `-Testes [-Filtro ...]`. Prova: `iniciar-backend.ps1 -Testes` sem `JAVA_HOME` → 317 testes, BUILD SUCCESS |
| P2 — Prompt injection em anexos | **Já resolvido para o Gemini; procedente em parte para a JEV** | `PesquisaNcmIa`: delimitador aleatório por pedido, `<`/`>` e controles neutralizados, trechos com cara de instrução → observação + revisão humana, saída limitada (`PesquisaNcmIaSegurancaTest`, 5; E2E `etapa6` passo 6). Mas o `state` enviado à JEV levava o texto cru | `JevHttp.estado`/`perguntas`: mesma neutralização + teto de 2.000 caracteres por campo. Teste `JevHttpTest.textoDoUsuarioNoStateENasPerguntasENeutralizadoELimitado` |
| P2 — Atualização da tabela NCM só manual | **Procedente quanto ao aviso; atualização automática recusada** | Antes, o alerta só aparecia dentro de cada análise depois de 120 dias | `VerificacaoTabelaNcm`: log na inicialização (INFO em dia / WARN vencida), `GET /api/admin/tabelas/ncm` (ADMIN) e card "Tabela NCM" em Configurações. **Decisão:** não baixar sozinha; trocar dados oficiais sem revisão e sem testes mudaria resultados sem ninguém ver. Testes `VerificacaoTabelaNcmTest` (2) e `JevControllerTest.situacaoDaTabelaNcmParaAdmin` |
| P2 — Política da pontuação JEV indefinida | **Não procedente (já definida e implementada)** | `ValidadorNcm.avaliacaoDaJev`: divergência ≥ 0,20 ou nota < 0,50 → ALERTA + revisão humana; concordância nunca confirma. `ValidadorNcmJevTest` (5), `ComJevDivergente`, E2E `etapa6` passo 5. Documentado em `docs/JEV-AI-INTEGRACAO.md` §2 | Nada a mudar; limites seguem configuráveis para calibração |

## 2. Problemas novos encontrados nesta conferência

| # | Severidade | Problema | Correção |
|---|---|---|---|
| N1 | **P1** | **Testes não eram herméticos:** o `application.properties` importa `../.env`, e o Maven roda na pasta `backend`, então os testes liam o `.env` da máquina. As chaves já eram anuladas pela configuração de teste, mas outras propriedades vazavam: o `tribia.jev.modelo=jev-latest` do `.env` quebrou `JevControllerTest`. `-Dspring.config.import=` (usado nas instruções antigas) **não** isola: confirmado na prática | Caminhos do `spring.config.import` viraram `${tribia.arquivo-local:...}`, `${tribia.arquivo-env-raiz:...}`, `${tribia.arquivo-env-backend:...}` (padrões iguais aos de antes) e o `maven-surefire-plugin` aponta para arquivos inexistentes. Prova: teste verde com o `.env` presente; backend normal continua lendo o `.env` (log "JEV AI ativa ... jev-latest", admin criado com a senha do `.env`) |
| N2 | **P1** | **Senha real do administrador local em arquivos que iriam para o Git:** `.env.example` (o `.gitignore` foi alterado para incluí-lo com `!.env.example`) e `docs/AUDITORIA_HERMES_INTELIGENCIA_FISCAL.md` | Substituída por texto de exemplo. Histórico do Git conferido (`git log -S`): a senha nunca foi commitada. Segue só no `.env` e no `application-local.properties`, ignorados |
| N3 | Informativo | O `.env` local está com `TRIBIA_JEV_MODO=HTTP` e chave definida: **todo backend iniciado com ele chama a JEV real (cobrada) em cada análise**. Também fixa `tribia.jev.modelo=jev-latest` (alias que muda sozinho; a documentação recomenda versão fixa) | Não alterado (configuração da responsável). `iniciar-backend.ps1` agora avisa em amarelo quando a JEV está em HTTP |
| N4 | P3 | Rotas erradas no guia do Hermes (`/empresa/1/fiscal`) | Corrigidas para `/dashboard/empresas/1/inteligencia-fiscal` |
| N5 | P3 | `TestEnv.java`/`TestEnv.class` soltos na raiz (diagnóstico do Hermes) | Não versionar; podem ser apagados |

## 3. Testes executados nesta etapa (sem chaves reais, sem chamadas pagas)

| Execução | Resultado |
|---|---|
| `iniciar-backend.ps1 -Testes` (suíte completa, JDK detectado, sem ler `.env`) | **317 testes, 0 falhas, 0 erros, 8 ignorados** (3 calculadora oficial, 2 Gemini real, 2 geradores, 1 contrato real da JEV) |
| `iniciar-backend.ps1 -Testes -Filtro "JevHttpTest,PesquisaNcmIaSegurancaTest,TesteConexaoJevTest"` | 24/24 |
| Front `npm run build` / `npm run lint` | verdes |
| E2E `frontend/scripts/etapa6-if-e2e.mjs` (dublês locais da IA, backend sem `.env`) | 7/7, 0 erros JavaScript |
| Backend real com `.env`, banco em memória, sem seed | sobe; JEV lida como ativa; verificação da NCM no log; nenhuma análise criada, nenhuma chamada externa |

Chamadas reais (Gemini/JEV): **zero**.

## 4. Como o Hermes deve rodar a próxima auditoria

```powershell
# na raiz do repositório
powershell -ExecutionPolicy Bypass -File .\iniciar-backend.ps1 -SkipRun   # confere JDK e .env (sem mostrar valores)
powershell -ExecutionPolicy Bypass -File .\iniciar-backend.ps1 -Testes    # suíte completa, hermética
```

E2E: ver cabeçalho de `frontend/scripts/etapa6-if-e2e.mjs` (use `--tribia.arquivo-env-raiz=x` etc. para o backend não
carregar o `.env`; **não** use `--spring.config.import=`).

## 5. Teste real da JEV (autorizado pela responsável)

`JevContratoTest`, uma execução: API real da TypeSafe aceitou o formato; modelo `jev-1.13.0`; 327 ms; 568 tokens de
entrada / 56 de saída; sabonete × 3401.11.90 = 0,83 e × 8517.13.00 (celular) = 0,01. Aprovado (1/1). Valida o contrato
técnico, não a correção fiscal.

## 6. Continua pendente (não depende de código)

- Confirmar que a JEV AI do projeto é o Jev da TypeSafe (o contrato real já funciona com a chave do `.env`).
- Hipótese S5 (PIS/Cofins na base de 2027): especialista.
- Calibrar os limites da JEV (0,50 / 0,20) com análises reais.
