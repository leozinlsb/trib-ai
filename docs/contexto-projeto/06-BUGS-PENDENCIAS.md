# 06 — Bugs, limitações e pendências

Estado em 08/10/2026. "Status" só é **resolvido** com evidência citada. Complementa `PENDENCIAS.md` (limitações
T*, simplificações S*, validações V*, operacional O*).

## Bugs e estado após P0.1 autorizada

Validação mais recente: **Etapa 1 concluída com ressalvas no ambiente isolado/caminhos avaliados**.
120 direcionados locais, 3 contratos RTC offline e 10 E2E Chrome aprovados. Suíte 220 testes,
201 passaram, 15 falhas fiscais, 0 erros, 4 ignorados. Contratos/fallback resolvidos.
Antes de uso real: revogação da chave, triagem histórica autorizada e validação fiscal.
Etapa 2 não iniciada. [Execução e auditoria individual](CONCLUSAO-ETAPA-1-2026-10-08.md).

| ID | Gravidade | Descrição | Arquivos | Causa | Impacto | Status | Correção recomendada | Como verificar |
|---|---|---|---|---|---|---|---|---|
| **B3** | **Crítica** (segurança) | Acesso direto cruzado nas rotas de apuração, revisão, dashboard, CSV e lista | `security/AcessoService`, serviços, painel, demo/seed, `SecurityConfig` | Autorização anteriormente ausente | Leitura/alteração alheia e efeitos externos | **Corrigido nos caminhos testados**, não commitado | Preservar guardas e validar UI; avaliar histórico antes de uso real | 63 casos direcionados verdes, incluindo cinco HTTP reais |
| **B3-CACHE** | **Crítica** | Propagação indireta de justificativa/aceite privados entre empresas | ClassificacaoCache, ClassificacaoCacheRepository, ClassificacaoService, ChaveClassificacao | Cache antigo sem proprietário por produto | Vazamento e contaminação de validação | **Corrigido e testado**, não commitado | Privado por empresa; catálogo SEED; legado inseguro ignorado/preservado. Notas históricas já copiadas não limpas | Regressão verde, reuso próprio, IA/aceite/legado e snapshots; relatório atual |
| **B4** | Alta | Lista sem ativo bloqueava upload | ClienteListaDto | Campo ausente | Bloqueio indevido | **Corrigido/verificado, não commitado** | Manter contrato | API e Chrome testam ativo/inativo, bloqueio de upload/reativação |
| **B5** | Média | Item aparece "Pendente" no detalhe da nota mesmo classificado | `frontend/src/pages/NotaDetalhe.tsx:190`, `components/analises/ClassificacaoItens.tsx:126`, `lib/aggregate.ts:104`, `api/types.ts` | Front lê só `ibsCbsDestacado`; backend envia `classificacao` | Informação enganosa | **Aberto** | Tipar `classificacao`/`calculo` e exibir | Nota classificada mostra CST·cClassTrib e origem |
| **B6** | Alta (fiscal) | 15 divergências S5, 13 conhecidas + 2 antes mascaradas | Apuração 4, Dashboard 6, Revisão 3, Roteiro 1, CSV 1 | Base exclui ICMS/PIS/Cofins; referências antigas usam base cheia | Projeções/relatórios não certificados | **Aberto, auditado; Etapa 3** | Fonte consultada não basta para aprovar motor/gabarito; revisão profissional | Experimento causal isolado base antiga 15/15; suíte padrão mantém 15 falhas |
| **B7** | Média (teste) | Seis contratos anteriormente divergentes | NotaController, GeminiClient, Dashboard, Relatorio | Testes desatualizados frente a contratos existentes | Ruído | **Resolvido/verificado** | Contratos corrigidos, negativas reforçadas, assert fiscais preservados | Contratos verdes na suíte padrão |
| B8 | Baixa | README dizia H2 memória/JDBC mem | README.md | Documentação antiga | Setup errado | **Corrigido pontualmente** | Persistência, limitações/aviso do ensaio e fallback atualizados | Leitura comparada ao código |
| B9 | Média | `ensaio-demo.ps1` não autentica | `backend/ferramentas/ensaio-demo.ps1` (0 ocorrências de login/CSRF) | Script anterior ao login | Ensaio da demo falha com 401 (**inferido**, não executado) | Aberto | Adaptar para login+CSRF | Executar ensaio |
| B10 | Média | `backend/data/` (banco) não ignorado pelo Git | `.gitignore` | omissão | Risco de commitar dados/hash de senha | **Corrigido, não commitado** (`backend/data/` adicionado ao `.gitignore`) | Commitar a alteração | `git status` não lista `backend/data/` |
| B11 | Baixa | Senha do admin não muda após a primeira criação | `config/AdminSeeder.java` | Cria só se não houver ADMIN; DB persistente | Confusão ao mudar `TRIBIA_ADMIN_SENHA` | Documentado | Fluxo de troca de senha ou apagar `data/` | — |
| B12 | Média | Reinicialização global de demonstração | `service/demo/DemoService.java` | Antes só propriedade + login | Reset destrutivo permanece disponível ao ADMIN se habilitado | **Guarda corrigida P0.1** (parte de B3) | Status/reset exigem ADMIN antes de contagens/socket/deletes; nunca habilitar em produção | Negativas HTTP e serviço não alteram banco nem acionam IA/calculadoras; nenhum reset real executado |
| B13 | Média | Chave Gemini exposta em conversa durante o desenvolvimento | — | PENDENCIAS O1 | Cota/abuso | Aberto (revogar) | Gerar nova chave | — |

Resolvidos antes (evidência em `PENDENCIAS.md`): B1 (seed termina antes da API aceitar requisições), B2
(classificar/revisar recalculam a nota).

## Limitações e riscos

T1–T19, S1–S10, V1–V6, O1–O3: ver PENDENCIAS.md. Build/lint e E2E de base passaram; Gemini
real/revogação e histórico pendentes. Suíte 220 testes, 15 falhas fiscais, 0 erros, 4 ignorados.
RTC offline real: 3 contratos verdes. Relatórios não armazenados; sem
troca/recuperação de senha e sem exclusão definitiva. Classificação/cálculo têm fases separadas; revisão ainda
envolve recálculo em transação externa (T2). B3-CACHE corrigido com privado/catálogo (T9); legado
preservado e ignorado quando inseguro. Avaliar notas históricas potencialmente contaminadas somente
em ambiente autorizado, antes de uso real; sem ocorrência real comprovada ou limpeza automática.

## Divergências entre documentos antigos e o código

| Documento | Afirmação | Realidade (evidência) |
|---|---|---|
| Hermes `AUDITORIA_*`/`INTEGRACAO_FRONT_BACK.md` | isolamento multiempresa correto | falso na auditoria anterior; B3 corrigido agora com testes P0.1 |
| Hermes | existe `DashboardController` | não; o painel está em `ClienteController` (`/api/clientes/{id}/dashboard`) |
| Hermes | gestão de clientes OK | falso na auditoria anterior; B4 corrigido na P0.1 |
| Hermes | Gemini sugere NCM | falso: CST/cClassTrib |
| Hermes | não há mecanismo de revisão | falso: `RevisaoController` + `CriterioRevisao` |
| Hermes `FLUXO_FISCAL.md` | referências legais | várias erradas (ver [04](04-INTELIGENCIA-FISCAL.md) §4) |
| Hermes `PLANO_CORRECOES.md` | mock de JEV com pontuações fixas | contraria a regra de não usar dados fictícios |
| `HANDOFF.md` (antigo) | "~40 testes falham por falta de `@WithMockUser`" | eram 40 falhas + 1 erro; 36 por 401/403 (corrigido com `@WithUserDetails`+CSRF); `@WithMockUser` não serve |
| `HANDOFF.md`/`PENDENCIAS.md` (antigo) | caminhos `C:\Users\Leoba\...`, T1 memória, T8 sem login, S10 devoluções rejeitadas | já atualizados nesta fase |
| README.md (anterior) | H2 em memória | arquivo; corrigido B8 neste ciclo |
| `frontend/README.md` | "não há classificação por IA nem cálculo 2027" | o **backend** tem; o front não usa |
