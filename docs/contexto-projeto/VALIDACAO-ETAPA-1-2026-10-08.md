# Validação da Etapa 1 — 08/10/2026

## Conclusão e limite da autorização

**Etapa 1 bloqueada para encerramento. Não iniciar a Etapa 2.**
As guardas de acesso direto B3 e o contrato B4 passaram na cobertura executada, mas um novo
teste comprovou vazamento indireto de justificativa manual entre empresas pelo cache global.
Não foi alterado código de produção neste ciclo; a correção dessa política precisa de aprovação.
As 22 falhas anteriores foram reproduzidas/classificadas; nenhuma expectativa antiga nem
regra fiscal foi alterada para tornar a suíte verde.

Criados `PLANO_MESTRE_TRIBIA.md` (exatamente quatro etapas) e `AGENTS.md`.
Planos antigos são referências históricas, não autorização para mudar de etapa.

## Ambiente e execução

- Windows, JDK 21.0.12.1 via JAVA_HOME; Maven Wrapper offline com dependências já disponíveis.
- Configuração de testes: H2 em memória exclusivo por contexto (UUID), create-drop;
  importação de application-local.properties desativada; senha ADMIN sintética.
- GEMINI_API_KEY vazio; IA e calculadora oficial substituídas por mocks nos novos testes HTTP.
  Calculadora simplificada observada por spy. Nenhum banco em backend/data foi usado.
- Novo teste HTTP inicia Tomcat em 127.0.0.1 e porta aleatória, com cookie jar por perfil,
  autenticação BCrypt real e cookie/header CSRF reais; encerra o contexto/servidor ao terminar.
- Nenhuma chamada paga, reset em banco real, commit/push ou mudança de produção neste ciclo.
- Browser inventory retornou apps=[]/browsers=[] e getBrowser(iab) informou indisponibilidade.
  A habilidade computer-use não pôde realizar ações visuais; HTTP real não substitui navegador.

| Execução | Total | Passaram | Falharam | Erros | Ignorados | Resultado |
|---|---:|---:|---:|---:|---:|---|
| Suíte inicial reproduzida, 04:38:45 -03 | 172 | 143 | 22 | 0 | 7 | Maven exit 1 |
| Segurança + novos HTTP, antes do teste de cache, 04:42:45 -03 | 41 | 41 | 0 | 0 | 0 | Maven exit 0 |
| Diagnóstico: base antiga + fallback desligado, 04:40:13 -03 | 32 | 30 | 2 | 0 | 0 | Maven exit 1; apenas 2 contratos restantes nessas classes |
| Diagnóstico: somente base antiga, 04:48:05 -03 | 32 | 28 | 4 | 0 | 0 | 13 divergências numéricas eliminadas; 2 fallback + 2 contratos permanecem |
| Regressão de cache isolada, 04:43:57 -03 | 1 | 0 | 1 | 0 | 0 | Vazamento reproduzido, Maven exit 1 |
| Suíte ampliada, 04:45:02 -03 | 178 | 148 | 23 | 0 | 7 | 22 falhas anteriores + 1 nova falha de segurança |
| Suíte completa confirmada novamente, 04:49:02 -03 | 178 | 148 | 23 | 0 | 7 | Mesmo resultado, sem flags fiscais; Maven exit 1 |
| Frontend build / lint | — | — | 0 | — | — | Ambos exit 0 |

Os 178 testes incluem os 5 HTTP novos e a nova regressão de cache. Não interpretar 23 como
23 regressões causadas por mudanças de produção: 22 já existiam, e a 23ª revela comportamento
preexistente antes não coberto. O teste novo permanece vermelho e não foi ignorado.
Contagens completas vêm do resumo Maven: não somar apenas XMLs, pois testes aninhados de
RegrasApuracaoTest têm particularidade de relatório já registrada em 07.

### Comandos reproduzíveis

Defina JAVA_HOME para seu JDK 21. Na pasta backend:

```powershell
$env:GEMINI_API_KEY = ''
.\mvnw.cmd -o test '-Dspring.config.import=' '-Dtribia.admin.senha=senha-apenas-dos-testes'
.\mvnw.cmd -o test '-Dspring.config.import=' '-Dtribia.admin.senha=senha-apenas-dos-testes' '-Dtest=FluxoHttpEtapa1Test,AcessoEmpresasTest,IsolamentoEmpresasTest,SeedRunnerTest,SeedRunnerAutorizacaoTest,ClienteControllerTest'
.\mvnw.cmd -o test '-Dspring.config.import=' '-Dtribia.admin.senha=senha-apenas-dos-testes' '-Dtest=IsolamentoEmpresasTest#justificativaManualPrivadaNaoDeveVazarPeloCacheGlobal'
# Apenas experimento causal; não é configuração de produção nem solução fiscal:
.\mvnw.cmd -o test '-Dspring.config.import=' '-Dtribia.admin.senha=senha-apenas-dos-testes' '-Dtest=ApuracaoControllerTest,ClassificacaoIaControllerTest,DashboardControllerTest,RevisaoControllerTest,RoteiroDemoTest' '-Dtribia.calculo.excluir-tributos-da-base=false' '-Dtribia.classificacao.fallback-regra=false'
```

O comando direcionado agora inclui a regressão de cache: espera-se falha até correção aprovada.
Na pasta frontend: `npm run build` e `npm run lint`.
O segundo experimento causal usa o mesmo comando das cinco classes, apenas removendo
`-Dtribia.classificacao.fallback-regra=false`; resultado 32 testes / 4 falhas.
Verificação final `git diff --check`: sem erros de whitespace (exit 0).

## Cobertura HTTP e integração efetivamente executada

`FluxoHttpEtapa1Test` contém 5 cenários, sem usuário/token artificiais no request:

1. Login válido/inválido, POST sem CSRF recusado, cookie JSESSIONID HttpOnly/SameSite=Lax,
   usuário da sessão, mutação sem CSRF recusada, logout e acesso pós-logout bloqueado.
2. ADMIN lista três empresas fictícias; cada perfil de empresa lista/seleciona somente a sua;
   ativo é booleano; upload multipart real das duas empresas, listagem/detalhe/relatórios/CSV;
   XML duplicado 409, inválido 422, upload sem CSRF 403, sem gravações inesperadas.
3. Acesso cruzado nas duas direções: dados/indicadores/revisão/relatórios/CSV, upload,
   classificação com/sem cálculo, cálculo individual/lote, pagamento e revisão de item.
   Empresa também não executa administração e demo/reset. Negativas 404/403.
4. Desativação lógica por ADMIN, ativo=false na lista, sessão previamente autenticada bloqueada,
   upload ADMIN na inativa 409, histórico preservado e reativação recupera acesso.
5. Servlet H2 real: GET/POST de console/login/query recusados a anônimo (401) e empresa (403);
   página do console acessível ao ADMIN (200). Não foi efetuado login JDBC no console.

Nas negativas, snapshots de oito tabelas (cliente, usuario, nota, item, classificacao,
calculo, classificacao_cache, registro_revisao) permanecem iguais e mocks/spies verificam
zero chamadas IA/calculadoras. Cobertura anterior continua exercitando serviços diretamente,
usuário removido com principal antigo, revisões de idênticos restritas ao cliente e fluxos
positivos de IA simulada/cálculo simplificado.

Isso comprova os caminhos testados, não ausência absoluta de falhas nem UX/console JavaScript.
Não foram testados visualmente seleção de arquivos, toasts, filtros, redirecionamentos ou
renderização de ativo/inativo. Sem navegador, não se afirma ausência de erros JS.

## Classificação das 22 falhas anteriores

| Grupo | Quantidade | Causa verificável | Classificação / decisão |
|---|---:|---|---|
| Base 2027 | 13 | Configuração excluir-tributos-da-base=true passa base2027(valor, ICMS, PIS, Cofins) ao cálculo; testes esperam valores com base cheia | Regra fiscal/expectativas precisam de validação; não concluir que resultado antigo ou novo está correto só por passar teste |
| Fallback por regra | 2 | fallback-regra=true cria sugestões REGRA de baixa confiança quando IA não resolve; testes esperam pendentes | Divergência de comportamento/contrato e teste anterior à mudança; validar política de produto e rastreabilidade |
| Dashboard IS | 1 | DashboardService separa sujeitoIs de porRegime | Contrato/teste desatualizado |
| CSV | 1 | RelatorioCsvService inclui Operação após Tipo | Contrato/teste desatualizado |
| XML inconsistente no teste | 1 | trocarCnpjs gera chave de nota 777 sem trocar nNF do XML; parser recusa 422 | Erro de fixture/teste; preservar validação da chave |
| Escopo de NF-e | 2 | NotaService aceita finNFe 1/2/4 e tpNF 0/1; testes ainda esperam rejeição | Teste anterior ao contrato; confirmar operação/estornos fiscais antes de tratar suporte como validado |
| Retry Gemini | 2 | GeminiClient repete modelo-a uma vez após 429/503; mock espera modelo-b imediatamente | Teste anterior ao contrato; não prova indisponibilidade real do Gemini |

Nenhuma das 22 falhas foi causada por 401/403 de autenticação na execução reproduzida.
Não foi comprovada falha de ambiente como causa dessas 22. Serviços externos indisponíveis
explicam testes ignorados, não os erros de asserção dos mocks.
O experimento com apenas excluir-tributos-da-base=false elimina os 13 erros numéricos e deixa
4 falhas (2 fallback, fixture da segunda nota e porRegime), sem desligar o fallback.
O experimento de 32 testes com as duas flags antigas elimina as 15 divergências de base/fallback;
restam somente fixture da segunda nota e porRegime nessas classes. É prova causal, não conserto.

### Inventário individual (nome exato do método)

| Classe / teste | Grupo |
|---|---|
| ApuracaoControllerTest.notaComIbsCbsNoXmlEhClassificadaPeloXmlECalculadaComoCredito | Base |
| ApuracaoControllerTest.cenarioDeCbsRecalculaEOValorInvalidoDa400 | Base |
| ApuracaoControllerTest.recalculaTodasAsNotasDoCliente | Base |
| ApuracaoControllerTest.cacheClassificaOQueConheceEOrestoFicaPendenteForaDoCalculo | Fallback |
| ClassificacaoIaControllerTest.iaForaDoArDeixaOsItensPendentesComAvisoEORestoDoFluxoSegue | Fallback |
| ClassificacaoIaControllerTest.segundaNotaComOsMesmosProdutosUsaOCacheEOQueJaFoiClassificadoNaoVoltaParaIA | Fixture |
| DashboardControllerTest.distribuidoraPagaMaisEm2027PeloRefrigeranteEPeloOleo | Base |
| DashboardControllerTest.impostoLiquidoPorMes | Base |
| DashboardControllerTest.farmaciaDoPresumidoGanhaCreditoEm2027ETemMedicamentosParaRevisar | Base |
| DashboardControllerTest.lojaFicaPraticamenteEstavel | Base |
| DashboardControllerTest.produtosQueMaisMudamOImpostoEFornecedoresQueMaisGeramCredito | Base |
| DashboardControllerTest.listaDeClientesTrazOsIndicadoresResumidos | Base |
| DashboardControllerTest.faturamentoPorRegime | Contrato dashboard |
| RevisaoControllerTest.corrigirGravaComoManualNoCacheValidadoERecalculaComANovaReducao | Base |
| RevisaoControllerTest.itemSemClassificacaoNaoPodeSerAceitoMasPodeSerCorrigido | Base |
| RevisaoControllerTest.usoEConsumoTiraOCreditoDaCompra | Base |
| RoteiroDemoTest.roteiroCompletoTresVezesSeguidasComOsMesmosNumeros | Base |
| RelatorioControllerTest.relatorioDoClienteParaExcelEmPortugues | Contrato CSV |
| NotaControllerTest.notaDeDevolucaoEComplementarSaoRejeitadas | Escopo NF-e |
| NotaControllerTest.notaComTpNfZeroERejeitada | Escopo NF-e |
| GeminiClientTest.sobrecargaNoPrimeiroModeloPassaParaOSegundo | Retry |
| GeminiClientTest.cotaEsgotadaETimeoutTambemPassamParaOProximo | Retry |

Exemplos reais: crédito 74,34 esperado / 54,07 obtido; cenário 69,42 / 50,50;
líquido Distribuidora 143,90 / 118,08; revisão 42,71 / 33,47.
Asserção inicial interrompe cada teste: depois de validar e corrigir uma expectativa,
outras divergências ainda podem aparecer. Não certificar todo o roteiro por um único assert.
Referências legais nos comentários não foram certificadas neste ciclo; revisão oficial/profissional
continua necessária, inclusive correspondência entre campos do XML e base implementada.

### Sete testes ignorados

- GeminiContratoTest: 2, exige GEMINI_API_KEY não vazio; não autorizado gastar cota.
- CalculadoraOficialContratoTest: 2, condição calculadoraNoAr em localhost:8080/api não satisfeita.
- SimplificadaVsOficialContratoTest: 1, depende da mesma calculadora local.
- GerarArquivosSeedTest: 1, exige seed.gerar=true; gera arquivos, não foi habilitado.
- GerarRespostasIaDemoTest: 1, exige seed.gerar=true e GEMINI_API_KEY; gera arquivos/chama IA.

## Novo bloqueador: B3-CACHE (erro real de implementação/política de isolamento)

Reprodução sintética, sem IA:

1. Duas empresas têm o mesmo produto (NCM + descrição), com item ainda pendente.
2. Usuário A corrige seu próprio item com justificativa INTERNO-EMPRESA-A-CONTRATO-SINTETICO.
3. RevisaoService.corrigir chama gravarNoCacheDoItem; ClassificacaoCache não tem proprietário.
4. Usuário B classifica sua própria nota; doCache consulta só findByChave.
5. GET da nota B devolve exatamente o marcador de A, origem CACHE e aceita=true.

Teste `IsolamentoEmpresasTest.justificativaManualPrivadaNaoDeveVazarPeloCacheGlobal` exige
ausência do marcador e falha com seu conteúdo literal. Não é acesso por ID estrangeiro:
é propagação de conteúdo privado e validação humana de uma empresa para outra.
Impacto: confidencialidade de texto livre e contaminação de decisões futuras entre empresas.
A arquitetura de cache global era intencional, mas autorização das rotas não resolve esse impacto.
O cache de IA também recebe justificativas: origem/proveniência/escopo precisam ser examinados,
embora a reprodução deste ciclo prove especificamente a justificativa MANUAL.

### Próxima correção proposta — ainda na Etapa 1, aguardando aprovação

- Separar cache privado aprendido por empresa de catálogo global explicitamente curado/público.
  MANUAL e aprendizado vinculado a documentos não devem compartilhar justificativa/validação
  indiscriminadamente. Não apenas ocultar o campo no DTO: isso manteria contaminação de decisões.
- Arquivos: model/ClassificacaoCache.java, repository/ClassificacaoCacheRepository.java,
  service/classificacao/ClassificacaoService.java, RevisaoService.java e ClassificacoesSeedLoader.java;
  testes de isolamento/cache/seed. Avaliar migração/índice apenas após aprovar a política.
- Preservar catálogo público, cache na mesma empresa, seed, precedência XML e CSRF.
  Entradas históricas sem proprietário não podem ser atribuídas arbitrariamente nem apagadas:
  definir migração conservadora e aprovação antes de tocar banco real.
- Critérios: regressão nova verde; A/B com mesmo produto não compartilham conteúdo/validação
  privados; classificação/aceitação/revisão/IA simulada não alteram cache da outra empresa;
  cache próprio e seed funcionam; negativas seguem sem escrita/chamada externa; sem regras fiscais alteradas.
- Depois: estabilizar contratos/fixtures com decisões documentadas, validar base/política de fallback,
  executar roteiro visual e reavaliar os critérios da Etapa 1.

## Roteiro visual manual pendente (ambiente novo e descartável)

Não usar o backend/data existente nem o ensaio-demo.ps1. Exemplo de ambiente isolado,
com portas livres 18090/15173, sem credenciais locais e sem IA externa:

```powershell
# terminal backend; JAVA_HOME configurado; só para esta sessão
$env:GEMINI_API_KEY = ''
.\mvnw.cmd -o spring-boot:run '-Dspring-boot.run.arguments=--server.address=127.0.0.1 --server.port=18090 --spring.datasource.url=jdbc:h2:mem:tribia-visual-etapa1 --spring.jpa.hibernate.ddl-auto=create-drop --spring.config.import= --tribia.admin.senha=VisualTeste1234 --tribia.llm.api-key= --tribia.calculo.modo=SIMPLIFICADA --tribia.seed.enabled=false --tribia.demo.habilitado=false'
# terminal frontend; só para esta sessão
$env:TRIBIA_BACKEND_URL = 'http://127.0.0.1:18090'
npm run dev -- --host 127.0.0.1 --port 15173 --strictPort
```

Esse roteiro não foi executado nesta sessão. Confirme no log jdbc:h2:mem e a porta antes
de qualquer upload; pare os dois processos ao terminar. A senha acima é somente fictícia.
Caso o comando não consiga iniciar, não relaxar segurança nem reutilizar banco real.

1. ADMIN entra em http://127.0.0.1:15173/login; conferir erro de senha incorreta,
   sucesso, F5 mantendo sessão, logout e impedimento de voltar às telas protegidas.
2. Em /dashboard/empresas, confirmar três fictícias ativas, filtros Ativas/Desativadas/Todas,
   seleção da empresa correta, indicadores e botão Enviar notas habilitado.
3. Criar dois acessos EMPRESA sintéticos (somente nesse banco) e entrar em perfis de navegador
   separados; cada um deve ver apenas sua empresa, sem administração nem dados alheios.
4. Em /dashboard/empresas/1/documentos, enviar
   backend/src/test/resources/nfe/nfe_teste_hackathon.xml; verificar progresso, sucesso,
   nota na lista e detalhe com oito itens. XML duplicado deve gerar erro 409 legível;
   inválido deve gerar 422 legível e não duplicar registros.
5. Para a empresa 2, usar backend/notas-demo-ao-vivo/2-farmacia_nf504.xml;
   conferir que não aparece nas notas da empresa 1.
6. Como ADMIN, desativar a empresa 2 pela UI: filtro/etiqueta Desativada e upload desabilitado;
   sessão já aberta da empresa 2 deve receber bloqueio sem dados alheios. Reativar e
   confirmar preservação das notas e recuperação do acesso.
7. Como EMPRESA, abrir URL de empresa/nota alheia diretamente; verificar redirecionamento ou
   estado de erro sem conteúdo de terceiros. Via ferramentas de rede, confirmar 404/403 na API.
8. Conferir ausência de erros inesperados no console do navegador, respostas 5xx e logs da API;
   testar serviço indisponível sem prometer sucesso ou deixar carregamento infinito.
9. B5 pode ainda mostrar itens processados como pendentes: registrar, não consertar neste ciclo.
   Não usar telas de Inteligência Fiscal como prova de backend implementado.

O fluxo visual de classificação/cálculo ainda não está conectado (Etapa 2).
O defeito de cache deve ser corrigido antes de liberar o sistema como multiempresa seguro.
Só após encerrar a Etapa 1 e aprovação, primeira tarefa da Etapa 2: contrato do detalhe/B5 e
classificação existente na UI, seguida de cálculo/resultados/relatórios, sem criar nova IA.
