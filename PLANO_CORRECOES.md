# PLANO_CORRECOES.md

Plano priorizado de correções e implementações para TribIA.

## Princípios
- Trabalhar em incrementos pequenos, entregando valor verificável a cada passo.
- Validar cada mudança com testes manuais ou automatizados (quando possível).
- Manter a arquitetura multiempresa e a segurança de acesso em todas as novas funcionalidades.
- Não alterar regras fiscais existentes; apenas integrar serviços de apoio.
- Começar pelas correções que removem bloqueios de funcionamento core.

## Etapas

### 1. Criar serviço de análise fiscal (Inteligência Fiscal) – **Crítica**
**Objetivo:** Implementar os endpoints esperados pelo frontend (`/api/clientes/{clienteId}/analises-fiscais` etc.) e integrar Gemini (LLM) e JEV AI para classificação, validação e geração de relatório.

**Atividades:**
1.1. Criar entidade `AnaliseFiscal` (id, clienteId, status, dadosEntrada, anexos, resultado, criadaEm, atualizadaEm, etc.).
1.2. Criar repositório JPA `AnaliseFiscalRepository`.
1.3. Criar serviço `AnaliseFiscalService` com métodos:
   - `iniciarAnalise(Long clienteId, MercadoriaEntrada entrada, List<MultipartFile> arquivos)`: valida, salva entrada, dispara processamento assíncrono (via `@Async` ou fila de mensagens), retorna `AnaliseResumo`.
   - `listarAnalises(Long clienteId, FiltroAnalises filtro)`: paginação.
   - `indicadores(Long clienteId)`: contagens por status.
   - `detalharAnalise(Long id)`: busca com verificações de acesso.
   - (opcional) métodos admin para listar todas as análises.
1.4. Criar controlador `AnaliseFiscalController` expondo os endpoints, usando `AcessoService` para garantir que `clienteId` pertence ao usuário ou que o usuário seja admin.
1.5. Integrar cliente Gemini (reutilizando `LlmClient` existente ou adaptando) para receber prompt estruturado e retornar sugestão NCM, justificativa, confiança.
1.6. Integrar simulação de JEV AI (por enquanto pode ser um mock que retorna pontuação fixa; posteriormente substituir por chamada real).
1.7. Implementar validação fiscal automática (consulta a tabela NCM vigente, verifica restrições usando dados de CAMEX se disponíveis, verifica possibilidade de crédito CBS/IBS com base na legislação em transição).
1.8. Gerar relatório em PDF (usando biblioteca como iText ou Apache PDFBox) ou HTML e armazenar em disco ou S3; salvar URL no registro.
1.9. Cobrir com testes de integração (cenários de sucesso, falta de dados, IA indisponível).
1.10. Atualizar o frontend (se necessário) para consumir os novos endpoints (já estão prontos em `inteligenciaFiscal.ts`).

**Dependências:** Nenhuma além do código existente. Pode ser desenvolvido em paralelo com a etapa 2.

**Critério de conclusão:** Após fazer upload de uma nota e iniciar análise fiscal, o frontend exibe status "CONCLUIDA" com NCM sugerida, justificativa e botão de download funcionando.

### 2. Integrar chamada de classificação de notas – **Alta**
**Objetivo:** Fazer com que o sistema chame o endpoint de classificação existente (`POST /api/notas/{id}/classificar`) após upload de nota ou mediante ação do usuário, preenchendo o campo de classificação na nota.

**Atividades:**
2.1. Verificar se a classificação automática após upload é desejável. Se sim:
   - Modificar `NotaService.importar` ou criar um evento pós‑persist que chame `ClassificacaoService.classificar(notaId)` dentro da mesma transação ou em transação separada (para não bloquear upload).
   - Adicionar tratamento de exceções para não falhar o upload caso a classificação falhe.
2.2. Caso prefira ação do usuário:
   - Adicionar botão "Classificar nota" na tela `NotaDetalhe.tsx`.
   - Criar método em `api/tribia.ts` (ex.: `classificarNota(id)`) que chama `/api/notas/{id}/classificar`.
   - Conectar botão ao método e atualizar a tela após sucesso (refetch de `detalharNota`).
2.3. Atualizar UI para exibir data/hora da última classificação e permitir reclassificação.
2.4. Testar com notas de entrada e saída, verificando que CST/cClassTrib são preenchidos corretamente.

**Dependências:** Funcionalidade de classificação já existente no backend.

**Critério de conclusão:** Após upload de uma nota, ao abrir seu detalhe os campos CST e cClassTrib estão preenchidos (não aparecem como "Pendente").

### 3. Implementar indicadores de análise fiscal e exibir no painel da empresa – **Alta**
**Objetivo:** Mostrar no dashboard da empresa (InicioEmpresa.tsx) o número de análises em processamento, concluídas, aguardando revisão etc.

**Atividades:**
3.1. Criar endpoint `/api/clientes/{clienteId}/analises-fiscais/indicadores` (já planejado na etapa 1).
3.2. Adicionar chamada ao serviço `inteligenciaFiscal.ts` (já existe `indicadores`).
3.3. Em `InicioEmpresa.tsx`, usar o hook `useDados` ou criar novo hook `useAnalisesFiscalIndicadores` para buscar indicadores e exibir em cards KPI.
3.4. Layout: acrescentar cards ao lado dos existentes (Volume, Recentes etc.) ou em nova seção "Análise Fiscal".
3.5. Testar com diferentes status.

**Dependências:** Etapa 1.

**Critério de conclusão:** O dashboard da empresa exibe números corretos de análises por status.

### 4. Adicionar opções de recálculo, confirmação de pagamento e recálculo em lote – **Média**
**Objetivo:** Utilizar os endpoints de cálculo já existentes para melhorar a experiência de apuração tributária.

**Atividades:**
4.1. Recalculo de nota:
   - Adicionar botão "Recalcular" na tela `NotaDetalhe.tsx` (após classificação).
   - Criar método `calcularNota(id)` em `api/tribia.ts` que chama `/api/notas/{id}/calcular`.
   - Atualizar KPI de ICMS/PIS/Cofins após sucesso.
4.2. Confirmação de pagamento:
   - Em `NotaDetalhe.tsx`, quando a nota for de entrada (compra), mostrar checkbox "Pagamento confirmado" que chama `/api/notas/{id}/pagamento` via método `confirmarPagamento(id, boolean)`.
   - Atualizar o cálculo de crédito CBS/IBS (se aplicável).
4.3. Recalculo em lote:
   - Em `Empresa/ConfiguracoesEmpresa.tsx`, adicionar botão "Recalcular todas as notas" que chama `/api/clientes/{id}/calcular` (com opcional CBS).
   - Exibir mensagem de processamento e recarregar lista de notas após conclusão.
4.4. Garantir que essas ações apenas estejam disponíveis para usuários com permissão (geralmente admin da empresa).

**Dependências:** Endpoints de cálculo já existentes.

**Critério de conclusão:** Cada funcionalidade pode ser executada sem erro e reflete nos valores exibidos (ICMS, PIS/Cofins, crédito CBS/Ibs).

### 5. Ajustes menores de contrato de dados e UI – **Baixa**
**Objetivo:** Garantir que os DTOs entre frontend e backend estejam perfeitamente alinhados, evitando conversões desnecessárias.

**Atividades:**
5.1. Verificar tipo `Relatorio` em `frontend/src/api/types.ts` comparar com objeto retornado por `RelatorioController`. Se houver diferença, ajustar um dos lados (preferencialmente o backend para manter contrato estável usado por outros consumidores).
5.2. Revisar campos de `AnaliseResumo` e `AnaliseDetalhe` para garantir que nenhum seja omitido ou tenha tipo incompatível (ex.: datas como string ISO 8601).
5.3. Pequenos ajustes de texto ou layout nas telas de análise fiscal (ex.: mensagens de serviço indisponível já tratadas).
5.4. Executar build do frontend (`npm run build`) e garantir que não haja erros de tipo.
5.5. Rodar testes de integração básicos (via Postman ou script) nos novos endpoints.

**Dependências:** Nenhuma.

**Critério de conclusão:** Build do frontend passa e as telas funcionam sem erros de console relacionados a tipos ausentes.

### 6. Documentação e treinamento – **Baixa**
**Objetivo:** Deixar claro para usuários e administradores como usar as novas funcionalidades.

**Atividades:**
6.1. Atualizar `README.md` com visão geral do fluxo de análise fiscal.
6.2. Criar documento `USO_ANALISE_FISCAL.md` com passo a passo (upload de nota → classificação → inicia análise → acompanhamento → relatório).
6.3. Incluir treinar administradores sobre como acessar tela admin de análises (se implementada).
6.4. Garantir que os comentários no código estejam atualizados (Javadoc, comentários de serviço).

**Dependências:** Conclusão das etapas anteriores.

**Critério de conclusão:** Documentação disponível e compreensível por usuário final.

## Cronograma sugerido (semanas)

| Semana | Atividade principal |
|--------|---------------------|
| 1      | Etapa 1 – criação de entidade, repositório, serviço básico (sem IA). |
| 2      | Etapa 1 – integração Gemini, JEV mock, validação fiscal, geração de relatório. |
| 3      | Etapa 1 – controller, testes de integração, ajuste de contrato frontend. |
| 4      | Etapa 2 – implementar chamada de classificação (automática ou botão). |
| 5      | Etapa 3 – indicadores de análise fiscal e painel. |
| 6      | Etapa 4 – recálculo, confirmação pagamento, lote. |
| 7      | Etapa 5 – ajustes de contrato, build, testes finais. |
| 8      | Etapa 6 – documentação, revisão geral, entrega. |

*Cada semana pressupõe esforço de um desenvolvedor full‑time; pode ser paralelizado se houver mais de um recurso.*

## Métricas de sucesso
- Todas as telas de análise fiscal funcionam sem erros de 404/500.
- Após upload de nota, classificação aparece automaticamente (ou via botão) com CST/cClassTrib preenchidos.
- Dashboard da empresa exibe indicadores de análise fiscal corretos.
- Relatórios de análise fiscal podem ser baixados e contêm todas as informações esperadas.
- Nenhuma regressão nas funcionalidades existentes (login, upload de notas, geração de relatório tradicional).
- Código passa lint e testes de unidade existentes.
