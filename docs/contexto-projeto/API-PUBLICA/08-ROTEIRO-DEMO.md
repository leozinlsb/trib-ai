# 08 — Roteiro de demonstração (hackathon)

**Mensagem:** "O TribIA não precisa substituir o ERP de ninguém. O ERP manda o produto, o TribIA devolve a análise
fiscal em JSON — com fundamentação, verificação na NCM oficial e indicação de revisão humana."

Duração: 4–6 minutos. Tudo com dados fictícios. Ensaiado em 09/10/2026 (ver [05](05-TESTES-E-RESULTADOS.md)).

## Preparação (antes da apresentação)

1. Terminal 1 — backend isolado (banco em memória). Escolha:
   - **Sem custo (padrão):** comando do [guia, seção 1](06-GUIA-DE-USO.md#1-subir-o-backend). A análise termina em
     `FALHOU` com "IA não configurada" — mostra o fluxo, a segurança e o erro honesto.
   - **Com sugestão real:** mesmo comando sem `--tribia.llm.api-key=` e com `GEMINI_API_KEY` no ambiente. **Gera
     chamada ao Gemini (custo/cota): só com autorização do responsável.**
2. Terminal 2 — `export TRIBIA_API_URL=http://localhost:8091` e
   `export TRIBIA_API_KEY=$(node exemplos/api-publica/emitir-chave-local.mjs 1 "ERP Demo")` (com
   `TRIBIA_ADMIN_SENHA` sintética definida).
3. Navegador — `http://localhost:8091/swagger-ui.html`, documento "API pública v1 (integradores)".

## Roteiro

| # | Ação | O que mostrar |
|---|---|---|
| 1 | Swagger da API pública | Separada da plataforma; autenticação `X-API-Key`; contrato com estados, erros, limites |
| 2 | "Somos o ERP": `node exemplos/api-publica/cliente-tribia.mjs exemplos/api-publica/produto-exemplo.json` | 202 + id; acompanhamento das etapas; resultado (ou `FALHOU` honesto sem IA) |
| 3 | Destacar no JSON (`TRIBIA_SAIDA_JSON=1`) | `natureza`, `situacaoValidacao`, `revisaoHumana`, `avisos`; com IA: a NCM informada `3402.20.00` **não consta da NCM vigente embarcada** (conferido no CSV em 09/10/2026; a subposição presente é `3402.50.00`); se a sugestão for diferente da informada, o motor registra a divergência e manda para revisão (comportamento testado com outro produto; com este produto não foi executado sem IA real) |
| 4 | Rodar de novo com `TRIBIA_IDEMPOTENCY_KEY=pedido-1` duas vezes | Segunda vez: mesma análise, sem nova chamada à IA |
| 5 | `curl .../api/v1/uso` | Cota e consumo da chave |
| 6 | Segurança: chave errada e chave de outra empresa (emitir `... emitir-chave-local.mjs 2`) | 401; 404 idêntico a "não existe" |
| 7 | Na plataforma (ADMIN), abrir a Inteligência Fiscal da empresa 1 | A mesma análise aparece para revisão humana; ao revisar, a API passa a mostrar `DECISAO_REVISAO_HUMANA` |

## Falas de cuidado (obrigatórias)

- É **sugestão de IA conferida contra a NCM vigente**, não classificação definitiva nem decisão da Receita.
- A pontuação da JEV, quando ligada, é compatibilidade texto × NCM, não probabilidade de acerto.
- Validade da NCM não define tributação (IPI, ICMS, PIS/Cofins, CBS/IBS, IS).

## Plano B

- Backend não sobe: mostrar `ApiPublicaV1Test` (`./mvnw test -Dtest='ApiPublicaV1Test$FluxoComMotorFiscal'`), que
  executa o fluxo completo com o motor real e IA simulada.
- Gemini indisponível (503/cota): o resultado sai `FALHOU` com mensagem clara — use como demonstração de tratamento
  de falha.
