# 12 — Fase 2: classificador e calculadora sem nota (09/10/2026)

Pedido da responsável, sabendo que as regras ainda aguardam validação profissional (S5, R2, V1–V6). Por isso toda
resposta sai marcada: a classificação como `SUGESTAO_AUTOMATICA`, o cálculo como `PROJECAO_PENDENTE_VALIDACAO`, com
avisos. Não há regra fiscal nova: os dois endpoints usam os mesmos motores do site, com uma entrada "avulsa".

## Endpoints

| Método e rota | Escopo | Limite | Descrição |
|---|---|---|---|
| `POST /api/v1/classificacoes` | `CLASSIFICAR` | 50 produtos | NCM + descrição → CST/cClassTrib sugeridos (**assíncrono**: 202 + consulta) |
| `GET /api/v1/classificacoes/{id}` | `CLASSIFICAR` | — | Situação e resultado do pedido |
| `POST /api/v1/calculos/simular` | `CALCULAR` | 100 itens | Itens com cClassTrib e valor → CBS/IBS/IS de 2027; nada é gravado |

Chaves novas recebem os seis escopos por padrão (análises, notas, classificar, calcular); as antigas mantêm os seus.
`/api/v1/uso` aceita qualquer escopo de leitura ou os dois novos.

## Classificação de produtos avulsos

```json
POST /api/v1/classificacoes
{"produtos": [{"referencia": "SKU-1", "ncm": "1006.30.21", "descricao": "ARROZ TIPO 1 5KG",
               "unidade": "PCT", "valorUnitario": 23.90}]}
```

- Mesma ordem do fluxo da nota (`ClassificacaoService.classificarAvulsos`): cache privado da empresa → catálogo SEED
  → IA (lista fechada de opções; benefício de anexo só com NCM na lista oficial; códigos que dependem do comprador
  nunca sugeridos) → respostas gravadas (profile demo) → regra oficial do NCM (confiança 0,40).
- Produtos repetidos na chamada (mesmo NCM + descrição normalizada) vão uma vez só à IA.
- **Cota:** os produtos distintos enviados à IA contam na **mesma cota diária de itens das notas** (500 por chave). Se
  não couberem no que resta, nenhum vai à IA e eles saem `SEM_CLASSIFICACAO` com aviso; cota já esgotada: 429
  `COTA_DIARIA_ITENS_IA_EXCEDIDA`. A trava por chave é compartilhada com o envio de notas.
- Nada vira classificação de item. As sugestões da IA vão para o cache **privado da empresa** (não validadas), como no
  fluxo da nota: a próxima chamada, ou a próxima NF-e com o mesmo produto, sai do cache sem gastar cota. O cache de uma
  empresa nunca serve outra (testado).
- `situacao`: `PENDENTE_REVISAO` para tudo que não foi confirmado por uma pessoa (a IA nunca nasce aceita);
  `CONFIRMADA` só para o que veio do cache validado com confiança suficiente; `SEM_CLASSIFICACAO` sem evidência.

## Simulação do cálculo de 2027

```json
POST /api/v1/calculos/simular
{"operacao": "VENDA", "ufDestino": "SP", "municipioDestino": "3550308", "aliquotaCbs": 8.8,
 "itens": [{"referencia": "SKU-1", "ncm": "22021000", "cClassTrib": "000001", "quantidade": 96,
            "valor": 959.04, "icms": 172.63, "pis": 0, "cofins": 0}]}
```

- `CalculoService.simular`: mesma base de 2027 (valor − ICMS − PIS − Cofins informados), IS só para empresa
  fabricante vendendo (as demais: revenda, IS zero), calculadora oficial com plano B simplificado (aviso),
  `aliquotaCbs` opcional como cenário (0,01–30).
- `cst` é opcional (deduzido do cClassTrib); par inconsistente ou cClassTrib fora das opções de NF-e: 400
  `DADOS_INVALIDOS` com `campos` (ex.: `itens[0].cClassTrib`).
- `VENDA` devolve `debito` (CBS + IBS + IS); `COMPRA` devolve `credito` (CBS + IBS; IS não gera crédito). A simulação
  de compra supõe fornecedor do regime regular e pagamento confirmado (aviso), diferente da apuração da nota.
- Município/UF vazios usam os da empresa da chave. Não chama a IA e não consome cota.

Exemplo conferido à mão (teste): refrigerante R$ 959,04 com R$ 172,63 de ICMS → base R$ 786,41 → CBS R$ 74,16 + IBS
R$ 0,39 + R$ 0,39 = **R$ 74,94**, o mesmo valor do item no painel da plataforma.

## Erros novos

| HTTP | `codigo` | Quando |
|---|---|---|
| 400 | `DADOS_INVALIDOS` (+ `campos`) | NCM/cClassTrib/CST inválidos, valor ≤ 0, lista vazia ou acima do limite |
| 422 | `CALCULO_RECUSADO` | A calculadora oficial recusou o cálculo |
| 503 | `CALCULADORA_INDISPONIVEL` | Modo só oficial (`tribia.calculo.modo=OFICIAL`) e calculadora fora do ar |
| 429 | `COTA_DIARIA_ITENS_IA_EXCEDIDA` | Cota de itens para a IA esgotada (compartilhada com as notas) |

## Evidências

- `ApiPublicaFase2Test`: 9 testes (classificação com IA e cache, repetidos agrupados, cache isolado por empresa, cota
  que não cabe, cota esgotada, cota compartilhada com as notas, entradas inválidas, venda/compra/redução de 60%/cenário
  de CBS com valores conferidos à mão, cClassTrib/CST inválidos, escopos). Suíte: **371 testes, 0 falhas, 8
  ignorados**, nas duas ordens.
- Ensaio real (API isolada, Gemini real, JEV desligada, 09/10/2026): 5 produtos → arroz 200003 (alíquota zero),
  detergente 000001 (integral: fora do Anexo VIII), azeite 000001 (sem código de insumo agropecuário), sabonete 200035
  (redução de 60%, NCM na lista), cerveja 000001; todos `PENDENTE_REVISAO`. Repetição: 0,0 s, origem `CACHE`, sem cota.
  Simulação dos 5 (valor 1.000, ICMS 180): base 820; integral R$ 78,15; redução de 60% R$ 31,25; cesta zero; cerveja
  no campo do IS com IS zero (revenda). Uso 5/500.

## Tempo de resposta: classificação assíncrona (resolvido em 09/10/2026)

No primeiro ensaio real a classificação era síncrona e levou **69,4 s**: o `gemini-3.5-flash` não respondeu até o
limite de 60 s e só então o modelo reserva respondeu. Com proxies que cortam em ~100 s (Render), o integrador
receberia erro. Por isso a classificação passou a ser **assíncrona**, como as notas:

1. `POST /api/v1/classificacoes` (aceita `Idempotency-Key`): na requisição, só o cache (sem IA). Conta os produtos que
   iriam à IA, confere a cota e reserva. Responde **202** com `id`, `status` e `Location`.
   - Tudo resolvido pelo cache, ou cota insuficiente para os pendentes: já vem `CONCLUIDA` (no segundo caso, com aviso
     do saldo real da cota).
   - Senão, `EM_PROCESSAMENTO`, e a IA trabalha em segundo plano (fila da API pública, a mesma das notas).
2. `GET /api/v1/classificacoes/{id}` (escopo `CLASSIFICAR`) até `finalizada = true`. Outra empresa ou id inválido: 404
   `CLASSIFICACAO_NAO_ENCONTRADA`. Falha: `FALHOU` com `erro.codigo = PROCESSAMENTO_FALHOU`.
3. Reinício do servidor retoma os pedidos em processamento (cota já reservada). Limite de pedidos simultâneos por chave:
   `LIMITE_CLASSIFICACOES_SIMULTANEAS` (mesmo valor das análises).

Evidências: teste com a IA travada de propósito (`ApiPublicaFase2Test$Assincrono`): o POST responde
`EM_PROCESSAMENTO` em menos de 5 s e o resultado chega pela consulta depois que a IA libera. Ensaio real com o Gemini:
**POST em 151 ms** (antes 69 s), IA em segundo plano por 26 s; pedido novo com os mesmos produtos: 20 ms, do cache;
repetição idempotente: 9 ms. A simulação de cálculo continua síncrona (não chama a IA).
