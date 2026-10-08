# AUDITORIA_TRIBIA.md
# Diagnóstico Completo do Projeto TribIA
# TRIBIA ARCHITECT
# Data: 08/10/2026

## 1. Estado Real do TribIA

### 1.1. Arquitetura e Tecnologias
- **Backend**: Java 21, Spring Boot 3, Spring Security (sessão em cookie HttpOnly + CSRF), H2 (arquivo em `backend/data/tribia`), Maven Wrapper.
- **Frontend**: React 19 + TypeScript, Vite, CSS puro, comunicação via `fetch` nativo com suporte a CSRF (header `X-XSRF-TOKEN`).
- **Integração**: Vite proxy `/api` para `localhost:8090`.
- **IA**: Google Gemini API (`gemini-3.5-flash`) via `LlmClient`.
- **Banco de Dados**: H2 em arquivo (persistente entre reinícios do backend).

### 1.2. Funcionalidades Core Funcionando (verificadas via código e documentação)
✅ Autenticação (login/logout) - endpoints em `AuthController`.
✅ Gestão de clientes (empresas) - CRUD em `ClienteController`.
✅ Gestão de usuários por empresa - endpoints em `UsuarioController`.
✅ Upload de notas fiscais (XML) - `NotaController.upload`.
✅ Listagem de notas - `NotaController.listar`.
✅ Detalhamento de nota - `NotaController.detalhar`.
✅ Geração de relatório CSV/Excel - `RelatorioController.gerar` e endpoints de export.
✅ Classificação de notas (CST + cClassTrib) - `ApuracaoController.classificar`.
✅ Cálculo de impostos (hoje vs 2027) - `ApuracaoController.calcular`.
✅ Confirmação de pagamento - `ApuracaoController.pagamento`.
✅ Recalculo em lote de notas - `ApuracaoController.calcularCliente`.
✅ Revisão manual de classificações - `RevisaoController`.
✅ Dashboard com indicadores - `DashboardController`.
✅ Tela de demonstração (profile `demo`) - `DemoController`.

### 1.3. Funcionalidades Parcialmente Implementadas ou com Problemas
⚠️ **Testes do Backend com Falha de Autenticação**: ~40 testes falham com 401/403 após adição do Spring Security. Os testes existentes não têm autenticação mockada. Classes afetadas: `ClienteControllerTest`, `NotaControllerTest`, `RelatorioControllerTest`, `RevisaoControllerTest`, `DashboardControllerTest`, `ApuracaoControllerTest`, `AcessoEmpresasTest` (este último já possui autenticação via `@WithUserDetails` e `.with(user(...))`).

⚠️ **Funcionalidade de Inteligência Fiscal (Análise Fiscal)**: O frontend possui telas completas (`frontend/src/pages/fiscal/`) e contrato de API definido (`frontend/src/api/inteligenciaFiscal.ts` e `frontend/docs/inteligencia-fiscal-api.md`), mas **nenhum endpoint existe no backend**. Não há classes, repositórios ou serviços relacionados a `analise`, `inteligencia` ou `fiscal` no backend.

⚠️ **Integração de Classificação de Notas Não Utilizada**: O endpoint `POST /api/notas/{id}/classificar` existe em `ApuracaoController`, mas **não é chamado pelo frontend** após upload ou via botão. Conforme `MAPA_FUNCIONAL.md` linha 14 e `INTEGRACAO_FRONT_BACK.md` seção 2.

⚠️ **Endpoints de Cálculo Não Utilizados**: `POST /api/notas/{id}/calcular`, `PUT /api/notas/{id}/pagamento`, `POST /api/clientes/{id}/calcular` existem, mas não são utilizados pelo frontend (exceto possivelmente em fluxos de demonstração não verificados).

### 1.4. Funcionalidades Ausentes ou Não Verificadas
- Endpoints de Inteligência Fiscal: `POST /api/clientes/{clienteId}/analises-fiscais`, `GET /api/clientes/{clienteId}/analises-fiscais`, `GET /api/clientes/{clienteId}/analises-fiscais/indicadores`, `GET /api/analises-fiscais/{id}`, `GET /api/analises-fiscais/{id}/relatorio`.
- Integração com JEV AI (apenas mencionada no plano, não implementada).
- Geração de relatório de análise fiscal (PDF/HTML).
- Tela administrativa de análise fiscal (`InteligenciaFiscalAdmin.tsx` no frontend, mas sem backend).
- Fluxo completo de análise fiscal com processamento assíncrono, polling, histórico, alternativas, fundamentação, validação fiscal.
- Cache global de classificações fiscais (menção no `FLUXO_FISCAL.md`, mas não evidenciado no código).

### 1.5. Evidências de Isolamento Multitenante e Segurança
✅ Uso de `AcessoService` em todos os controllers para verificar acesso a recursos por `clienteId`.
✅ Testes em `AcessoEmpresasTest` confirmam que usuários de uma empresa não podem acessar dados de outra empresa (retornam 404).
✅ Spring Security protege todas as rotas `/api/**`.
✅ Cookies de sessão HttpOnly e proteção CSRF presentes.

### 1.6. Problemas de Desempenho e Experiência do Usuario (limitados à inspeção de código)
- T1: H2 em arquivo, mas dados não seed (uploads, revisões, cache da IA) se perdem ao reiniciar (documentado em `PENDENCIAS.md` T1).
- T2: Chamada à IA dentro da transação do banco (alguns segundos) - pode causar bloqueio.
- T3: `POST /api/clientes/{id}/calcular` recalcula todas as notas numa única transação - lento para clientes com muitas notas.
- T4: Painel agrega em memória a cada requisição - ok para demo, mas com volume real precisaria de pré-agregação.
- T5: IA sem espera progressiva (backoff) quando a cota estoura.
- T6: Respostas gravadas da IA (profile `demo`) cobrem só os produtos das notas em `notas-demo-ao-vivo/`.
- T7: Validação de chave de acesso só verifica dígito verificador.
- T8: CORS liberado para qualquer origem (fora do escopo do MVP).
- T9-T18: Diversas limitações de revisão, agrupamento, limites fixos, etc., documentadas em `PENDENCIAS.md`.

## 2. O Que Já Funciona de Ponta a Ponta
1. Login → seleção de empresa → upload de XML de NF-e → visualização da nota no detalhe (com itens, mas sem classificação CST/cClassTrib preenchidos).
2. Login → gestão de empresas (listar, criar, editar, desativar, reativar).
3. Login → gestão de usuários por empresa (listar, criar, remover).
4. Login → geração de relatório CSV/Excel da empresa (com dados de notas já importadas).
5. Login → acesso ao dashboard com indicadores de faturamento, liquidez, top itens, top fornecedores (baseado nas notas já importadas e classificadas via seed ou classificação manual via API direta? Observação: o dashboard usa dados do seed que já vem classificado e calculado na inicialização).

## 3. O Que Parece Funcionar, Mas Não Funciona
1. **Classificação automática após upload**: O frontend tem tela de upload (`Documentos.tsx`) que chama `enviarNotas` (via `tribia.ts`), mas não dispara a classificação via `/api/notas/{id}/classificar`. O usuário vê os itens como "Pendente" no detalhe da nota até que a classificação seja feita manualmente (se houver um botão, mas não há).
2. **Telas de Inteligência Fiscal**: As telas (`InteligenciaFiscal.tsx`, `NovaAnalise.tsx`, `AnaliseFiscal.tsx`, `ExemploAnalise.tsx`) renderizam, mas ao tentar iniciar uma análise, o frontend chama `iniciarAnalise` (em `inteligenciaFiscal.ts`) que recebe 404 (rota inexistente) e exibe "Análise fiscal ainda não disponível".
3. **Indicadores de análise fiscal no dashboard**: O componente `InicioEmpresa.tsx` possui espaço para cards, mas não há chamada para `/api/clientes/{clienteId}/analises-fiscais/indicadores` (endpoint inexistente).
4. **Botões de recálculo, confirmação de pagamento e lote**: Não existem no frontend, embora os endpoints existam no backend.

## 4. Funcionalidades que Não Fazem Sentido ou Precisam ser Repensadas
Nenhuma identificada como não fazendo sentido. Porém, algumas simplificações tributárias documentadas em `PENDENCIAS.md` (S1-S10) devem ser comunicadas aos usuários como limitações conhecidas, pois podem afetar a precisão da apuração de créditos CBS/IBS em certos cenários (ex.: S1: todo imposto destacado na compra considerado pago, sem condicionar à extinção do débito; S8: crédito de PIS/Cofins hoje pela alíquota do comprador, não pelo destacado pelo fornecedor). Isso é aceitável para um MVP, mas deve ser destacado.

## 5. Riscos Fiscais Existentes
1. **Dependência de sugestão de IA sem revisão humana obrigatória**: O sistema sugere NCM via Gemini, mas não exige que um contador valide antes de usar a classificação em operações fiscais (emissão de NF-e, apuração de tributos). Conforme `FLUXO_FISCAL.md` seção 3.5: "O TribIA não deve substituir a responsabilidade do classificador profissional, mas auxiliar na coleta de informações, sugestão de NCM fundamentada e geração de relatório de apoio." Porém, no estado atual, não há mecanismo para forçar ou mesmo incentivar a revisão humana.
2. **Falta de rastreabilidade e auditoria de classificações**: Não há registro de quem classificou, quando, e quais alterações foram feitas (além da data da última alteração na revisão, conforme `PENDENCIAS.md` T10).
3. **Uso de respostas gravadas da IA em modo demo sem aviso claro ao usuário**: Quando o perfil `demo` está ativo, o sistema usa respostas pré-gravadas da IA, mas o usuário pode não perceber que está vendo dados simulados e não uma classificação real.
4. **Limitações na validação fiscal automática**: O plano menciona validação de vigência do NCM, restrições de Licença Importação/Exportação, conformidade com alíquota de IPI, possibilidade de crédito CBS/IBS, mas nenhuma implementação existe ainda. Quando implementada, deve-se ter cuidado para não apresentar validação como garantia absoluta.
5. **Confusão entre classificação fiscal (NCM) e classificação tributária (CST + cClassTrib)**: O sistema atualmente classifica notas com CST e cClassTrib (regime de 2027), mas a Inteligência Fiscal visa sugerir NCM. É necessário garantir que o usuário entenda a diferença e que a sugestão de NCM seja usada para determinar o CST/cClassTrib adequado, e não como substituto direto.

## 6. O Que Falta para Transformar o Projeto em um SaaS Utilizável
1. **Implementar o serviço de Inteligência Fiscal (Análise Fiscal)**: criar endpoints, integrar Gemini e JEV AI, gerar relatórios, permitir download.
2. **Integrar a classificação de notas no fluxo de upload ou via botão**: para que os campos CST e cClassTrib sejam preenchidos automaticamente ou mediante ação do usuário.
3. **Exibir indicadores de análise fiscal no dashboard da empresa**.
4. **Adicionar opções de recálculo, confirmação de pagamento e recálculo em lote na UI**.
5. **Corrigir os testes do backend** adicionando autenticação mockada para que o pipeline de CI/CD funcione.
6. **Garantir que o isolamento multitenante e a segurança sejam mantidos nas novas funcionalidades**.
7. **Atualizar a documentação e treinar usuários** sobre o uso das novas funcionalidades e suas limitações.

## 7. Primeiras Cinco Tarefas que o Claude Code Deveria Executar
1. **Corrigir os testes do backend**: adicionar `@WithMockUser` ou `SecurityMockMvcRequestPostProcessors` nas classes de teste que estão falhando (ex.: `ClienteControllerTest`, `NotaControllerTest`, `RelatorioControllerTest`, `RevisaoControllerTest`, `DashboardControllerTest`, `ApuracaoControllerTest`). Isso remove o bloqueio crítico e permite que o desenvolvimento prossiga com confiança nos testes.
2. **Começar a implementação do serviço de Inteligência Fiscal**: criar a entidade `AnaliseFiscal`, repositório JPA, serviço básico (sem IA ainda) e controlador com os endpoints `POST /api/clientes/{clienteId}/analises-fiscais`, `GET /api/clientes/{clienteId}/analises-fiscais`, `GET /api/clientes/{clienteId}/analises-fiscais/indicadores`, `GET /api/analises-fiscais/{id}` (e opcionalmente o de relatório). Garantir multitenância com `AcessoService`.
3. **Integrar a chamada de classificação de notas**: escolher entre disparar automaticamente após upload (em `NotaService.importar`) ou adicionar botão "Classificar nota" na tela `NotaDetalhe.tsx`. Implementar a chamada ao endpoint existente `/api/notas/{id}/classificar` e atualizar a UI após sucesso.
4. **Implementar o endpoint de indicadores de análise fiscal** e conectar ao dashboard da empresa (`InicioEmpresa.tsx`) para exibir cards com número de análises por status (total, concluidas, emProcessamento, aguardandoRevisao).
5. **Adicionar opções de recálculo, confirmação de pagamento e recálculo em lote nos locais adequados**: botão "Recalcular" em `NotaDetalhe.tsx`, checkbox "Pagamento confirmado" para notas de entrada, e botão "Recalcular todas as notas" em `Empresa/ConfiguracoesEmpresa.tsx`. Utilizar os endpoints existentes do backend.

## 8. Conclusão
O TribIA possui um núcleo forte de funcionalidades de gestão de empresas, upload e visualização de notas fiscais, classificação e cálculo de impostos (CST/cClassTrib e CBS/IBS/IS), relatórios e dashboard. No entanto, a principal proposta de valor — a Inteligência Fiscal para classificação NCM com IA e geração de relatório fundamentado — está completamente ausente do backend, apesar do frontend estar pronto para consumi-la. Além disso, funcionalidades já existentes como classificação de notas e cálculos não estão sendo utilizadas pelo frontend, gerando uma experiência incompleta para o usuário.

Os testes do backend estão quebrados devido à falta de autenticação nos testes, o que impede a validação automática de mudanças. A multitenância e a segurança parecem estar corretamente implementadas.

Para que o TribIA se torne um SaaS utilizável, é necessário focar na implementação da Inteligência Fiscal, na integração das funcionalidades existentes no fluxo do usuário e na correção dos testes. Após isso, o sistema poderá entregar valor real a contadores e empresas que desejam entender o impacto da Reforma Tributária em seus produtos.
