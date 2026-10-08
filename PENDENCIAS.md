# Pendências do TribIA

Registro de bugs conhecidos, limitações e simplificações, para resolver depois do MVP (ou citar no pitch).
Atualizado a cada etapa. Itens resolvidos saem daqui e ficam no histórico do git.

## Bugs conhecidos

Nenhum aberto. Resolvidos em 08/10/2026: B1 (seed agora termina antes de a API aceitar requisições) e B2
(classificar e revisar já recalculam a nota, mantendo o cenário de CBS).

## Limitações técnicas

| # | Limitação | Impacto |
|---|-----------|---------|
| T1 | H2 em memória: tudo o que não é seed (uploads, revisões, cache da IA) some ao reiniciar. | Demo: não reiniciar a API entre o upload ao vivo e a apresentação. |
| T2 | A chamada à IA acontece dentro da transação do banco (alguns segundos). | Irrelevante com H2 e um usuário; revisar com banco real. |
| T3 | `POST /api/clientes/{id}/calcular` recalcula todas as notas numa única transação. | Lento para clientes com muitas notas. |
| T4 | O painel agrega em memória a cada requisição. | Ok para a demo; com volume real, pré-agregar. |
| T5 | IA: sem espera progressiva (backoff) quando a cota estoura; tenta o modelo de reserva e desiste. | No plano gratuito, itens podem ficar pendentes em rajadas. |
| T6 | Respostas gravadas da IA (profile `demo`) cobrem só os produtos das notas em `notas-demo-ao-vivo/`. Outro XML subido ao vivo sem IA fica pendente. | Usar as notas preparadas. Regravar com `GerarRespostasIaDemoTest` se mudarem. |
| T7 | Chave de acesso: só o dígito verificador é validado; não confere com emitente, modelo, série e número. | Baixo. |
| T8 | CORS liberado para qualquer origem; sem login. | Fora do escopo do MVP (adendo). |
| T9 | Revisão: "aplicar aos idênticos" vale só para itens do mesmo cliente. Em outros clientes, só as notas futuras herdam a correção (pelo cache global). | Itens já classificados de outros clientes precisam de revisão própria. |
| T10 | Revisão sem trilha de auditoria (quem revisou e o que era antes); só a data da última alteração. | Depende de login (fora do escopo). |
| T11 | "Uso e consumo" (`creditavel=false`) vale só para o item marcado; não se propaga aos idênticos. | Marcar item a item. |
| T12 | Painel `topItens` agrupa por NCM + descrição: o mesmo produto com descrição diferente na compra e na venda aparece em duas linhas. | Ranking um pouco fragmentado. |
| T13 | Painel `porRegime`: produto no campo do IS vai só para a faixa `SUJEITO_IS` (não soma também em `INTEGRAL`). | Escolha de apresentação; documentada no DTO. |
| T14 | Limites fixos no painel: 10 produtos em `topItens`, 5 fornecedores em `topFornecedores`. | Parametrizar se o front pedir. |
| T15 | `GET /api/notas/{id}/resumo` do documento base não foi criado: o resumo da nota vem em `POST /api/notas/{id}/calcular` e o do cliente no painel. | Criar se o front precisar. |
| T16 | CSV no padrão Excel pt-BR (";", vírgula decimal, BOM): em Excel/LibreOffice configurado em inglês os números podem virar texto. | Importar com separador ";" e decimal ",". |
| T17 | IA: classificar 8 produtos novos leva 11-12 s com `gemini-3.5-flash` (o modelo "pensa"). O `flash-lite` leva ~2,5 s, mas erra o azeite (ver V6). | Na demo, narrar a espera. Avaliar chamada assíncrona ou modelo mais rápido sem perder precisão. |
| T18 | Endpoints `/api/demo/*` existem só com `tribia.demo.habilitado=true` (profile `demo`); `reiniciar` apaga uploads, revisões e o cache aprendido. | Nunca habilitar fora da apresentação. |

## Simplificações tributárias (dizer no pitch, não esconder)

| # | Simplificação |
|---|---------------|
| S1 | Todo imposto destacado na compra é considerado pago (a LC 214 condiciona o crédito à extinção do débito). |
| S2 | Compras de fornecedor do Simples Nacional não têm o crédito limitado. |
| S3 | ICMS e ISS ficam fora do comparativo (não mudam em 2027). |
| S4 | A alíquota da CBS 2027 é estimativa (9,43%, configurável). TODO: confirmar se haverá a redução de 0,1 p.p. de compensação do IBS. |
| S5 | Base de cálculo de 2027 = valor do item. TODO: conferir na LC 214 o que sai da base na transição (ICMS, ISS, PIS/Cofins). |
| S6 | Os 3 clientes são tratados como revendedores no Imposto Seletivo (CST 200 / 200007, IS zero). Cliente fabricante não é suportado. |
| S7 | Município da operação = município do cliente (o destino da mercadoria não é considerado no IBS). |
| S8 | Crédito de PIS/Cofins hoje (Lucro Real) pela alíquota do comprador (1,65% + 7,6%), não pelo destacado pelo fornecedor. |
| S9 | Débito de PIS/Cofins hoje no Presumido usa o valor destacado na nota (respeita monofásico e alíquota zero), em vez de 0,65% + 3% sobre todas as saídas como diz o adendo. |
| S10 | Notas de devolução, complementares, de ajuste e com `tpNF=0` são rejeitadas no upload. |

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
| O1 | A chave do Gemini usada no desenvolvimento ficou exposta em conversa: revogar e gerar outra depois do hackathon. |
| O2 | O `nfe_teste_hackathon.xml` original nunca foi recebido: os testes usam uma reconstrução a partir da tabela do PDF (inclusive o NCM extinto 34022000 do detergente). |
| O3 | O pacote da calculadora baixado pelo portal veio truncado (`calculadora.tar.gz`); usamos a distribuição oficial `jar` via `ferramentas/atualizar_calculadora.py`. |
