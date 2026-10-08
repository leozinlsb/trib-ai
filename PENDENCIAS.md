# Pendências do TribIA

Registro de bugs conhecidos, limitações e simplificações, para resolver depois do MVP (ou citar no pitch).
Atualizado a cada etapa. Itens resolvidos saem daqui e ficam no histórico do git.

## Bugs conhecidos

Etapa atual: **1, CONCLUÍDA COM RESSALVAS no ambiente isolado/caminhos avaliados**;
não certifica produção/fiscal. Etapa 2 em andamento no front (branch `dev/nicolau`, ver HANDOFF). Referência: `PLANO_MESTRE_TRIBIA.md`.
Resultados, auditoria individual e plano histórico:
`docs/contexto-projeto/CONCLUSAO-ETAPA-1-2026-10-08.md`. Relatórios anteriores preservados.

Resolvidos nos caminhos testados: B1 (seed antes da API), B2 (classificar/revisar recalculam),
B4, guardas diretas B3 e **B3-CACHE**. IA/revisão/aceite privados por empresa; catálogo somente
SEED. Legado IA/MANUAL/desconhecido não reutilizado nem apagado/reatribuído. 120 testes locais
direcionados verdes, 10 fluxos E2E Chrome aprovados; seis contratos/fallback resolvidos.
Classificações históricas potencialmente copiadas exigem avaliação autorizada antes de uso
real; o patch não modifica notas já processadas. Não iniciar Etapa 2 automaticamente.
B3: autorização central por empresa aplicada nos serviços, consultas agregadas, notas/itens, lote, revisão,
CSV/JSON, usuários e seed. Demo restrita a ADMIN; console H2 também, pois antes permitia contornar a API.
B4: `ClienteListaDto.ativo` booleano presente, lista visível filtrada antes dos indicadores.
Evidência: 36 testes direcionados verdes, incluindo 14 novos casos de isolamento e 2 de contexto do seed;
duas empresas, negativas HTTP e serviços, snapshots de 8 tabelas e zero chamadas IA/calculadoras nas negativas.
Sem commit, sem mudanças fiscais, sem tocar dados reais. Abertos:

| # | Bug | Evidência | Correção proposta |
|---|-----|-----------|-------------------|
| B3-HISTORICO | Classificações eventualmente contaminadas pelo cache antigo não foram remediadas. | Defeito anterior reproduzido em dados sintéticos; ocorrência real não investigada. | Avaliar histórico somente em ambiente autorizado, aprovar remediação auditável; não inferir dono/apagar automaticamente. Etapa 1. |
| B5 | ~~Detalhe da nota mostra "Pendente" mesmo classificado.~~ Corrigido no front (branch `dev/nicolau`), aguardando build/teste local. | `itemClassificado` agora lê `classificacao` persistida. | Validar no navegador e remover daqui após o merge. |
| B6 | ~~Relatório (back) contava classificados só pelo XML.~~ Corrigido em `dev/nicolau`: soma as classificações persistidas. | `RelatorioService` + `ClassificacaoRepository.findByItemIdIn`. | Escrito sem compilar: rodar `.\mvnw.cmd test` e conferir o relatório. |
| I-FISCAL | Telas de Inteligência Fiscal chamam 4 endpoints inexistentes. | `api/inteligenciaFiscal.ts`. | Ocultas por padrão (`VITE_INTELIGENCIA_FISCAL`); implementar back na Etapa 3. |
| F-TABELA | Exceções NCM em linhas repetidas podem reintroduzir associação excluída. | Arroz/feijão retornam múltiplos códigos; fallback agora se abstém. | Conferir extração/semântica com base oficial, Etapa 3; não alterar tabelas sem validação. |

## Testes do backend (08/10/2026)

Histórico anterior: 156 testes, 40 falhas + 1 erro → 23 falhas, 0 erros, 7 ignorados.
Resultado inicial reproduzido: 172 testes, 22 falhas, 0 erros, 7 ignorados.
Resultado ampliado anterior: 178 testes, 148 passaram, 23 falhas, 0 erros, 7 ignorados.
Após B3-CACHE: 195 testes / 167 passaram / 21 falhas / 0 erros / 7 ignorados (histórico).
**Final com RTC offline: 220 testes, 201 passaram, 15 falhas fiscais, 0 erros, 4 ignorados**;
sem RTC seriam 198 passaram/7 ignorados. **120 direcionados locais + 3 contratos reais RTC
aprovados**, build/lint verdes, 10 E2E aprovados. Comandos em [07](docs/contexto-projeto/07-TESTES-EXECUCAO.md).
Restam 13 divergências S5 conhecidas + 2 antes mascaradas (CSV/cache-only), auditadas
individualmente; fórmulas/alíquotas/tabelas/valores esperados intactos. Base legal consultada,
mas enquadramento temporal e implementação completa exigem validação profissional.
Os 4 ignorados são 2 Gemini reais e 2 geradores opt-in. Não iniciar Etapa 2 automaticamente.
Histórico abaixo explica correções anteriores, não as falhas atuais.
36 dos 41 testes quebrados paravam em 401/403. Eles foram ajustados só no lado do teste (usuário admin via
`@WithUserDetails` e `.with(csrf())` nas requisições que alteram dados, o mesmo padrão de `NotaControllerTest`),
em 6 classes: Apuração, ClassificaçãoIA, Dashboard, Relatório, Revisão e Roteiro demo. A segurança da aplicação
não mudou. Desses 36, 18 passaram; os outros 18 agora mostram a falha real que o 401 escondia. Nenhuma das 23
restantes era de autenticação (evidências históricas); a execução P0.1 não mudou esses testes:

## Divergências fiscais restantes

| Falhas | Causa comprovada | Decisão pendente |
|--------|------------------|------------------|
| 15 (Apuração 4, Dashboard 6, Revisão 3, Roteiro 1, CSV 1) | Padrão `excluir-tributos-da-base=true` subtrai ICMS/PIS/Cofins; referências antigas usam base cheia. Experimento causal em H2 apenas com base antiga: 15/15 aprovados. Ex.: crédito 74,34 esperado / 54,07 atual. Não é solução de produção. | Validar incidência na operação projetada, dados necessários e exclusões; só então corrigir implementação/fixtures/gabarito. Auditoria e fontes no relatório, Etapa 3. |

## Limitações técnicas

| # | Limitação | Impacto |
|---|-----------|---------|
| T1 | H2 em arquivo (`backend/data/`, fora do Git). Alterar `TRIBIA_ADMIN_SENHA` depois não muda senha já gravada. | Não apagar banco para trocar senha; recuperação segura de acesso requer procedimento autorizado (fluxo próprio ainda ausente). |
| T2 | Classificação e cálculo separam leitura, chamada externa e gravação; a revisão ainda envolve o recálculo numa transação externa. | Avaliar duração da transação da revisão com banco real (não modificado na P0.1). |
| T3 | Cálculo em lote é síncrono, com transações por nota e autorização da empresa e de cada nota. | Lento para clientes com muitas notas; não é uma única transação global. |
| T4 | O painel agrega em memória a cada requisição. | Ok para a demo; com volume real, pré-agregar. |
| T5 | IA: em 429/503 espera e tenta o mesmo modelo uma vez, depois o de reserva, e desiste (sem backoff progressivo). | No plano gratuito, itens podem ficar pendentes em rajadas. |
| T6 | Respostas gravadas da IA (profile `demo`) cobrem só os produtos das notas em `notas-demo-ao-vivo/`. Outro XML subido ao vivo sem IA fica pendente. | Usar as notas preparadas. Regravar com `GerarRespostasIaDemoTest` se mudarem. |
| T7 | Chave de acesso: upload confere dígito e dados da nota; fixture de cache agora coerente. | Validação preservada e teste de cache entre empresas verde. |
| T8 | Sessão + CSRF/guardas/cache testados, HTTP real e E2E Chrome aprovados. | Escopo sintético não certifica produção; O1/histórico pendentes, desabilitar console H2/demo em produção. |
| T9 | Cache IA/revisão privado por empresa; catálogo somente SEED. Legado sem dono ignorado, não apagado. | Possível aumento de chamadas IA; notas históricas potencialmente contaminadas requerem revisão autorizada, sem limpeza automática. |
| T10 | Revisão sem trilha de auditoria (quem revisou e o que era antes); só a data da última alteração. | Depende de login (fora do escopo). |
| T11 | "Uso e consumo" (`creditavel=false`) vale só para o item marcado; não se propaga aos idênticos. | Marcar item a item. |
| T12 | Painel `topItens` agrupa por NCM + descrição: o mesmo produto com descrição diferente na compra e na venda aparece em duas linhas. | Ranking um pouco fragmentado. |
| T13 | Painel: sujeitoIs separado de porRegime; contrato do teste corrigido/verificado. | Não somar sujeitoIs como regime adicional; integração visual do painel na Etapa 2. |
| T14 | Limites fixos no painel: 10 produtos em `topItens`, 5 fornecedores em `topFornecedores`. | Parametrizar se o front pedir. |
| T15 | `GET /api/notas/{id}/resumo` agora verifica acesso à nota; o front ainda não usa. | Integrar na fase seguinte. |
| T19 | Rotas do backend que o front ainda não usa: classificar/calcular nota (`POST /api/notas/{id}/classificar`, `/calcular`), recalcular empresa (`POST /api/clientes/{id}/calcular`), pagamento (`PUT /api/notas/{id}/pagamento`), revisão (`GET /api/clientes/{id}/revisao`, `PUT /api/itens/{id}/classificacao`, `GET /api/classificacoes/opcoes`), painel 2027 (`GET /api/clientes/{id}/dashboard`), resumo da nota e os dois CSVs. O upload só importa (não classifica). | Integrar só depois de B3, para não expor dados de outras empresas pela interface. |
| T16 | CSV no padrão Excel pt-BR (";", vírgula decimal, BOM): em Excel/LibreOffice configurado em inglês os números podem virar texto. | Importar com separador ";" e decimal ",". |
| T17 | IA: classificar 8 produtos novos leva 11-12 s com `gemini-3.5-flash` (o modelo "pensa"). O `flash-lite` leva ~2,5 s, mas erra o azeite (ver V6). | Na demo, narrar a espera. Avaliar chamada assíncrona ou modelo mais rápido sem perder precisão. |
| T18 | Endpoints `/api/demo/*` exigem ADMIN e `tribia.demo.habilitado=true`; `reiniciar` apaga uploads, revisões e cache. Seed inicial usa ADMIN persistido em contexto temporário, restaurado inclusive na falha. | Nunca habilitar em produção; adaptar ensaio para login ADMIN + CSRF. Nenhum reset em dados reais foi executado. |

## Simplificações tributárias (dizer no pitch, não esconder)

| # | Simplificação |
|---|---------------|
| S1 | Todo imposto destacado na compra é considerado pago (a LC 214 condiciona o crédito à extinção do débito). |
| S2 | Compras de fornecedor do Simples Nacional: padrão `SEM_CREDITO` (conservador), porque a nota não traz o valor recolhido no Simples (`tribia.calculo.credito-fornecedor-simples`). |
| S3 | Comparativo atual não inclui apuração de ICMS/ISS; não representa carga tributária total. |
| S4 | A alíquota da CBS 2027 é estimativa (9,43%, configurável). TODO: confirmar se haverá a redução de 0,1 p.p. de compensação do IBS. |
| S5 | Código subtrai ICMS/PIS/Cofins do valor da operação; não recebe ISS nessa função. LC 214 art. 12 consultado confirma direção das exclusões, não o motor/projeção temporal inteira. 15 referências antigas preservadas; validação profissional antes de mudar fórmula/gabarito. Avisos de estimativa na UI/API; não usar como apuração definitiva. |
| S6 | Imposto Seletivo: a empresa pode ser marcada como fabricante (campo `fabricante`); as demais são tratadas como revendedoras (CST 200 / 200007, IS zero). |
| S7 | Código prioriza município/UF de destino da nota; usa cliente como fallback se ausente. Correspondência com local fiscal da operação ainda precisa de validação. |
| S8 | Crédito de PIS/Cofins hoje (Lucro Real) pela alíquota do comprador (1,65% + 7,6%), não pelo destacado pelo fornecedor. |
| S9 | Débito de PIS/Cofins hoje no Presumido usa o valor destacado na nota (respeita monofásico e alíquota zero), em vez de 0,65% + 3% sobre todas as saídas como diz o adendo. |
| S10 | Escopo existente: finNFe 1/2/4 e tpNF 0/1; ajuste e outras finalidades fora do escopo. Testes corrigidos com negativas sem gravação; não certificam regras tributárias de devolução. |

## Validar com especialista tributário

| # | Ponto |
|---|-------|
| V1 | Medicamentos: 200032 (redução de 60%) x 200009 (alíquota zero, Anexo XIV). O seed usa 200032 com confiança 0,65 (vai para a revisão). |
| V2 | Vitamina C (NCM 2106.90.30): integral ou medicamento (200032)? Seed usa integral com confiança 0,60. |
| V3 | Detergente (NCM 3402.50): a IA chegou a sugerir o Anexo VIII (60%); a lista oficial por NCM não inclui esse código. Hoje: integral. |
| V4 | Água sanitária (2828.90.11) e papel toalha (4818.20): fora do Anexo VIII pela lista oficial; confirmar. |
| V5 | Resultado da Distribuidora (+333% em 2027: refrigerante deixa de ser monofásico na revenda; óleo de soja sai da alíquota zero para redução de 60%). |
| V6 | Códigos de "insumos agropecuários" (200038, 515001) aparecem nas regras oficiais de capítulos inteiros (ex.: 15, óleos): o `flash-lite` escolheu 200038 para azeite de varejo. Avaliar trava extra: esses códigos só valem na venda para produtor rural. |

## Operacional / segurança

| # | Ponto |
|---|-------|
| O1 | Revogação da chave Gemini exposta não confirmada. Nenhum padrão encontrado nos arquivos rastreados atuais não comprova revogação/histórico. Responsável deve confirmar revogação na conta, registrar evidência sanitizada e provisionar substituta fora do Git. Bloqueia uso real da integração; não usar/testar chave antiga. |
| O2 | O `nfe_teste_hackathon.xml` original nunca foi recebido: os testes usam uma reconstrução a partir da tabela do PDF (inclusive o NCM extinto 34022000 do detergente). |
| O3 | O pacote da calculadora baixado pelo portal veio truncado (`calculadora.tar.gz`); usamos a distribuição oficial `jar` via `ferramentas/atualizar_calculadora.py`. |
