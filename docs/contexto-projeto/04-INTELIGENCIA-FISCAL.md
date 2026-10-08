# 04 — Lógica fiscal, IA e limitações

**Aviso:** nada aqui é parecer tributário. O que está marcado **[validar]** não foi conferido contra fonte oficial
nesta fase. Distinção essencial: **NCM** identifica a mercadoria; **CST** e **cClassTrib** dizem como CBS/IBS a
tratam em 2027. O classificador de notas devolve CST+cClassTrib e **nunca** sugere NCM (o NCM vem do XML).

## 1. Classificação das notas (CST + cClassTrib)

Ordem em `backend/.../service/classificacao/ClassificacaoService.java`:

1. **XML:** se o item traz o grupo IBS/CBS, usa CST+cClassTrib dele (`ClassificacaoXml`, origem `XML`).
2. **Cache privado da empresa**, depois **catálogo público SEED**, origem `CACHE`.
   NCM/descrição normalizados têm hash SHA-256 em namespaces EMPRESA/CATALOGO. IA e revisão
   só alimentam o privado; aceite humano não promove catálogo nem outras empresas. Catálogo
   vem de `resources/seed/classificacoes.json`, por carga ADMIN. Legado sem dono só é lido
   se fonte SEED; IA/MANUAL/desconhecido ignorados e preservados. B3-CACHE corrigido e testado;
   não tratar validação de um cliente como revisão fiscal universal. Notas históricas já
   processadas não alteradas. [Evidência e limites](CORRECAO-B3-CACHE-2026-10-08.md).
3. **IA (Gemini)** para o que sobrar: uma chamada por nota, produtos repetidos agrupados.
4. **Sem IA:** respostas gravadas (só profile demo), depois associação **única permitida**
   da tabela NCM com confiança 0,40, aceita=false (REGRA, não cache). Sem regra, ambiguidade,
   NCM inválido ou só códigos excluídos por adquirente: Optional vazio, mantém pendência e
   não calcula o item. Não inventa integral. 13 novos casos e fluxos de recuperação testados;
   [evidências e limites das exceções NCM](CONCLUSAO-ETAPA-1-2026-10-08.md).

Itens já classificados não são tocados (reclassificar é papel da revisão). Sugestões de IA/regra nunca nascem
aceitas (`aceita=false`). Todo código passa pela tabela oficial (`resources/dados-oficiais/tabela-cclasstrib.csv`).

### Gemini

- Cliente: `client/llm/GeminiClient.java` (HTTP, header `x-goog-api-key`, temperatura 0, resposta JSON com esquema).
- Modelos em ordem: `gemini-3.5-flash`, `gemini-3.5-flash-lite` (`tribia.llm.modelos`).
- Falha: 401/403 → erro de configuração; 429/503 → espera (Retry-After/retryDelay, máx. `tribia.llm.espera-maxima`,
  padrão 5 s) e **uma** nova tentativa no mesmo modelo, depois o próximo; no fim `LlmException INDISPONIVEL`.
- Classificador: `ClassificadorIa.java`. Prompt em `backend/src/main/resources/prompt-classificador.txt`
  (`{{OPCOES}}` recebe a lista fechada de opções). O prompt manda: escolher só entre as opções, usar a regra oficial
  por NCM como pista, não presumir benefício de anexo sem NCM na lista, confiança < 0,7 na dúvida, ignorar IS.
- Validação da resposta: código fora da lista, CST incoerente ou item faltando → rejeitado e reenviado uma vez;
  se falhar de novo, fica sem classificação com aviso. Benefícios de anexo só valem se o NCM consta em
  `ncm-aplicavel.csv`. Códigos que dependem do adquirente (`tribia.classificacao.codigos-por-adquirente`) não são
  oferecidos à IA.
- Revisão: `CriterioRevisao` — pendente se sem classificação, não aceita ou confiança < 0,70.
- Chave: só por variável `GEMINI_API_KEY`; testes forçam `tribia.llm.api-key=` vazio. **Nunca** gravar em arquivo.
- Riscos: custo/cota; a IA pode errar produto ambíguo (ex.: azeite → 200038, ver V6); latência (8 produtos ≈ 11–12 s
  no flash); a chamada ocorre dentro da transação (T2); chave usada no desenvolvimento foi exposta em conversa
  (PENDENCIAS O1: revogar). Nesta fase de documentação/estabilização nenhuma chamada ao Gemini foi feita
  (`GeminiContratoTest` não roda sem `GEMINI_API_KEY`; a variável não estava definida no ambiente dos testes).

## 2. Cálculo (hoje x 2027)

Regras em `service/apuracao/RegrasApuracao.java`; orquestração em `service/calculo/CalculoService.java`.

- **Hoje (PIS/Cofins):** saída = valor destacado na nota; entrada no Lucro Real = 1,65% + 7,6% sobre o item se
  creditável e CST tributado (`cst-com-credito=01,02,03`); entrada no Presumido = sem crédito; devoluções estornam.
- **2027:** CBS 9,43% (estimativa, `cbs-estimativa=true`), IBS UF 0,05% e municipal 0,05% (simbólicos); redução da CBS
  de transição = 0 (não confirmada). Alíquota efetiva = referência × (100 − redução)/100; IS integra a base de CBS/IBS
  (conferido contra a calculadora oficial 1.5.4, segundo o Javadoc). Arredondamento HALF_EVEN por item.
- **Base 2027 implementada:** valor da operação menos **ICMS/PIS/Cofins destacados**;
  função não recebe ISS (`excluir-tributos-da-base=true`). Texto compilado da LC 214 art. 12
  consultado; direção das exclusões sustentada, motor/projeção temporal não certificados.
  15 divergências numéricas preservadas (13 conhecidas + 2 antes mascaradas), auditadas
  individualmente no relatório; validar incidência/dados/outras exclusões antes de mudar gabarito.
- **Pagamento:** compra sem `pagamentoConfirmado` não gera crédito 2027 (citado: LC 214 art. 47). **[validar]**
- **Fornecedor do Simples:** `credito-fornecedor-simples=SEM_CREDITO` (conservador). **[validar]**
- **Calculadora oficial** (`CalculadoraOficialClient`, RTC) → no modo AUTO, se falhar usa a simplificada e marca
  `simulado`/aviso. NCM desconhecido pela calculadora → recalcula sem NCM com aviso.
- **Imposto Seletivo:** tabela `aliquotas-is.csv`; empresa `fabricante=true` paga no primeiro fornecimento (CST 000 /
  000001); revendedora usa CST 200 / 200007 (IS zero).
- Data do fato gerador fixa `2027-01-15` para todas as notas ("como se fossem de 2027").
- UI e API alertam que resultados são estimativas, não apuração definitiva; sugestões não
  aceitas continuam exigindo revisão. Não usar projeções/CSV para recolhimento real.
- RTC offline real app 1.5.4-082a5001/base V0059: 3 contratos aprovados neste ciclo em pasta
  temporária e loopback, só dados sintéticos. Isso não valida a base enviada pelo TribIA.

## 3. Pontos a validar com especialista (não resolvidos)

PENDENCIAS V1–V6 (medicamentos 200032 x 200009; vitamina C; detergente; água sanitária/papel toalha; resultado da
Distribuidora +333%; códigos agropecuários) e S1–S10, mais: base 2027 sem tributos (S5), redução CBS 0,1 p.p. (S4),
crédito Simples (S2), `pagamentoConfirmado` padrão `true`.

## 4. Inteligência Fiscal e "JEV AI"

- **Não implementada no backend.** Contrato proposto: `frontend/docs/inteligencia-fiscal-api.md`. Resultado esperado:
  NCM sugerido + fundamentação + alternativas + validação + fontes, processamento assíncrono com acompanhamento.
- Regras de produto já assumidas no front: nenhuma porcentagem inventada, pontuação do JEV exibida só como valor+escala
  com aviso de que não é probabilidade, "validado pelas verificações disponíveis" ≠ aprovação da Receita, fontes
  exibidas como vierem.
- **JEV:** só existe como campo opcional `pontuacao` no contrato. Não há documentação, cliente, chave ou prompt no
  repositório. **Informação não recuperada** — confirmar o que é, como se chama e se há API.
- `PLANO_CORRECOES.md` (Hermes) propõe um mock de JEV com notas fixas: **contraria** a regra "não substituir
  respostas reais por dados fictícios"; não adotar.
- Erros do `FLUXO_FISCAL.md` (Hermes) já identificados: Lei 12.973/2014 não é a TIPI (TIPI = Decreto 11.158/2022);
  Lei 12.741/2012 trata de transparência fiscal; Lei 13.988/2020 é transação tributária; LC 214 é de 2025. Não usar
  as referências legais desse arquivo sem conferir.

## 5. Dados oficiais embutidos

`backend/src/main/resources/dados-oficiais/` (`tabela-cclasstrib.csv` 144 linhas, `ncm-aplicavel.csv` 964,
`aliquotas-is.csv` 333), gerados por `backend/ferramentas/extrair_dados_oficiais.py`. Atualização dos dados:
**não verificada** nesta fase (versão/data da fonte não confirmadas).
