# Plano mestre do TribIA

Referência estratégica aprovada em 08/10/2026. Existem exatamente quatro etapas principais.
O roadmap e os planos anteriores continuam como histórico e backlog; suas prioridades não
autorizam avançar de etapa nem implementar funcionalidades novas automaticamente.

## Etapa 1 — Segurança, estabilização e isolamento entre empresas

**Objetivo:** base segura e estável antes de ampliar funcionalidades.

**Entregáveis:** autorização central nos serviços e operações em lote; isolamento de consultas,
alterações, classificação, cálculo e exportação; autenticação/sessões/CSRF preservados;
estado ativo/inativo coerente; testes automatizados e validação visual em ambiente isolado;
investigação documentada das falhas existentes, sem mascarar divergências fiscais.

**Critérios de conclusão:** testes de acesso entre pelo menos duas empresas e ausência de
efeitos colaterais nas negativas; fluxos principais verificados; build/lint sem regressão;
falhas restantes classificadas, riscos críticos resolvidos e ressalvas explicitamente aceitas.
Nenhuma afirmação de correção fiscal sem evidência e validação apropriada.

**Estado:** CONCLUÍDA COM RESSALVAS no ambiente isolado e nos caminhos avaliados, não
liberação de produção nem certificação fiscal. B3/B4/B3-CACHE verdes; 120 testes locais
direcionados aprovados (104 + 16 não sobrepostos), 10 fluxos E2E Chrome aprovados, zero
pageerrors; 3 contratos reais RTC offline aprovados. Suíte final: 220 testes, 201 passaram,
15 falhas fiscais preservadas, 0 erros, 4 ignorados. Build/lint e diff --check verdes.
Seis contratos estabilizados; fallback sem evidência/ambíguo mantém pendência. Avisos de
estimativa na API e UI. Valores esperados, fórmulas, alíquotas e tabelas fiscais intactos.

**Ressalvas:** 13 divergências fiscais conhecidas + 2 antes mascaradas, auditadas
individualmente (S5); base legal consultada não certifica toda a implementação/projeção.
Etapa 3 deve resolver com fundamentação/especialista. Gemini real e geradores opt-in não
executados. Antes de uso real, confirmar revogação da chave exposta e autorizar avaliação
do histórico possivelmente contaminado; não houve limpeza/reatribuição automática.
Etapa 1 não significa prontidão para dados reais. B5 continua na Etapa 2.

**Atualização 08/10/2026:** O1 confirmado pelo responsável (chave antiga revogada, nova só no `.env`).
**Próxima tarefa prioritária (histórica):** responsável confirma O1 e autoriza triagem histórica antes
de uso real. Após aprovação de transição, pode-se iniciar Etapa 2 em ambiente isolado por
B5, mantendo revisão/avisos de simulação. Não avançar automaticamente. Evidências e
procedimento seguro: `docs/contexto-projeto/CONCLUSAO-ETAPA-1-2026-10-08.md`.
Relatórios anteriores de cache/diagnóstico preservados como histórico.

## Etapa 2 — Integração completa entre frontend e backend

**Objetivo:** tornar as funcionalidades existentes utilizáveis de ponta a ponta.

**Entregáveis:** mapa tela/endpoint atualizado; classificação, cálculo, pagamento, revisão,
indicadores e relatórios existentes conectados; estados, erros e permissões corretos na UI.
Fluxo: login → empresa → XML → processamento → classificação → cálculo → resultados → relatório.

**Critérios de conclusão:** ações reais e contratos verificados; resultados persistidos
corretamente exibidos; erros e bloqueios tratados; fluxos completos de perfis diferentes
testados. Reutilizar o backend antes de criar serviços novos.

**Estado:** CONCLUÍDA, aprovada pelo responsável em 08/10/2026. Integração feita em `dev/nicolau`
(merge na `main`, commit `50a026c`): classificar/calcular, painel 2027, revisão, opções e relatório
conectados; B5 e B6 corrigidos. Evidência na aprovação: build e lint do front verdes; backend compila e os
testes de relatório passam; ensaio da demo pela API (login ADMIN + CSRF, upload → classificação →
cálculo → revisão → painel → CSV) 3 de 3 sem erro e com os mesmos números.
**Ressalvas:** fluxo visual no navegador validado manualmente pelo Nicolau (7 testes), sem E2E
automatizado da Etapa 2; endpoints `resumo`, `pagamento` e CSVs ainda sem uso na UI (T19).

## Etapa 3 — Inteligência Fiscal e validação tributária

**Objetivo:** desenvolver a proposta de valor fiscal com necessidade real e evidência.

**Entregáveis:** fluxo funcional validado; contratos/endpoints necessários; papel de Gemini
e JEV AI esclarecido; distinção NCM/CST/cClassTrib; justificativas, fontes, rastreabilidade,
incerteza e revisão humana; regras verificadas com fontes oficiais/especialista.

**Critérios de conclusão:** necessidade e integração demonstradas; regras com fundamentos
verificáveis; IA não apresentada como verdade fiscal definitiva; casos de baixa confiança
tratados; testes fiscais e de isolamento; custos/chamadas externas previamente autorizados.

**Estado:** PARCIAL, revisada em 08/10/2026 (sem aprovação de conclusão). Feito: motor de alertas
no front (`frontend/src/lib/alertas.ts`), agora com 8 testes automáticos (`npm test`). Revisão encontrou
R1 e R2 (alertas e crédito de compras), ambos resolvidos em 08/10/2026 (R2: crédito = menor entre a nota e a
correção, a confirmar com especialista; R1: compras viram "efeito no preço").
**Pendências:** A1, I-FISCAL, V1–V6 (S5 decidida em 08/10: base sem ICMS/PIS/Cofins),
validação profissional. Detalhes em `PENDENCIAS.md`.

## Etapa 4 — Testes completos, refinamento e preparação do produto

**Objetivo:** produto consistente, confiável e utilizável.

**Entregáveis:** E2E abrangente de perfis e erros; revisão UX, desempenho, segurança,
estabilidade e relatórios; documentação e ambiente de demonstração seguros.

**Critérios de conclusão:** fluxos reais aceitos, riscos conhecidos registrados e tratados,
resultados e relatórios validados, documentação reproduzível e demonstração sem dados reais
ou chamadas pagas não autorizadas. Testes são obrigatórios também nas etapas anteriores.

**Estado:** INICIADA em 08/10/2026 com aprovação do responsável. Primeiro incremento:
ensaio da demo adaptado a login ADMIN + CSRF (3 de 3 verdes em API isolada, banco em memória, sem IA real);
`.env` da raiz lido pelo backend (chave fora do Git, testes nunca a usam); testes do motor de alertas.
Suíte do backend verde após S5 e R2: 226 testes, 0 falhas, 0 erros, 7 ignorados, nas duas ordens.
**Evidência 08/10/2026 (tarde):** ensaio pela API com calculadora oficial + Gemini real (chave nova do `.env`)
1/1 verde, mesmos números do modo simplificado (Distribuidora R$ 264,58 → R$ 378,44), IA classificou 8/8 em
11,7 s. E2E no Chrome (`frontend/scripts/etapa4-e2e.mjs`): 11 fluxos aprovados, 0 erros JavaScript (login
inválido/ADMIN, comparativo do início, upload + classificação/cálculo com IA real, XML inválido e duplicado,
detalhe da nota, revisão/aceite, alertas, relatório e CSVs, perfil EMPRESA isolado, logout).
**Pendências:** revisão de segurança para o deploy
(console H2 e `/api/demo` desligados fora da demo).

## Continuidade e evidências

- Consultar `CONTEXTO_COMPLETO_TRIBIA.md`, `docs/contexto-projeto/00-LEIA-PRIMEIRO.md`
  até `09-ROADMAP.md`, `HANDOFF.md` e `PENDENCIAS.md` antes de decidir.
- Código e resultados reproduzíveis prevalecem sobre documentos legados divergentes.
- Atualizar este plano somente por progresso comprovado, decisão aprovada ou mudança justificada.
- Ao concluir tarefas relevantes, atualizar pontualmente HANDOFF, pendências e contexto afetado.
- Não iniciar a próxima etapa sem verificar critérios, apresentar riscos e obter aprovação.
- Falhas críticas de etapas anteriores exigem correção prioritária, com impacto explicado.
