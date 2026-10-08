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

**Próxima tarefa prioritária:** responsável confirma O1 e autoriza triagem histórica antes
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

**Estado:** não iniciada neste ciclo; algumas integrações já existem, outras estão parciais.
**Pendências:** B5 (UI ignora classificação/cálculo persistidos), endpoints não consumidos,
contratos e exportações. Detalhes em `MAPA_FUNCIONAL.md` e `INTEGRACAO_FRONT_BACK.md`.
**Primeira tarefa proposta, após aprovação:** confirmar o contrato do detalhe da nota e corrigir
B5, conectando a classificação existente e seus resultados, com testes de fluxo e isolamento.

## Etapa 3 — Inteligência Fiscal e validação tributária

**Objetivo:** desenvolver a proposta de valor fiscal com necessidade real e evidência.

**Entregáveis:** fluxo funcional validado; contratos/endpoints necessários; papel de Gemini
e JEV AI esclarecido; distinção NCM/CST/cClassTrib; justificativas, fontes, rastreabilidade,
incerteza e revisão humana; regras verificadas com fontes oficiais/especialista.

**Critérios de conclusão:** necessidade e integração demonstradas; regras com fundamentos
verificáveis; IA não apresentada como verdade fiscal definitiva; casos de baixa confiança
tratados; testes fiscais e de isolamento; custos/chamadas externas previamente autorizados.

**Estado:** parcial no produto, não autorizada para desenvolvimento neste ciclo.
Classificação Gemini existe; telas/contratos de análises fiscais não equivalem a backend pronto.
**Pendências:** implementação real das análises, papel da JEV, auditoria, limitações fiscais
S1–S10/V1–V6 em `PENDENCIAS.md`, incluindo a divergência de base S5.

## Etapa 4 — Testes completos, refinamento e preparação do produto

**Objetivo:** produto consistente, confiável e utilizável.

**Entregáveis:** E2E abrangente de perfis e erros; revisão UX, desempenho, segurança,
estabilidade e relatórios; documentação e ambiente de demonstração seguros.

**Critérios de conclusão:** fluxos reais aceitos, riscos conhecidos registrados e tratados,
resultados e relatórios validados, documentação reproduzível e demonstração sem dados reais
ou chamadas pagas não autorizadas. Testes são obrigatórios também nas etapas anteriores.

**Estado:** não iniciada como validação abrangente.
**Pendências:** dependerá das evidências das etapas anteriores; ensaio demo precisa de
autenticação ADMIN e CSRF, nunca resetar dados reais.

## Continuidade e evidências

- Consultar `CONTEXTO_COMPLETO_TRIBIA.md`, `docs/contexto-projeto/00-LEIA-PRIMEIRO.md`
  até `09-ROADMAP.md`, `HANDOFF.md` e `PENDENCIAS.md` antes de decidir.
- Código e resultados reproduzíveis prevalecem sobre documentos legados divergentes.
- Atualizar este plano somente por progresso comprovado, decisão aprovada ou mudança justificada.
- Ao concluir tarefas relevantes, atualizar pontualmente HANDOFF, pendências e contexto afetado.
- Não iniciar a próxima etapa sem verificar critérios, apresentar riscos e obter aprovação.
- Falhas críticas de etapas anteriores exigem correção prioritária, com impacto explicado.
