# 09 — Roadmap priorizado

**Referência estratégica vigente:** [plano mestre](../../PLANO_MESTRE_TRIBIA.md), exatamente
quatro etapas. P0/P1/P2/P3 abaixo são rótulos históricos de prioridade, não novas etapas.
Mapeamento: P0.1/P0.2/P1.1 → Etapa 1; P1.2/P1.3 → Etapa 2; Inteligência Fiscal → Etapa 3;
validação abrangente → Etapa 4. Não avançar automaticamente.

**Estado atual:** Etapa 1 CONCLUÍDA COM RESSALVAS no ambiente isolado/caminhos avaliados;
Etapa 2 não iniciada. 120 direcionados locais, 3 RTC offline reais e 10 E2E Chrome aprovados.
Suíte final 220 testes / 201 passaram / 15 falhas fiscais / 0 erros / 4 ignorados; build/lint
verdes. Seis contratos estabilizados, fallback sem evidência/ambíguo mantém pendência.
Antes de uso real: revogação da chave, triagem autorizada do histórico e validação fiscal.
Próximo incremento proposto, só após aprovação: Etapa 2/B5 em ambiente isolado.
Ver [relatório atual](CONCLUSAO-ETAPA-1-2026-10-08.md); resultados P0.1 abaixo são históricos.

Princípios: mudanças pequenas, verificáveis e reversíveis; não enfraquecer segurança; não alterar regra fiscal sem
validação; não usar dados fictícios como reais; não chamar APIs pagas sem autorização; mudanças estruturais só com
aprovação.

## P0 — Críticos

### P0.1 Isolamento entre empresas (B3) + `ativo` na lista (B4) — guardas/cache/E2E de base verificados
- **Objetivo:** nenhuma rota devolve ou altera dados de empresa que o usuário não pode ver; `GET /api/clientes` traz
  `ativo` e lista só as visíveis.
- **Implementação:** `security/AcessoService`; serviços de clientes, notas, usuários, dashboard, relatório,
  classificação/revisão, cálculo, painel/CSV, demo/seed; `dto/ClienteListaDto`; `config/AdminSeeder`,
  `SeedRunner`, `SecurityConfig`; `UsuarioRepository`. Frontend já espera `ativo` e não foi editado.
- **Dependências validadas:** seed antes do servidor agora usa ADMIN persistido com contexto temporário restaurado;
  demo tem finalidade global e nenhum consumidor no front; ensaio continua pendente de login/CSRF.
  Guardas em serviços protegem também chamadas internas, consultas e lote; filtros de controller preservados.
- **Critérios:** usuário EMPRESA recebe 404 em qualquer recurso de outra empresa (incluindo itens e notas); admin
  mantém acesso; `/api/demo/*` só admin; lista traz `ativo`; UI mostra empresas ativas e habilita upload.
- **Evidência:** 36 testes direcionados verdes (incluindo 14 de isolamento e 2 de contexto do seed), 2 empresas,
  negativas sem persistência nem IA/calculadoras, lote e revisão restritos à empresa, ativo/inativo/reativação.
  Console H2 limitado a ADMIN para não contornar a API. Build/lint passam. Suíte completa: 172 testes,
  22 falhas B6/B7, 0 erros, 7 ignorados. Detalhes em [07](07-TESTES-EXECUCAO.md).
- **Validação posterior:** E2E Chrome confirmou lista/upload, inativação/reativação, acesso
  cruzado e logout; dez fluxos verdes. Código sem commit/push; dados reais intactos.
  Incremento B3-CACHE separou privado/catálogo e testou legado, sem migração destrutiva;
  classificações históricas já processadas requerem avaliação autorizada antes de uso real.

### P0.2 Higiene de segredos e dados locais
- Commitar `.gitignore` com `backend/data/` (B10); revogar a chave Gemini exposta (B13, ação da autora).
- **Critério:** `git status` limpo de `backend/data/`; nenhuma chave em arquivos versionados (`git grep`).

## P1 — Integrações essenciais

### P1.1 Estabilizar a suíte (B6, B7)
- **Progresso atual:** B7/contratos e fallback resolvidos; 15 divergências fiscais auditadas
  (13 + 2 antes mascaradas). Referências antigas preservadas até validação na Etapa 3;
  não liberar produção/apuração real nem usar flags fiscais para alegar suíte verde.
- **Objetivo:** suíte verde sem flags.
- **Passos:** (a) validar com fonte oficial/especialista a base 2027 sem ICMS/ISS/PIS/Cofins (S5) e a política de
  fallback por regra; (b) só então revisar os 13 testes numéricos e, separadamente, os 2 de fallback; (c) atualizar os 7 testes
  desatualizados após confirmação do autor (colunas do CSV, `sujeitoIs`, devoluções, chave de acesso com `nNF`
  coerente, nova tentativa Gemini).
- **Critério:** `mvnw test` com 0 falhas; nenhum teste removido nem enfraquecido; cada alteração explicada.

### P1.2 Mostrar a classificação no front (B5)
- **Arquivos:** `frontend/src/api/types.ts`, `pages/NotaDetalhe.tsx`, `components/analises/ClassificacaoItens.tsx`,
  `lib/aggregate.ts`, `hooks/useCobertura.ts`.
- **Critério:** item com `classificacao` exibe CST·cClassTrib, origem e confiança; sem classificação mostra pendente.
- **Teste:** Playwright com nota classificada via API; `npm run build` e `lint`.

### P1.3 Ligar o fluxo classificar → revisar → calcular → painel → CSV
- **Depende de:** P0.1.
- **Rotas:** `POST /api/notas/{id}/classificar`, `/calcular`, `PUT /pagamento`, `POST /api/clientes/{id}/calcular`,
  `GET /api/clientes/{id}/revisao`, `PUT /api/itens/{id}/classificacao`, `GET /api/classificacoes/opcoes`,
  `GET /api/clientes/{id}/dashboard`, CSVs.
- **Decisão de produto pendente:** o upload deve classificar automaticamente ou haver botão? (hoje não classifica).
  Cuidado com custo/latência da IA (≈12 s por nota nova).
- **Critério:** usuário completa o fluxo pela interface; erros da IA mostram aviso e itens pendentes (sem dados falsos).
- **Teste:** E2E com IA simulada/profile demo; nenhuma chamada paga sem autorização.

### P1.4 Documentação e scripts
- Atualizar `README.md` (B8) e `ensaio-demo.ps1` (B9); remover caminhos pessoais de docs.

## P2 — Funcionalidades centrais

### P2.1 Inteligência Fiscal (backend)
- **Objetivo:** implementar `analises-fiscais` conforme `frontend/docs/inteligencia-fiscal-api.md` (assíncrono,
  `AcessoService` em todo endpoint, `ProblemDetail`).
- **Antes de começar (decisões a obter):** o que é o JEV e como integrá-lo; fontes oficiais de NCM/vigência; política
  de retenção de anexos; limites de custo da IA; quem valida o conteúdo fiscal.
- **Arquivos:** novo pacote `service/inteligencia/…`, entidades `AnaliseFiscal`, controller; front já existe
  (`pages/fiscal/*`, `api/inteligenciaFiscal.ts`).
- **Critérios:** sem resultado fictício; "validado" nunca vira "aprovado pela Receita"; fontes citadas como retornam.
- **Testes:** isolamento por empresa, falha da IA, informações insuficientes, polling do front.

### P2.2 Validação fiscal das simplificações (S1–S10, V1–V6) com especialista; registrar fontes.

## P3 — Melhorias

- Troca/recuperação de senha; exclusão definitiva de empresas; relatórios armazenados.
- Desempenho: pré-agregação do painel (T4), cálculo em lote assíncrono (T3), transação externa da revisão (T2;
  classificação/cálculo já têm fases separadas).
- Testes de frontend e CI (build + lint + testes).
- Parametrizar limites do painel (T14); trilha de auditoria completa (T10 parcialmente atendida por `RegistroRevisao`).
- Acessibilidade e responsividade; refinamentos visuais.
