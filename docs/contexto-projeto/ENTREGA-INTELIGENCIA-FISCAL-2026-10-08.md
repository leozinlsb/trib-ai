# Entrega — Inteligência Fiscal e integração (08/10/2026)

Ciclo pedido pela responsável a partir da auditoria do Hermes (fases A–K). Código no branch `dev/prataliyann-hue`,
**sem commit**. Etapa no plano mestre: 3 (Inteligência Fiscal) e complementos da 2 (integração).

## Resultado por fase

| Fase | Estado | Evidência |
|---|---|---|
| A. JEV AI | **Implementado, não validado externamente** | Adaptador para a API pública do Jev (TypeSafe), desligado por padrão; 13 testes com servidor simulado + 2 de config + 3 de fluxo. Nenhuma chamada real (custo; sem autorização). Premissa a confirmar: JEV AI = Jev da TypeSafe. Ver `docs/JEV-AI-INTEGRACAO.md` |
| B. NCM e vigência | **Implementado e testado** | Tabela oficial da NCM vigente (Portal Único Siscomex, "Vigente em 08/10/2026", Res. Gecex nº 926/2026, 15.157 registros, 10.516 de 8 dígitos) em `dados-oficiais/ncm-vigente.csv`; `TabelaNcmVigente` + `ValidadorNcm`; 6 testes unitários + 2 de fluxo |
| C. Relatório PDF | **Implementado e testado** | `GET /api/analises-fiscais/{id}/relatorio` (PDFBox 3.0.5, Apache 2.0); conteúdo lido de volta no teste; 409 sem resultado; 404 para outra empresa |
| D. Classificar nota no front | **Já existia** (Etapa 2, equipe) | Botão "Processar nota" e classificação após upload; coberto pelo E2E Etapa 4 (11/11) |
| E. Indicadores | **Implementado e testado** | Card no início da empresa; campos `falhas` e `informacoesInsuficientes` no endpoint; E2E |
| F. Fluxos existentes | **Implementado e testado** | Pagamento da compra (com confirmação), "Recalcular 2027" da nota, "Recalcular todas as notas"; E2E |
| G. Processamento persistente | **Implementado e testado (reinício simulado)** | Texto dos anexos gravado; reserva atômica; retomada na inicialização com limite de tentativas; 3 testes |
| H. Anexos | **Implementado e testado** | PDF, DOCX e XLSX lidos; assinatura conferida; zip-bomb e XXE bloqueados; 12 testes |
| I. Divergências S5 | **Investigado; valores coerentes com a regra; validação profissional pendente** | Ver abaixo |
| J. Testes | **Executados** | Ver abaixo |
| K. Documentação | **Atualizada** | Este arquivo, `JEV-AI-INTEGRACAO.md`, `HANDOFF.md`, `PENDENCIAS.md`, plano mestre, contrato da API |

## B. Validação da NCM — o que ela afirma e o que não afirma

- **Existe e vigora na data da análise** → OK, com data de início e ato legal (ex.: "desde 01/04/2022, Res Gecex nº 272/2021").
- **Só entra em vigor depois** → ALERTA e pendência.
- **Não consta da NCM vigente** → FALHA e INCONSISTENCIA → a análise vai para revisão. A fonte só traz códigos vigentes: ausente pode ser extinto **ou** inexistente, e o texto diz isso.
- A descrição do resultado passa a ser o **texto oficial pela hierarquia** (capítulo → subitem), não o texto da IA; se o código não consta, fica o da IA com a limitação dita.
- Fonte e versão aparecem nas fontes da análise e no PDF. Depois de 120 dias da extração, alerta de tabela possivelmente desatualizada (`tribia.fiscal.ncm-dias-validade-tabela`). Atualizar: `node ferramentas/atualizar_ncm.mjs`.
- **NCM não é TIPI**: não usamos a TIPI (alíquota de IPI) para dizer se o código existe. Validade da NCM não é enquadramento tributário.
- Não há histórico de alterações: datas anteriores a 2022-04-01 (NCM 2022) não são avaliadas com a tabela da época.

## G. Processamento assíncrono — decisão

Avaliado: classificação de notas é disparada pelo usuário, síncrona, uma transação por nota, idempotente (itens já
classificados não mudam). Não foi convertida em fila: não havia necessidade comprovada. As **análises fiscais** já eram
assíncronas, mas em memória (um reinício marcava tudo como FALHA e os anexos se perdiam). Solução escolhida, sem serviço
externo (Redis/Kafka): o próprio banco é a fila.

- Texto extraído dos anexos gravado na análise (`anexosLidosJson`); os arquivos não são guardados.
- `reservar`: `UPDATE ... WHERE status = AGUARDANDO` atômico; uma segunda execução não faz nada (sem duplicidade).
- `tentativas` por análise; na inicialização (`ApplicationReadyEvent`) as interrompidas voltam para a fila (histórico
  mostra a volta) até `tribia.fiscal.max-tentativas` (2); depois, FALHA com mensagem. Análises antigas com anexos e sem
  texto gravado viram FALHA (retomar sem os anexos mudaria o resultado sem avisar).
- Colunas novas anuláveis (`ddl-auto=update` só acrescenta). Nenhuma migração destrutiva.
- Retomar repete a chamada ao Gemini da análise interrompida (é a mesma análise pedida pelo usuário).
- Reinício real não foi exercitado de ponta a ponta: o estado interrompido é montado no teste e a retomada é chamada
  diretamente (o gatilho é o evento de inicialização do Spring).

## H. Anexos — o que é lido

| Formato | Leitura |
|---|---|
| .txt | UTF-8 ou Windows-1252; binário renomeado é recusado |
| .pdf | Texto das 30 primeiras páginas. Protegido por senha, corrompido ou digitalizado (sem texto): não lido, com motivo |
| .docx / .xlsx | Texto do documento / células das planilhas. StAX sem DTD (XXE), teto de 8 MB por parte e 30 MB no total, 2.000 entradas |
| .png .jpg .jpeg .webp | Aceitos e listados, **não lidos** (OCR não implementado) |
| .doc .xls (binários antigos) | Aceitos, não lidos; orienta salvar como .docx/.xlsx/PDF |

Conteúdo que não corresponde à extensão → 400 sem criar análise nem chamar a IA. Até 20.000 caracteres por anexo são
guardados; a IA recebe até 8.000 no total (a análise avisa quando corta).

## I. Divergências S5 — auditoria

Situação encontrada: a equipe aprovou a decisão S5 (base de 2027 sem ICMS, PIS e Cofins) e **atualizou os 15 valores
esperados** para os que o código produz; a suíte está verde. Não alterei fórmulas, alíquotas nem valores.

| # | Teste | Valor esperado atual | Conferência nesta entrega |
|---|---|---:|---|
| 1, 3 | ApuracaoController: nota com IBS/CBS no XML; recálculo do cliente | 54,07 | **Recalculado de forma independente a partir do XML**: (360 − 64,80 − 5,94 − 27,36) = 261,90 → 24,70 + 0,13 + 0,13 = 24,96; (420 − 75,60 − 6,93 − 31,92) = 305,55 → 28,81 + 0,15 + 0,15 = 29,11; total 54,07 |
| 2 | ApuracaoController: cenário CBS 8,8% | 50,50 | **Recalculado**: 23,31 + 27,19 = 50,50 |
| 15 | ApuracaoController: cálculo só do cache | 41,53 | Coerente com a regra; não recalculado individualmente |
| 4, 9, 13 | Dashboard/lista/roteiro demo: Distribuidora | 118,08 | Coerente; não recalculado individualmente |
| 5 | Dashboard: líquido por mês | 80,52 | Idem |
| 6, 14 | Dashboard top itens / CSV: refrigerante | 74,94 | Conferido pela fórmula registrada: (959,04 − 172,63) × 9,53% = 74,94 |
| 7 | Dashboard: Farmácia | 316,48 | Coerente; não recalculado individualmente |
| 8 | Dashboard: Loja | 396,55 | Idem |
| 10, 11, 12 | Revisão: correção, uso e consumo, item sem classificação | 33,47 / 64,89 / 13,92 | Idem |

**Conclusão técnica:** não encontrei erro de implementação. A aritmética confere com a regra nos casos recalculados.

**Conclusão fiscal (não resolvida, exige profissional):**
- **ICMS:** a exclusão tem base legal: LC 214/2025, art. 12, §2º, V ("o montante incidente na operação" de ICMS, ISS,
  PIS e Cofins, de 01/01/2026 a 31/12/2032). Texto conferido em fontes secundárias; o site do Planalto recusou a
  conexão nesta sessão.
- **PIS/Cofins:** em 2027 eles são extintos, então numa operação de 2027 **não haverá PIS/Cofins incidente** para
  excluir. Subtrair o PIS/Cofins destacado na nota de 2026 é uma **hipótese de projeção de preço** (o preço cairia sem
  eles), não uma regra legal da operação de 2027. A alternativa (manter o preço e excluir só o ICMS) dá base maior e
  imposto de 2027 maior. Isso muda todos os 15 números e o resultado das três empresas.
- **Mecanismos que já impedem apresentar como definitivo:** aviso permanente de estimativa no layout e nas respostas
  da API, rótulo "simulação" nos valores de 2027, `simulado=true` no cálculo, aviso no PDF e no roteiro da demo.
- **Proposta:** levar ao especialista a pergunta "na projeção de 2027, a base deve partir do preço com ou sem o
  PIS/Cofins de 2026?" e tornar o comportamento uma opção de configuração explícita depois da resposta.

## J. Testes executados (08/10/2026, máquina local, sem chaves reais)

| Execução | Resultado |
|---|---|
| Backend, suíte completa (ordem padrão) | **290 casos, 0 falhas, 0 erros, 7 ignorados** (antes: 246). Contagem pelos `<testcase>` dos relatórios, inclusive classes aninhadas |
| Backend, ordem inversa (`-Dsurefire.runOrder=reversealphabetical`) | 290 / 0 / 0 / 7 |
| Novos/alterados | TabelaNcmVigente 6, JevHttp 13, JevConfig 2, LeitorAnexos 12, AnaliseFiscalController 20 (era 10) |
| Frontend `npm run build`, `npm run lint`, `npm test` | verdes |
| E2E novo `frontend/scripts/etapa5-e2e.mjs` (Chrome headless, API isolada em memória, sem IA) | **7/7, 0 erros JavaScript** |
| E2E regressão `etapa4-e2e.mjs` | **11/11, 0 erros JavaScript** |
| `git diff --check` | limpo |
| Não executados | `inteligencia-fiscal-e2e.mjs` e `GeminiContratoTest` (usam o Gemini real, cobrado); chamada real à JEV; contratos da calculadora oficial (não estava no ar) |

Os 7 ignorados são os de sempre: 3 contratos da calculadora RTC, 2 do Gemini real e 2 geradores opt-in.

## Bloqueios externos

1. **JEV AI:** confirmar que é o Jev da TypeSafe; chave e autorização de custo para validar com uma chamada real.
2. **S5:** validação profissional da hipótese de PIS/Cofins na base projetada.
3. **NCM histórica:** a fonte pública só tem a tabela vigente; vigência de datas antigas exige o histórico de atos Gecex.
4. **OCR:** imagens não são lidas; exigiria biblioteca/serviço de OCR (decisão de custo e de precisão).

## Riscos remanescentes

- A pontuação da JEV, quando ativada, é exibida sem efeito na decisão; o significado fiscal de um limite não foi definido.
- A tabela NCM embarcada envelhece: o alerta de 120 dias depende de alguém rodar a ferramenta.
- Anexos com texto malicioso vão para a IA dentro do bloco de dados (já delimitado pelo `PesquisaNcmIa`); não há
  filtro semântico de injeção de prompt.
- A retomada após reinício chama o Gemini de novo (custo de uma análise).
