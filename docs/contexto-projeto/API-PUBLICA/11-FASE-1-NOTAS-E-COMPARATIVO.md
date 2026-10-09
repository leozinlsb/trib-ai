# 11 — Fase 1: notas fiscais e comparativo (09/10/2026)

Amplia a API pública v1 para além da Inteligência Fiscal: o ERP envia o **XML da NF-e** e recebe os itens
classificados (CST/cClassTrib), o cálculo de 2027 e o comparativo hoje × 2027 da empresa. Não há regra fiscal nova:
a API chama os mesmos serviços do site (`NotaService`, `ClassificacaoService`, `CalculoService`, `DashboardService`).
A Fase 2 (classificação de produtos avulsos e calculadora sem nota) espera a validação profissional das regras.

## Endpoints

| Método e rota | Escopo | Sucesso | Descrição |
|---|---|---|---|
| `POST /api/v1/notas` | `NOTAS_ENVIAR` | 202 | `{referenciaExterna?, xml}`: importa na hora, classifica e calcula em segundo plano |
| `GET /api/v1/notas/{id}` | `NOTAS_LER` | 200 | Documento, itens com classificação e cálculo, comparativo da nota, avisos |
| `GET /api/v1/notas?referenciaExterna=&pagina=&tamanho=` | `NOTAS_LER` | 200 | Notas enviadas pela API para a empresa |
| `GET /api/v1/comparativo?de=AAAA-MM&ate=AAAA-MM` | `NOTAS_LER` | 200 | Indicadores, débito/crédito/líquido hoje × 2027 e por mês (site + API) |
| `GET /api/v1/uso` | `ANALISES_LER` ou `NOTAS_LER` | 200 | Agora mostra também `cotaDiariaItensIa`, `itensIaHoje`, `notasEmProcessamento` |

Chaves novas recebem os quatro escopos por padrão; chaves antigas mantêm os que tinham (`ANALISES_*`).

## Como o envio funciona

1. Na requisição, dentro de uma transação: importa a NF-e (mesmas validações do upload do site) e aplica o XML e o
   cache de classificação da empresa, **sem IA**. Os itens que sobram são os que iriam para a IA.
2. Cota: se os itens que sobraram cabem no que resta da **cota diária de itens para a IA** (padrão **500** por chave,
   `tribia.api-publica.cota-diaria-itens-ia`, ajustável por chave em `cotaDiariaItensIa`), eles são reservados. Se não
   cabem, a nota segue sem IA: é calculada com o que o XML e o cache resolveram e o resto fica `SEM_CLASSIFICACAO`, com
   aviso para classificar na plataforma. Cota já esgotada antes do envio: **429 `COTA_DIARIA_ITENS_IA_EXCEDIDA`**, nada
   é gravado.
3. Resposta 202 com o id (UUID) e o documento. Em segundo plano (fila própria, 2 threads, fila 20): classificação por
   IA dos itens reservados e cálculo de 2027. Reinício do servidor retoma os envios em processamento (é idempotente).
4. Itens que o cache da empresa já conhece não vão à IA nem gastam cota: a segunda nota com os mesmos produtos sai
   classificada por `CACHE`.

## Resposta da nota (resumo)

```json
{
  "id": "fda692ba-…", "referenciaExterna": "ERP-NF-1004", "status": "CONCLUIDA", "finalizada": true,
  "natureza": "PROJECAO_PENDENTE_VALIDACAO",
  "documento": {"chaveAcesso": "…", "numero": 1004, "operacao": "VENDA", "natureza": "DEBITO", "valorTotal": 6622.80, "…": "…"},
  "itens": [{"nItem": 6, "descricao": "PAO DE FORMA TRADICIONAL 500G", "ncm": "19059010",
             "classificacao": {"situacao": "PENDENTE_REVISAO", "cst": "200", "cClassTrib": "200034",
                               "regime": "REDUZIDA", "origem": "IA", "confianca": 0.95, "…": "…"},
             "calculo": {"impostoHoje": 49.89, "imposto2027": 14.96, "cbs": …, "origem": "SIMPLIFICADA", "…": "…"}}],
  "comparativo": {"hoje": {"debito": 127.37, "credito": 0, "liquido": 127.37},
                  "ano2027": {"debito": 138.47, "credito": 0, "liquido": 138.47}, "variacaoPct": …},
  "itensPendentesRevisao": 8, "itensClassificadosPorIa": 8, "erro": null,
  "avisos": ["Valores de 2027 são projeção pendente de validação fiscal: …", "…"]
}
```

- `status`: `EM_PROCESSAMENTO` → `CONCLUIDA` ou `FALHOU` (`erro.codigo = PROCESSAMENTO_FALHOU`; a nota continua na
  plataforma). `CONCLUIDA` quer dizer processamento terminado: itens podem continuar `PENDENTE_REVISAO`.
- `classificacao.situacao`: `CONFIRMADA` (XML, revisada ou aceita com confiança suficiente), `PENDENTE_REVISAO`
  (sugestão automática: IA nunca nasce aceita) ou `SEM_CLASSIFICACAO` (fora do cálculo). Mesma regra do site.
- Não saem ids internos (nota, item, empresa), nome de quem revisou, prompt ou chaves.

## Erros novos

| HTTP | `codigo` | Quando |
|---|---|---|
| 409 | `NOTA_JA_IMPORTADA` | A NF-e já está na empresa (pelo site ou pela API) |
| 409 | `IDEMPOTENCIA_CONFLITO` (+ `notaId`) | Mesma Idempotency-Key com outro corpo |
| 422 | `NOTA_INVALIDA` | XML ilegível, nota que não é da empresa da chave, modelo/finalidade fora do escopo |
| 404 | `NOTA_NAO_ENCONTRADA` | Id inexistente, malformado ou de outra empresa |
| 400 | `PARAMETRO_INVALIDO` | Competência fora de AAAA-MM ou período invertido no comparativo |
| 429 | `COTA_DIARIA_ITENS_IA_EXCEDIDA` / `LIMITE_NOTAS_SIMULTANEAS` | Cota de itens do dia / notas da chave em processamento |
| 503 | `SERVICO_OCUPADO` (+ `notaId`) | Fila cheia: a nota foi importada, mas ficou `FALHOU`; processe na plataforma |

## Segurança e isolamento

- **Escopo de integração** (`security/EscopoIntegracao`): a API chama os serviços do site dentro de um bloco preso à
  empresa da chave. Dentro dele o `AcessoService` vê um usuário de papel EMPRESA daquela empresa, então as mesmas regras
  do site valem (outra empresa = 404). Fora do bloco nada muda: o principal da API pública continua recusado pelo
  `AcessoService`. O bloco é por thread e removido no fim, inclusive em erro (testado).
- Duas barreiras contra acesso cruzado: a consulta do envio filtra por empresa da chave **e** o `AcessoService` recusa a
  nota de outra empresa. Mutação de controle: com o filtro removido da consulta, o `AcessoService` ainda barrou o
  acesso (404); o filtro foi restaurado.
- Negativas (escopo, cota, XML inválido, nota de outra empresa, duplicada, corpo inválido) não gravam nota nem envio e
  não chamam a IA (testado).

## Evidências (09/10/2026)

- `ApiPublicaNotasTest`: 10 testes (fluxo completo, cache sem nova IA, idempotência, duplicada/inválida/outra empresa
  sem efeitos, cota que não cabe, cota esgotada, escopos, isolamento, comparativo e período, escopo de integração,
  falha no processamento). Suíte do backend: **362 testes, 0 falhas, 8 ignorados**, nas duas ordens.
- Ensaio real em segundo plano (API isolada, banco em memória, Gemini real, JEV desligada): NF 1004 da Distribuidora
  `EM_PROCESSAMENTO` → `CONCLUIDA` em 12,3 s, 8 itens pela IA, nota R$ 127,37 → R$ 138,47 (mesmos números do site),
  reenvio idempotente 202 com `Idempotent-Replayed: true`, chave inválida 401, uso 8/500 itens.

## Pendências

- Fase 2 (`POST /api/v1/classificacoes` e `POST /api/v1/calculos/simular`) depois da validação profissional (S5, R2,
  V1–V6).
- Revisão humana continua só na plataforma; a API mostra o que está pendente.
- Contadores de cota no banco; limite por minuto e trava por chave em memória (como no resto da API pública).
- O cliente de exemplo (`exemplos/api-publica/cliente-tribia.mjs`) ainda cobre só as análises de NCM.
