# Etapa 1 — execução autônoma e validação (08/10/2026)

## Conclusão e limites

**CONCLUÍDA COM RESSALVAS no ambiente isolado e nos caminhos avaliados.** Contratos,
fallback, segurança e fluxos visuais de base verificados; não equivale a aprovação fiscal
ou liberação de produção. Etapa 2 **não iniciada**; depende de aprovação explícita.

Antes de usar dados reais: confirmar revogação da chave antiga (O1), avaliar histórico
potencialmente contaminado pelo cache (B3-HISTORICO) e validar os resultados tributários.
Não há evidência de ocorrência real de contaminação, nem evidência de revogação da chave.
Não tratar ausência de evidência como confirmação de segurança operacional.

## A. Ambiente, baseline e resultados reais

Contexto obrigatório consultado: AGENTS, plano mestre, documento central, HANDOFF,
PENDENCIAS e roadmap. Branch `dev/prataliyann-hue`, HEAD `ca65483`; mudanças anteriores
preservadas, sem commit/push. Baseline reproduzido sem flags fiscais: 195 testes,
167 aprovados, 21 falhas, 0 erros, 7 ignorados. Referência anterior: 63 direcionados verdes.

JDK 21.0.12.1, Maven Wrapper offline; testes com H2 em memória por UUID, importação local
desabilitada e senha ADMIN sintética. GEMINI_API_KEY vazio. Nenhuma chamada paga ou
operação no banco persistente `backend/data/`. Dados oficiais/rates/formulas/configuração
fiscal padrão **não alterados**. Reset existente do roteiro demo só ocorreu no H2 de teste;
nunca foi executado o script de ensaio ou um reset em banco real.

| Execução | Total | Aprovados | Falhas | Erros | Ignorados | Resultado |
|---|---:|---:|---:|---:|---:|---|
| Baseline reproduzido | 195 | 167 | 21 | 0 | 7 | Maven exit 1 |
| Direcionados: isolamento/cache/HTTP/contratos/fallback/clientes HTTP simulados | 104 | 104 | 0 | 0 | 0 | Maven exit 0 |
| AcessoEmpresasTest + ClienteControllerTest (não sobrepostos à seleção acima) | 16 | 16 | 0 | 0 | 0 | Maven exit 0 |
| Calculadora oficial offline real, dois contratos | 3 | 3 | 0 | 0 | 0 | Maven exit 0 |
| Experimento causal: somente os 15 testes fiscais, base antiga em H2 | 15 | 15 | 0 | 0 | 0 | Não é aprovação fiscal nem configuração recomendada |
| Completa após correções, sem calculadora iniciada | 220 | 198 | 15 | 0 | 7 | Maven exit 1 |
| **Completa final com RTC offline, configuração fiscal padrão, 06:57:36 -03** | **220** | **201** | **15** | **0** | **4** | **Maven exit 1: divergências fiscais preservadas** |
| Chrome/Playwright: fluxos E2E | 10 | 10 | 0 | 0 | 0 | Node exit 0; zero pageerrors nos três perfis |
| Frontend build / lint | — | — | 0 | — | — | Ambos exit 0 |
| git diff --check | — | — | 0 | — | — | Exit 0; avisos LF/CRLF não são falhas de whitespace |

Não somar rodadas sobrepostas. Suíte final inclui 28 IsolamentoEmpresas, 12 AcessoEmpresas,
5 FluxoHttpEtapa1, 19 NotaController, 9 GeminiClient, 5 ClassificacaoIaController e 13
ClassificadorPorRegra, todos aprovados. Contagem confrontada com Maven e nós `testcase` nos
XMLs; RegrasApuracaoTest relata 19 casos embora o atributo tests do XML externo seja zero.
Relatórios locais em `backend/target/surefire-reports/` (gerados, não versionados).

Falhas intermediárias da nova automação eram seletores ambíguos/nomes e verificação antes
de terminar o carregamento; corrigidas no harness, sem alterar o comportamento do produto.
Uma chamada direta de serviço no novo teste fiscal perdeu o contexto após MockMvc: corrigido
o contexto ADMIN sintético do teste com limpeza finally, sem relaxar autorização de produção.

## B. As seis falhas de contrato

| Caso original / arquivo em src/test/java/br/com/tribia | Causa comprovada | Correção e contrato preservado |
|---|---|---|
| controller/DashboardControllerTest.faturamentoPorRegime | Teste desatualizado: SUJEITO_IS não é regime, é recorte separado em sujeitoIs | Assert separado (959,04 e 1 item); demais valores preservados |
| controller/RelatorioControllerTest.relatorioDoClienteParaExcelEmPortugues | CSV já tinha Operação; cabeçalho esperado antigo | 37 colunas, VENDA/COMPRA, BOM UTF-8, decimal pt-BR, contagens; expectativas tributárias mantidas em teste separado |
| controller/NotaControllerTest.notaDeDevolucaoEComplementarSaoRejeitadas | Teste contrariava NotaService.validarEscopo e Operacao | finNFe 2/4 aceitas, operação/direção conferidas; acrescentadas negativas para finalidades fora do escopo |
| controller/NotaControllerTest.notaComTpNfZeroERejeitada | Teste contrariava suporte existente a entrada emitida pelo cliente | tpNF 0 aceito como ENTRADA/COMPRA; tpNF -1/2 negados e sem nota gravada |
| client/llm/GeminiClientTest.sobrecargaNoPrimeiroModeloPassaParaOSegundo | Expectativa não contemplava retry existente do modelo A | Duas respostas 503 de A antes da reserva B; recuperação no retry também coberta |
| client/llm/GeminiClientTest.cotaEsgotadaETimeoutTambemPassamParaOProximo | Idem para 429; timeout da reserva continua indisponibilidade | Retry de A, timeout de B e verify das chamadas HTTP simuladas |

Não foi preciso mudar a implementação desses contratos. finNFe 5/6 são **fora do escopo
atual do TribIA**, não afirmação de que sejam inválidas na especificação fiscal vigente.
Testes estruturais não certificam validade tributária das notas. Gemini mantém 401/403 sem
trocar modelos; 429/503 retry limitado, depois reserva; timeout/saída inválida com tratamento.
Comportamento coerente com a [orientação técnica do Google](https://ai.google.dev/gemini-api/docs/troubleshooting);
isso não comprova disponibilidade, credencial ou modelo real.

## C. Fallback corrigido pela causa

ClassificadorPorRegra antes inventava 000/000001 quando não encontrava regra e escolhia a
primeira regra quando havia associações divergentes. Agora retorna Optional vazio nesses
casos, para NCM malformado e quando só há códigos excluídos por dependência do adquirente.
Uma única associação permitida continua como **sugestão**, origem REGRA, confiança 0,40,
aceita=false, sem alimentar cache. Não foi criada alíquota, benefício ou classificação nova.

ClassificacaoService contabiliza somente sugestões presentes; sem evidência mantém item
pendente, sem cálculo para ele, com aviso de revisão manual. No XML hackathon sintético,
indisponibilidade da IA gera 1 sugestão (leite) e 7 pendentes. Com dois SEED autorizados:
2 CACHE + 1 REGRA, 5 pendentes. Recuperação da IA simulada preenche os restantes sem tocar
nos já classificados. Duas falhas anteriores resolvidas; 13 novos casos unitários cobrem
ausência, ambiguidade, NCM inválido, adquirente e várias linhas do mesmo código.

Risco identificado na tabela: arroz 10063021 e feijão 07133319 retornam mais de um código
permitido. CSV contém exceções distribuídas em linhas repetidas; loader avalia cada linha
isoladamente, o que pode permitir que outra linha reintroduza a associação excluída.
**Não mudar dados/semântica de exceções sem validar extração e base oficial**. Fallback
agora se abstém; lista/pistas da IA precisam dessa revisão na Etapa 3. A
[FAQ da Receita, itens 2.7/4.7](https://www.gov.br/receitafederal/pt-br/centrais-de-conteudo/publicacoes/apresentacoes/reforma-tributaria-do-consumo/faq-calculadora-v1-4.pdf/@@download/file)
reforça que mesma NCM pode ter tratamentos distintos, conforme a operação informada.

Medida de contenção: AppLayout mostra aviso permanente de estimativa/revisão, verificado
no E2E; CalculoService devolve aviso equivalente e remove a promessa genérica de equivalência
de todas as fórmulas no fallback AUTO. simulado/aceita/origem preservados. Relatórios não
devem servir para recolhimento ou obrigações reais até validação fiscal profissional.

## D. Auditoria individual das 13 falhas fiscais + 2 antes mascaradas

**Regra comum S5:** CalculoService envia `RegrasApuracao.base2027(valorDaOperacao, vICMS,
vPIS, vCOFINS, excluirTributos)` à calculadora; o padrão true subtrai valores destacados.
Testes antigos pressupõem base cheia. Parser lê vICMS do XML, não estima ICMS por alíquota.
Na entrada sintética: 780,00 − 140,40 − 72,15 = 567,45 antes do cálculo por item/arredondamento.
O experimento isolado trocando **somente** excluir-tributos-da-base para false aprovou os
15 testes abaixo; final voltou ao padrão true e preservou as 15 falhas. Não é solução fiscal.

Consulta do [texto compilado da LC 214/2025, art. 12](https://www.planalto.gov.br/ccivil_03/leis/lcp/lcp214compilado.htm)
confirmou exclusões, inclusive de ICMS/ISS/PIS/Cofins incidentes durante 2026–2032.
Isso sustenta a direção da exclusão, mas **não valida o motor inteiro**: a incidência efetiva
na operação projetada, a transição, os dados necessários e outras exclusões exigem revisão.
O código atual não recebe ISS nessa função; não confundir a lista legal com campos implementados.
As fixtures/seed são NF-e de 2026 projetadas na data fixa de 2027: revisar se vPIS/vCOFINS
da operação original podem ser reutilizados nesse cenário, conforme incidência no período
projetado. Não inferir que eliminar a falha com a flag antiga torna a projeção correta.
Também há IPI/IBS/CBS e condições no dispositivo que não podem ser resolvidos pela mera troca
dos valores esperados. Alíquotas, fórmulas, tabelas e todas as expectativas numéricas ficaram intactas.

Arquivos abaixo relativos a `backend/src/test/java/br/com/tribia/`. Valores em reais;
"atual" é o primeiro assert divergente na suíte padrão, não certificação do restante do teste.
Fundamento de eventual correção de cada linha: S5 + artigo consultado + experimento causal;
**nenhuma tem validação suficiente para aprovar novo gabarito fiscal nesta etapa**.

| # | Arquivo / método | Atual | Esperado | Regra/impacto para o usuário |
|---|---|---:|---:|---|
| 1 | controller/ApuracaoControllerTest.notaComIbsCbsNoXmlEhClassificadaPeloXmlECalculadaComoCredito | 54,07 | 74,34 | S5, soma de créditos de compra; risco de projetar crédito incorreto |
| 2 | controller/ApuracaoControllerTest.cenarioDeCbsRecalculaEOValorInvalidoDa400 | 50,50 | 69,42 | S5 com cenário CBS 8,8%; previsão do cenário divergente |
| 3 | controller/ApuracaoControllerTest.recalculaTodasAsNotasDoCliente | 54,07 | 74,34 | S5, lote agrega créditos; erro propagável ao cliente |
| 4 | controller/DashboardControllerTest.distribuidoraPagaMaisEm2027PeloRefrigeranteEPeloOleo | 118,08 | 143,90 | S5, débito menos crédito; decisão de caixa baseada em projeção |
| 5 | controller/DashboardControllerTest.impostoLiquidoPorMes | 80,52 | 103,86 | S5, líquido mensal; tendência mensal divergente |
| 6 | controller/DashboardControllerTest.produtosQueMaisMudamOImpostoEFornecedoresQueMaisGeramCredito | 74,94 | 91,40 | S5, valor do refrigerante; ranking/impacto por produto |
| 7 | controller/DashboardControllerTest.farmaciaDoPresumidoGanhaCreditoEm2027ETemMedicamentosParaRevisar | 316,48 | 419,29 | S5, crédito agregado no Presumido; não resolve validação de medicamentos |
| 8 | controller/DashboardControllerTest.lojaFicaPraticamenteEstavel | 396,55 | 545,10 | S5, líquido da loja; comparação de carga não certificada |
| 9 | controller/DashboardControllerTest.listaDeClientesTrazOsIndicadoresResumidos | 118,08 | 143,90 | S5, mesma agregação na lista; resumo também afetado |
| 10 | controller/RevisaoControllerTest.corrigirGravaComoManualNoCacheValidadoERecalculaComANovaReducao | 33,47 | 42,71 | S5 + redução existente após revisão; não prova falha do cache privado |
| 11 | controller/RevisaoControllerTest.usoEConsumoTiraOCreditoDaCompra | 64,89 | 89,20 | S5, créditos remanescentes após retirada; uso/consumo não muda a base dos demais |
| 12 | controller/RevisaoControllerTest.itemSemClassificacaoNaoPodeSerAceitoMasPodeSerCorrigido | 13,92 | 19,14 | S5 após correção manual do pendente; estimativa individual divergente |
| 13 | demo/RoteiroDemoTest.roteiroCompletoTresVezesSeguidasComOsMesmosNumeros | 118,08 | 143,90 | S5, roteiro falha no indicador inicial; não certificar todo o roteiro |
| 14 (antes mascarada) | controller/RelatorioControllerTest.comparativoFiscalDoCsvMantemReferenciaPendenteDeValidacao | 74,94 | 91,40 | S5, CSV preserva assert numérico que o cabeçalho antigo escondia |
| 15 (antes mascarada) | controller/ApuracaoControllerTest.calculoDoCacheSemIaMantemReferenciaFiscalAnterior | 41,53 | 57,09 | S5, cálculo cache-only preserva referência que o fallback escondia |

CSV também mostra resumo 2027 de 230,81/112,73/118,08 contra 298,85/154,95/143,90 antigos;
variação 255,45 contra 333,17. Asserções originais preservadas; falha inicial impede executar
todas na rodada padrão. Origem é divergência entre contrato fiscal do teste e base implementada,
não erro de ambiente/autorização. Revisão profissional decidirá se há erro de implementação,
fixture/projeção temporal ou expectativa antiga; não declarar simplesmente "teste desatualizado".

## E. E2E real e fronteiras verificadas

Skill computer-use consultada; inventário sem superfícies e ponte nativa indisponível.
Fallback técnico: Playwright 1.64.0 instalado só em pasta temporária, Chrome existente em
modo headless, contextos novos (ADMIN, empresa A e B), sem perfil real. Skill verification
orientou validar navegador → API → persistência → resposta, sem confundir HTTP com visual.
Harness versionável: `frontend/scripts/etapa1-e2e.mjs`, flag TRIBIA_E2E_ISOLADO obrigatória,
URL local fixa. Backend em 127.0.0.1:18990/H2 memória, Vite em 127.0.0.1:15173;
seed de notas, demo e console H2 desabilitados, API key vazia, cálculo simplificado.

Os 10 fluxos aprovados:

1. Login inválido, mensagem de erro e formulário desabilitado enquanto carrega.
2. Login ADMIN, listagem de três empresas ativas e aviso de limitação visível.
3. Seletor de empresa direciona para a empresa correta.
4. Upload XML pela UI, estado Processando, atualização e detalhe com oito itens persistidos.
5. XML malformado recusado, mensagem exibida, formulário recuperável.
6. Preparação de duas contas e nota B **por HTTP** (não alegar cadastro/upload B pela UI).
7. Login de ambos os perfis pela UI; negativas HTTP nos dois sentidos para lista, nota,
   CSV, classificação e cálculo; detalhe B idêntico antes/depois; URL alheia/admin redirecionada.
8. Desativação pela UI; sessão B já aberta recebe 403; dados preservados; upload desabilitado.
9. Reativação pela UI; sessão B recupera acesso à nota; upload habilitado e modal abre.
10. Logout pela UI; sessão ADMIN recebe 401; rota protegida redireciona ao login.

Capturas em `backend/target/etapa1-e2e/`: empresas-ativas, nota-a, empresa-a-protegida,
empresa-inativa e logout. Capturas inspecionadas, inclusive nota e upload desabilitado;
zero pageerrors inesperados nos três perfis. Falhas esperadas de XML/autenticação não são
erros inesperados. Não foi validado fluxo visual classificar/revisar/calcular: front não
o integra (B5/Etapa 2). Sem avaliação mobile completa ou E2E de todas as telas (Etapa 4).

## F. Cache, credenciais e procedimento histórico seguro

B3/B4/B3-CACHE continuam verdes: guardas em serviço/lote/consultas/exportações; snapshots
de oito tabelas e verifyNoInteractions nas negativas; sessão de usuário removido/inativo
negada; CSRF preservado; demo/console ADMIN. Cache privado deriva da empresa **da nota/item**,
não de parâmetro do front; namespace com SHA-256 de NCM/descrição. Catálogo compartilhado
somente SEED; legado IA/MANUAL/desconhecido sem dono ignorado e preservado. Não há nova
migração, limpeza, reatribuição ou mudança nas classificações históricas neste ciclo.

Limites: chave privada separa empresas, mas não inclui data, operação/destino/adquirente;
reuso dentro da empresa não certifica enquadramento fiscal universal (Etapa 3). Cache não
tem TTL/versionamento de tabela. Origem CACHE em nota histórica não identifica o registro
de origem que a alimentou: não é possível inferir automaticamente dono ou contaminação.

Plano **não executado** para B3-HISTORICO, somente após autorização:

1. Preservar cópia consistente do banco, com controle de acesso e identificação do snapshot;
   trabalhar em cópia isolada, nunca abrir banco de produção para alteração.
2. Somente SELECT/agregados: contar cache por namespace/fonte; contar classificações CACHE
   por empresa. Exemplo de triagem, sem justificativas ou dados pessoais no relatório:

   ```sql
   SELECT fonte, COUNT(*) FROM classificacao_cache
   WHERE chave NOT LIKE 'EMPRESA:v1:%' AND chave NOT LIKE 'CATALOGO:v1:%'
   GROUP BY fonte;
   SELECT n.cliente_id, COUNT(*) FROM classificacao c
   JOIN item i ON i.id = c.item_id JOIN nota n ON n.id = i.nota_id
   WHERE c.origem = 'CACHE' GROUP BY n.cliente_id;
   ```

3. Triagem não prova vazamento. Revisão restrita de proveniência/versão/logs, sem expor texto
   livre entre empresas; registro inconclusivo fica sinalizado, não automaticamente "corrigido".
4. Submeter eventual plano de remediação auditável e reversível a aprovação; revisão humana
   por dono, recálculo só com regras validadas. Não apagar caches/aceites em massa.

O1 permanece aberta: busca **só por nomes de arquivo** não encontrou padrão de chave Google
nos arquivos atualmente rastreados. application-local.properties e backend/data são ignorados.
Isso não audita todo o histórico e não comprova revogação. Nenhum segredo foi exibido/copiado,
e arquivos locais com credenciais não foram abertos. Responsável deve confirmar exclusão/
revogação no Google AI Studio/Cloud, registrar apenas data/projeto e evidência sanitizada,
e provisionar substituta fora do Git se necessária. Nunca "testar" a chave exposta chamando IA.

## G. Integrações externas

| Integração | Evidência | Limite |
|---|---|---|
| Gemini simulado | 9 testes do cliente + classificador/orquestração na suíte; 401/403, retry 429/503, timeout e resposta inválida | Não comprova API/modelos/credencial reais |
| Calculadora simulada | 6 client oficiais + 2 simplificados, respostas gravadas e erros | Mocks não equivalem a disponibilidade externa |
| RTC offline real | 3 contratos passaram; comparativo de todas as classificações do seed e NCM extinto | Não certifica base fiscal enviada pelo TribIA |
| Gemini real | 2 contratos ignorados, chave vazia, chamada pode consumir cota/custo | Exige chave revogada/substituída e autorização de consumo |
| Geradores | 2 ignorados: arquivos seed e respostas IA | Opt-in; regravam arquivos, segundo pode consumir IA; não executar neste escopo |

Distribuição pública RTC preparada em pasta **temporária nova** (não atualizada no Desktop):
app 1.5.4-082a5001, base V0059 de 30/09/2026, arquivo 87.675.639 bytes. Consulta pública de
versão/download, sem autenticação/custo de API; endpoint conforme ferramenta já existente
`backend/ferramentas/atualizar_calculadora.py`. Jar + fonte oficial inspecionados; profile
offline usa SQLite em leitura; API e métricas vinculadas a 127.0.0.1 (8080/18992).
Todos os dados enviados eram sintéticos. Nenhum CSV oficial do repositório foi reextraído.
Servidores temporários da tarefa encerrados após verificações; artefatos temporários preservados.

## H. Reprodução e continuidade

Na pasta backend, JAVA_HOME apontando para JDK 21:

```powershell
$env:GEMINI_API_KEY = ''
.\mvnw.cmd -o test '-Dspring.config.import=' '-Dtribia.admin.senha=senha-apenas-dos-testes'
# Contratos reais: só quando uma RTC offline própria e isolada estiver em localhost:8080:
.\mvnw.cmd -o test '-Dspring.config.import=' '-Dtribia.admin.senha=senha-apenas-dos-testes' '-Dtest=CalculadoraOficialContratoTest,SimplificadaVsOficialContratoTest'
```

Suíte normal sem RTC retorna 7 ignorados, não 4. Nunca use a flag de base antiga para afirmar
conclusão: foi somente experimento causal com H2; configuração final é a do repositório.

E2E exige iniciar backend novo **explicitamente em memória**, fora de configuração local:

```powershell
# Terminal backend, JDK 21:
$env:GEMINI_API_KEY = ''
.\mvnw.cmd -o spring-boot:run '-Dspring-boot.run.arguments=--server.address=127.0.0.1 --server.port=18990 --spring.config.import= --spring.datasource.url=jdbc:h2:mem:tribia-e2e-20261008;DB_CLOSE_DELAY=-1 --spring.jpa.hibernate.ddl-auto=create-drop --tribia.admin.senha=senha-apenas-e2e-2026 --tribia.seed.enabled=false --tribia.llm.api-key= --tribia.calculo.modo=SIMPLIFICADA --tribia.demo.habilitado=false --spring.h2.console.enabled=false'
# Outro terminal frontend:
$env:TRIBIA_BACKEND_URL = 'http://127.0.0.1:18990'
$env:VITE_API_URL = ''
npm run dev -- --host 127.0.0.1 --port 15173 --strictPort
# Terceiro terminal frontend, Playwright instalado fora do projeto:
$env:TRIBIA_E2E_ISOLADO = '1'
$env:TRIBIA_PLAYWRIGHT_MODULE = 'C:\caminho\temporario\node_modules\playwright'
node scripts/etapa1-e2e.mjs
npm run build
npm run lint
# Na raiz:
git diff --check
```

O harness não prova sozinho qual banco o servidor usa: confirme processo/configuração e
log H2 memória antes da flag. Ele cria contas e notas sintéticas; nunca aponte a dados reais.
Para RTC temporária: java -jar api-regime-geral.jar --spring.profiles.active=offline
--server.address=127.0.0.1 --server.port=8080 --management.server.address=127.0.0.1
--management.server.port=18992, na pasta temporária da distribuição (não na pasta do banco real).

**Arquivos alterados neste incremento:** ClassificadorPorRegra, ClassificacaoService,
CalculoService; testes ClassificadorPorRegra (novo), ClassificacaoIaController,
ApuracaoController, DashboardController, RelatorioController, NotaController, GeminiClient;
AppLayout, harness E2E (novo), README e documentos de continuidade/contexto. Guardas/cache
anteriores preservados. Nenhum commit/push, dados reais, nova feature fiscal ou Etapa 2.

**Próximo:** responsável confirma O1 e autoriza triagem histórica antes de uso real. Pode-se
propor início da **Etapa 2 em ambiente isolado**, após aprovação: B5, tipar/exibir
classificacao/calculo já persistidos no detalhe e distinguir pendência, sugestão/revisão,
simulação e ausência de resultado. Preservar avisos/isolamento, cobrir E2E; não apresentar
CSV/comparativos como definitivos. S5 e tabelas/exceções ficam na Etapa 3, com fontes e especialista.

## Verificação independente posterior (Claude Code, 08/10/2026)

Origem: reexecução própria, sem depender do relato do Codex. Sessão do Codex recuperada em log local
(`~/.codex/sessions/2026/10/08/`, tarefa "Compreenda o contexto do TribIA"); só mensagens de texto foram lidas, com
redação de segredos; `auth.json` e demais credenciais não foram abertos. O log confirma as 8 tarefas e que a última
("continue o que está fazendo", 10:10) terminou sem conteúdo nem alterações.

| Verificação | Resultado |
|---|---|
| Suíte backend completa | 220 testes, 15 falhas, 0 erros, **7 ignorados**, 0 respostas 401/403 (o Codex relatou 4 ignorados porque rodou com a calculadora RTC offline ativa; aqui ela não estava, então os contratos reais foram pulados) |
| As 15 falhas | as mesmas fiscais B6: Apuração 4, Dashboard 6, Revisão 3, Relatório 1, Roteiro demo 1 — valores esperados preservados |
| `npm run build` e `npm run lint` | exit 0 |
| `git diff --check` | sem erro de whitespace (só avisos LF/CRLF) |
| Autorização no código | centralizada em `AcessoService` (`notaAcessivel`, `itemAcessivel`, `clienteAcessivel`, `clientesVisiveis`) e chamada nos serviços de classificação, cálculo, revisão, painel, CSV, nota, relatório e demo/seed (ADMIN); `/h2-console` restrito a ADMIN |
| Segredos | nenhuma chave/senha no diff nem nos arquivos novos; `backend/data/` e `application-local.properties` ignorados pelo Git |
| Git | branch `dev/prataliyann-hue`, HEAD `ca65483`; 38 arquivos modificados e novos arquivos não commitados |

Não reexecutado por mim: os 10 fluxos Chrome (script em `frontend/scripts/etapa1-e2e.mjs`) e os 3 contratos RTC.
Os processos nas portas 8090 e 5173 continuam ativos; não foi comprovado se rodam código atual.

Ressalvas que dependem do responsável (inalteradas): O1 (confirmar revogação da chave), autorização para triar o
histórico do cache (B3-HISTORICO), validação fiscal das 15 divergências (S5, Etapa 3).
