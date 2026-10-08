# 08 — Estado exato do projeto (08/10/2026)

## Atualização após P0.1 autorizada

### Evidência mais recente: execução autônoma da Etapa 1

**CONCLUÍDA COM RESSALVAS no ambiente isolado/caminhos avaliados, não produção/fiscal.**
120 direcionados locais, 3 contratos reais RTC offline e 10 fluxos Chrome aprovados. Suíte
final: 220 testes / 201 passaram / 15 falhas fiscais preservadas / 0 erros / 4 ignorados.
Build/lint/diff --check verdes; seis contratos corrigidos; fallback não inventa integral
ou escolhe código ambíguo. UI/API alertam estimativa. Fontes oficiais e auditoria individual
registradas, sem alteração de regra/tabela/alíquota/gabarito. Etapa 2 não iniciada.
Antes de uso real: responsável confirma revogação da chave e autoriza triagem histórica do
cache; validar resultados fiscais. Gemini real/geradores não executados. Git permanece
dev/prataliyann-hue / ca65483, sem commit/push ou dados reais alterados. Ambiente temporário
encerrado; artefatos preservados. [Relatório atual](CONCLUSAO-ETAPA-1-2026-10-08.md).

### Registro anterior: B3-CACHE corrigido

Autorizado o próximo incremento ainda na Etapa 1: cache IA/revisão/aceite privado por empresa,
catálogo público somente SEED; legado inseguro sem dono ignorado/preservado. Namespace na
chave existente, sem alteração de schema. **63 testes direcionados verdes**; suíte completa
**195 testes, 167 passaram, 21 falhas, 0 erros, 7 ignorados**. Build/lint verdes. Fixture e
contrato de cache global IA corrigidos sem modificar valores fiscais. E2E visual pendente;
histórico possivelmente copiado requer avaliação autorizada. Etapa 1 avançada, não concluída;
Etapa 2 não iniciada. Próximo: estabilizar contratos, validar visualmente e decidir S5/fallback.
Git permanece `dev/prataliyann-hue` / `ca65483`, sem commit/push e sem tocar dados reais.
[Relatório atual](CORRECAO-B3-CACHE-2026-10-08.md).

### Registro anterior: diagnóstico da Etapa 1

O plano mestre de quatro etapas substitui planos legados como referência estratégica.
**Etapa 1 bloqueada; Etapa 2 não iniciada.** B4 e guardas diretas confirmados nos caminhos
testados; 41 testes direcionados passaram, incluindo 5 HTTP reais com Tomcat/H2 exclusivos.
Novo teste reproduz B3-CACHE: texto MANUAL privado e aceita=true copiados para outra empresa.
Suíte ampliada: 178 testes, 148 passaram, 23 falhas (22 anteriores + cache), 0 erros, 7 ignorados.
Frontend build/lint verdes. Sem navegador conectado, E2E visual pendente com roteiro manual.
Criados plano mestre, AGENTS, relatório de validação e teste HTTP; ampliado teste de isolamento.
Nenhum código de produção alterado neste ciclo, nem regras fiscais, dados reais ou commit/push.
Próximo: aprovação para separar cache privado por empresa de catálogo público curado;
tratamento conservador do legado sem proprietário. Ver [relatório](VALIDACAO-ETAPA-1-2026-10-08.md).

### Registro anterior da implementação P0.1 (histórico)

B3/B4 implementados nos serviços, consultas, exportações e lote; demo/seed ADMIN; console H2 ADMIN;
lista inclui `ativo`. Seed inicial usa contexto temporário de ADMIN persistido e o restaura inclusive na falha.
Arquivos detalhados em [09](09-ROADMAP.md); decisões em `HANDOFF.md`; testes em [07](07-TESTES-EXECUCAO.md).
36 testes direcionados verdes; suíte final 172 testes, 22 falhas B6/B7, 0 erros, 7 ignorados. Build/lint passam.
E2E visual e contratos externos não executados. Sem dados reais, IA paga, regras fiscais ou migrações alterados.
Git continua na branch `dev/prataliyann-hue`, HEAD `ca65483`, sem commit/push. Alterações locais anteriores
preservadas; agora também há mudanças de produção P0.1 e dois novos arquivos de testes.

Próximo passo: revisar diff/E2E visual em ambiente de teste; depois P1.1, validar S5/fallback antes de atualizar
expectativas fiscais. B5/integracão e ensaio autenticado continuam pendentes. Desabilitar H2/demo em produção.

## Fotografia histórica anterior à P0.1 (não representa o estado atual)

## Git

- **Branch atual:** `dev/prataliyann-hue` (main de referência: `main`). Remoto: `https://github.com/leozinlsb/trib-ai`.
- **Último commit:** `ca65483` "docs: adiciona HANDOFF.md com contexto completo para passagem de sessao" (leozinlsb).
- **Nada foi commitado nesta fase de estabilização/documentação.**

### Modificados, não commitados

| Arquivo | Mudança |
|---|---|
| `.gitignore` | + `backend/data/` |
| `HANDOFF.md`, `PENDENCIAS.md` | atualizados com evidências (testes, B3–B5, T19, S5/S10 etc.) |
| `backend/src/test/java/br/com/tribia/controller/{Apuracao,ClassificacaoIa,Dashboard,Relatorio,Revisao}ControllerTest.java` e `demo/RoteiroDemoTest.java` | só `@WithUserDetails("admin@tribia.local")` + `.with(csrf())` (+ reordenação de imports estáticos). **Nenhum código de produção alterado.** |

### Não rastreados

`AUDITORIA_INICIAL.md`, `AUDITORIA_TRIBIA.md`, `FLUXO_FISCAL.md`, `INTEGRACAO_FRONT_BACK.md`, `MAPA_FUNCIONAL.md`,
`PLANO_CORRECOES.md` (documentos do agente Hermes; contêm erros, ver [06](06-BUGS-PENDENCIAS.md)), e esta pasta
`docs/` + `CONTEXTO_COMPLETO_TRIBIA.md`. `backend/data/` existe localmente (banco; agora ignorado).
`backend/application-local.properties` existe localmente e é ignorado pelo Git (conteúdo não reproduzido aqui).

## O que foi feito mais recentemente

1. Merge do trabalho do colega (8ba383d + merge a556671); leitura de HANDOFF/PENDENCIAS.
2. Auditoria de estabilização: execução dos testes (156: 40 falhas + 1 erro → **23 falhas, 0 erros**), prova de causa
   das falhas, comprovação do vazamento B3 e do bug B4.
3. Documentação (esta pasta).

## Pendente agora

- **Aprovação da autora** para corrigir B3/B4 (mudança de comportamento de segurança; ver [09](09-ROADMAP.md) P0).
- Decisão fiscal sobre a base 2027 (S5) para liberar a atualização dos testes (B6).
- Confirmar com o autor do backend as mudanças intencionais (B7).

## Erros conhecidos neste estado

B3, B4, B5, B6, B7, B9, B12 (abertos); B10 corrigido e não commitado. Detalhes em [06](06-BUGS-PENDENCIAS.md).

## Serviços para executar o sistema

Backend (8090) e frontend (5173) são necessários; calculadora oficial (8080) e `GEMINI_API_KEY` são opcionais.
Na hora desta fotografia havia processos escutando em **8090 e 5173** (iniciados em sessões anteriores desta linha
de trabalho; a 8080 não estava ativa, logo o cálculo usaria o fallback simplificado).

## Não verificado nesta fase

`npm run build`/`lint`; testes de contrato com Gemini e calculadora; funcionamento de `ensaio-demo.ps1`;
atualidade dos CSVs oficiais; conteúdo integral da conversa original.

## Próxima ação recomendada

Responder à proposta de correção de B3/B4 (aprovar/ajustar). Em seguida implementá-la com um teste de isolamento por
rota e rodar a suíte completa.
