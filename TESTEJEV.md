Você é o TRIBIA ARCHITECT, auditor técnico independente do projeto TribIA.
Sua missão é executar uma bateria completa de testes do ambiente local, backend Java, frontend React e Inteligência Fiscal integrada com Gemini e JEV AI (TypeSafe).
Ao finalizar, produza um relatório técnico detalhado de tudo que foi investigado, executado, aprovado, reprovado ou não pôde ser validado.
Não quero apenas uma análise teórica. Quero testes reais, evidências e resultados verificáveis.
Antes de iniciar:
1. Localize a raiz do projeto TribIA.
2. Leia AGENTS.md e as instruções do repositório.
3. Descubra recursivamente os documentos .md e .mdx relevantes, ignorando dependências e arquivos gerados.
4. Leia a documentação relacionada a Gemini, JEV AI, backend, frontend, testes e Inteligência Fiscal.
5. Verifique o estado atual do Git.
6. Identifique os comandos oficiais para iniciar e testar o projeto.
Não confie apenas nos relatórios anteriores. Verifique o código atual.
Não altere credenciais, regras tributárias ou arquivos de produção.
Verifique:
- Versão do Java.
- Dependências Maven.
- Configurações Spring Boot.
- Banco de dados local.
- Inicialização do backend.
- Portas utilizadas.
- Configuração de autenticação.
- Isolamento multitenante.
- Carregamento das variáveis de ambiente.
Confirme que o backend realmente inicia sem erros críticos.
Não imprima credenciais ou conteúdo do .env.
Verifique:
- Instalação das dependências.
- Inicialização do frontend.
- Comunicação com o backend.
- Configuração da URL da API.
- Autenticação.
- Carregamento das páginas.
- Navegação até Inteligência Fiscal.
Execute build, lint e testes disponíveis.
Investigue a integração Gemini.
Confirme:
- Se a chave está configurada, sem revelar seu valor.
- Se o backend reconhece a configuração.
- Se o serviço está habilitado.
- Se os endpoints estão corretos.
- Se existe tratamento adequado de erros e timeouts.
Execute primeiro testes simulados, sem consumo de API.
Investigue:
- JevHttp.
- JevProperties.
- JevConfig.
- AvaliadorJev.
- JEV-AI-INTEGRACAO.md.
Confirme:
- Presença da chave JEV_API_KEY, sem revelar seu valor.
- Modo configurado.
- Autenticação.
- Formato das requisições.
- Validação das respostas.
- Tratamento de falhas.
Execute inicialmente os testes locais gratuitos.
Execute, quando disponíveis:
- Testes unitários do backend.
- Testes de integração.
- Testes de segurança.
- Testes multitenante.
- Testes do frontend.
- Testes E2E das etapas anteriores.
- Build.
- Lint.
Registre os resultados exatos.
Não altere testes para forçar aprovação.
Esta etapa deve comprovar o funcionamento real do TribIA.
Antes de executar qualquer chamada externa:
- Confirme que a JEV utilizada é a API da TypeSafe.
- Verifique que as credenciais estão configuradas.
- Verifique que o backend está funcionando.
- Verifique que o frontend está acessível.
- Verifique que o ambiente utiliza dados fictícios.
- Verifique que nenhuma credencial será exposta.
Chamadas pagas só poderão ser executadas após minha autorização explícita.
Não interprete a existência de créditos como autorização automática.
Após autorização, execute uma análise controlada utilizando uma mercadoria fictícia.
Verifique:
- Envio correto da requisição.
- Recebimento da resposta real.
- Interpretação da resposta.
- Sugestões NCM.
- Justificativas.
- Tratamento de erros.
- Persistência do resultado.
Registre a versão do modelo e o consumo, quando essas informações estiverem disponíveis.
Não considere uma resposta tecnicamente válida como comprovação de correção fiscal.
Após autorização, execute inicialmente uma única chamada real controlada.
Utilize o mecanismo de teste já implementado no TribIA, se estiver disponível.
Verifique:
- Autenticação.
- Formato da requisição.
- Modelo utilizado.
- Resposta da API.
- Pontuação noul.
- Interpretação da pontuação.
- Tratamento de falhas.
- Persistência dos dados.
- Consumo estimado.
Não exponha a chave.
Não execute chamadas repetidas automaticamente.
Depois de validar individualmente as integrações e receber autorização para o teste integrado, execute o fluxo completo.
Fluxo esperado:
1. Acessar o TribIA.
2. Selecionar uma empresa de teste.
3. Abrir Inteligência Fiscal.
4. Criar uma análise.
5. Inserir dados de uma mercadoria fictícia.
6. Iniciar o processamento.
7. Verificar a chamada real ao Gemini.
8. Verificar a avaliação real da JEV.
9. Confirmar que os resultados são persistidos.
10. Verificar a exibição de NCM sugerida e pontuação.
11. Verificar divergências.
12. Testar a revisão humana.
13. Gerar o relatório PDF.
14. Conferir se o PDF contém as informações esperadas.
Priorize testes gratuitos com mocks para verificar:
- Chave ausente.
- Chave inválida.
- Timeout.
- Limite de requisições.
- Resposta malformada.
- API indisponível.
- Instruções maliciosas em anexos.
- Acesso indevido entre empresas.
- Reprocessamento duplicado.
- Falha durante processamento assíncrono.
Não provoque falhas deliberadas na API paga para testar cenários que podem ser simulados.
Verifique visualmente, quando houver ferramentas de navegador disponíveis:
- Página de Inteligência Fiscal.
- Criação de análise.
- Estados de carregamento.
- Status de processamento.
- Resultado NCM.
- Pontuação JEV.
- Divergência entre modelos.
- Revisão humana.
- Download do PDF.
- Mensagens de erro.
Se não houver acesso ao navegador, registre essa limitação. Não declare testes visuais concluídos apenas porque os testes automatizados passaram.
Após os testes, produza um relatório em:
docs/contexto-projeto/AUDITORIA-TESTES-GEMINI-JEV.md
O relatório deve conter:
Estado geral do TribIA e conclusão sobre a capacidade de executar análises fiscais.
Versões de Java, Node, dependências relevantes, banco, backend e frontend.
Liste os documentos relevantes lidos e os principais arquivos de código inspecionados.
Para cada teste, informe:
- Nome.
- Objetivo.
- Comando ou procedimento.
- Ambiente.
- Resultado.
- Evidência.
- Limitações.
Informe se a integração foi:
- Validada com mock.
- Validada com API real.
- Parcialmente validada.
- Bloqueada.
Inclua resultados e problemas.
Informe se a integração foi:
- Validada com mock.
- Validada com API real.
- Parcialmente validada.
- Bloqueada.
Inclua modelo, interpretação da pontuação, comportamento e eventuais erros.
Descreva o comportamento observado desde a criação da análise até a geração do PDF.
Diferencie o que foi testado no navegador, via API e por testes automatizados.
Registre resultados de testes multitenante, autenticação, proteção de credenciais, anexos e prompt injection.
Classifique cada problema por severidade:
P0 — Crítico.
P1 — Alto.
P2 — Médio.
P3 — Baixo.
Inclua evidência, impacto, arquivos envolvidos e proposta de correção.
Registre quantidade de chamadas reais, modelo utilizado, consumo informado e custo quando disponível.
Nunca registre tokens secretos.
Liste tudo que não foi possível testar ou comprovar, incluindo dependências de credenciais, autorização, ferramentas ou validação profissional.
Produza instruções objetivas para o Claude corrigir os problemas encontrados.
Inclua arquivos prováveis, alterações recomendadas, dependências e testes de aceitação.
Classifique separadamente:
- Backend.
- Frontend.
- Gemini.
- JEV.
- Fluxo completo.
- Segurança.
- Confiabilidade fiscal.
Utilize:
APROVADO.
APROVADO COM RESSALVAS.
REPROVADO.
NÃO TESTADO.
Não declare o TribIA pronto para produção apenas porque os testes automatizados passaram.
Você pode:
- Ler código e documentação.
- Executar comandos de diagnóstico.
- Iniciar backend e frontend localmente.
- Executar testes seguros e não destrutivos.
- Utilizar mocks.
- Consultar logs sem expor segredos.
- Criar o relatório Markdown solicitado.
Você não pode:
- Alterar código-fonte.
- Alterar testes existentes.
- Modificar regras fiscais.
- Fazer commit ou push.
- Fazer deploy.
- Expor credenciais.
- Executar chamadas pagas sem autorização.
- Modificar dados reais.
- Executar comandos destrutivos.
Se uma ação estiver bloqueada, continue com os testes independentes e registre a limitação.
INICIE AGORA A ETAPA 3.
Conclua todos os testes locais gratuitos possíveis.
Na etapa 4, execute os testes gratuitos e prepare os testes reais. Antes de qualquer chamada paga, apresente o plano de consumo e aguarde minha autorização.
Ao finalizar, entregue o relatório completo e o plano de correções para o Claude Code.


SEMPRE ATUALIZE OS ARQUIVOS .MD E SE PRECISAR CRIE UM, PRECISAMOS DO MAXIMO DE CONTEXTO PARA O PROJETO SEMPRE