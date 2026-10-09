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

**Estado:** PARCIAL, revisada em 08/10/2026 (sem aprovação de conclusão). Inteligência Fiscal (sugestão de
NCM) implementada no backend a pedido do responsável: 4 endpoints do contrato do front, Gemini interpreta a mercadoria
e propõe até 4 NCMs, verificações honestas (vigência na TIPI aparece como não realizada), ponto de encaixe
`AvaliadorJev` para a JEV AI do colaborador; 10 testes de backend e E2E no navegador com Gemini real aprovados. Feito: motor de alertas
no front (`frontend/src/lib/alertas.ts`), agora com 8 testes automáticos (`npm test`). Revisão encontrou
R1 e R2 (alertas e crédito de compras), ambos resolvidos em 08/10/2026 (R2: crédito = menor entre a nota e a
correção, a confirmar com especialista; R1: compras viram "efeito no preço").
**Pendências:** A1, I-FISCAL, V1–V6 (S5 decidida em 08/10: base sem ICMS/PIS/Cofins),
validação profissional. Detalhes em `PENDENCIAS.md`.
**Progresso 08/10/2026 (tarde), sem aprovação de conclusão:** vigência da NCM pela tabela oficial do Siscomex, relatório
PDF, leitura de PDF/DOCX/XLSX, fila persistente com retomada e adaptador da JEV (API pública do Jev/TypeSafe, desligado,
não validado na API real). Backend 290 / 0 falhas; E2E 7/7 + 11/11. Pendentes: confirmar a JEV (JEV-CONFIRMAR),
hipótese de PIS/Cofins na base projetada (S5-PROJECAO), histórico da NCM (NCM-HIST), OCR.
Evidências: `docs/contexto-projeto/ENTREGA-INTELIGENCIA-FISCAL-2026-10-08.md`.

## Etapa 4 — Testes completos, refinamento e preparação do produto

**Objetivo:** produto consistente, confiável e utilizável.

**Entregáveis:** E2E abrangente de perfis e erros; revisão UX, desempenho, segurança,
estabilidade e relatórios; documentação e ambiente de demonstração seguros.

**Critérios de conclusão:** fluxos reais aceitos, riscos conhecidos registrados e tratados,
resultados e relatórios validados, documentação reproduzível e demonstração sem dados reais
ou chamadas pagas não autorizadas. Testes são obrigatórios também nas etapas anteriores.

**Estado:** CONCLUÍDA PARA A ENTREGA em 08/10/2026 (ver ressalvas abaixo); iniciada com aprovação do responsável. Primeiro incremento:
ensaio da demo adaptado a login ADMIN + CSRF (3 de 3 verdes em API isolada, banco em memória, sem IA real);
`.env` da raiz lido pelo backend (chave fora do Git, testes nunca a usam); testes do motor de alertas.
Suíte do backend verde após S5 e R2: 226 testes, 0 falhas, 0 erros, 7 ignorados, nas duas ordens.
**Evidência 08/10/2026 (tarde):** ensaio pela API com calculadora oficial + Gemini real (chave nova do `.env`)
1/1 verde, mesmos números do modo simplificado (Distribuidora R$ 264,58 → R$ 378,44), IA classificou 8/8 em
11,7 s. E2E no Chrome (`frontend/scripts/etapa4-e2e.mjs`): 11 fluxos aprovados, 0 erros JavaScript (login
inválido/ADMIN, comparativo do início, upload + classificação/cálculo com IA real, XML inválido e duplicado,
detalhe da nota, revisão/aceite, alertas, relatório e CSVs, perfil EMPRESA isolado, logout).
**Segurança do deploy (08/10/2026):** profile `prod` ativado pelo Dockerfile (sem console H2/Swagger/demo,
cookie Secure, senha do admin obrigatória), limite de tentativas de login e container sem root; 7 testes novos,
suíte 233 / 0 falhas nas duas ordens. Roteiro da apresentação em `docs/ROTEIRO_DEMO.md`.
**Deploy:** imagem única (API + front, `Dockerfile` na raiz) construída e validada em container (E2E 11/11).
**Deploy validado na URL pública (08/10/2026, Render, imagem única):** telas, rotas internas, 404 de arquivo
inexistente, 401 nas rotas protegidas, Swagger/H2/docs fechados, cookie Secure e HSTS; ensaio autenticado completo
1/1 verde (login ADMIN, upload nf1004, classificação, revisão, painel, CSV), Distribuidora R$ 264,58 → R$ 378,44.
No ensaio o Gemini respondeu 503 (sobrecarga) e o plano B das respostas gravadas assumiu, como projetado.
**Estado da Etapa 4:** critérios atendidos para a entrega do hackathon. **Ressalvas:** cálculo simplificado no
Render (sem a calculadora oficial); E2E de navegador não rodado contra a URL pública (criaria usuário de teste lá);
estimativas e regras S5/R2 aguardam validação profissional; pendências abertas em `PENDENCIAS.md`.
**Pendências:** ensaio final com quem vai apresentar; trocar a senha do administrador depois da avaliação.

## Trabalho fora das etapas — API pública v1 (09/10/2026)

Pedido explícito da responsável (missão autônoma), sem mudança de etapa: API REST `/api/v1` para ERPs e sistemas
contábeis sobre o motor da Inteligência Fiscal (chave de API presa a uma empresa, idempotência, limites, OpenAPI,
cliente de exemplo). Evidência: 35 testes novos, suíte do backend 353 / 0 falhas / 8 ignorados nas duas ordens,
ensaio ao vivo sem IA real. Não validada com Gemini real. Ver `docs/contexto-projeto/API-PUBLICA/07-HANDOFF-FINAL.md`.

## Continuidade e evidências

- Consultar `CONTEXTO_COMPLETO_TRIBIA.md`, `docs/contexto-projeto/00-LEIA-PRIMEIRO.md`
  até `09-ROADMAP.md`, `HANDOFF.md` e `PENDENCIAS.md` antes de decidir.
- Código e resultados reproduzíveis prevalecem sobre documentos legados divergentes.
- Atualizar este plano somente por progresso comprovado, decisão aprovada ou mudança justificada.
- Ao concluir tarefas relevantes, atualizar pontualmente HANDOFF, pendências e contexto afetado.
- Não iniciar a próxima etapa sem verificar critérios, apresentar riscos e obter aprovação.
- Falhas críticas de etapas anteriores exigem correção prioritária, com impacto explicado.
