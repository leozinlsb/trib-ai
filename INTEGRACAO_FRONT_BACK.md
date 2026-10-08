# INTEGRACAO_FRONT_BACK.md

Incompatibilidades e correções necessárias entre frontend e backend.

## 1. Endpoints esperados pelo frontend que não existem no backend

| Frontend (chamada) | Endpoint esperado | Backend existente? | Observações |
|---|---|---|---|
| `iniciarAnalise(clienteId, dados, arquivos)` | `POST /api/clientes/{clienteId}/analises-fiscais` | ❌ Não implementado | Frontend envia FormData com parte "dados" (JSON) e arquivos. Espera 202 com `AnaliseResumo`. |
| `listarAnalises(clienteId, filtro)` | `GET /api/clientes/{clienteId}/analises-fiscais` (com query params) | ❌ Não implementado | Retorna `Pagina<AnaliseResumo>`. |
| `indicadores(clienteId)` | `GET /api/clientes/{clienteId}/analises-fiscais/indicadores` | ❌ Não implementado | Retorna `IndicadoresFiscais`. |
| `detalharAnalise(id)` | `GET /api/analises-fiscais/{id}` | ❌ Não implementado | Retorna `AnaliseDetalhe`. |

**Correção:** Implementar controlador (ex.: `AnaliseFiscalController`) com esses endpoints, protegido por `AcessoService` para garantir multitenância. Serviço deverá orquestrar chamadas ao Gemini (LLM) e JEV AI, salvar análise em tabela, gerar relatório disparado assincronamente.

## 2. Endpoints existentes no backend que não são utilizados pelo frontend

| Backend endpoint | Uso esperado no frontend | Onde poderia ser integrado |
|---|---|---|
| `POST /api/notas/{id}/classificar` | Classificar itens da nota após upload ou mediante ação do usuário | Adicionar botão "Classificar" na tela de detalhamento da nota (`NotaDetalhe.tsx`) ou disparar automaticamente após upload exitoso (em `Documentos.tsx` após `enviarNotas`). |
| `POST /api/notas/{id}/calcular` | Recalcular CBS/IBS/IS de 2027 para itens já classificados | Adicionar opção "Recalcular" no painel ou no detalhamento da nota, talvez após classificação. |
| `PUT /api/notas/{id}/pagamento` | Confirmar ou não pagamento de compra (para efeito de crédito) | Incluir checkbox "Pagamento confirmado" no detalhamento da nota quando `tpNF` indicar compra. |
| `POST /api/clientes/{id}/calcular` | Recalcular lote de notas de um cliente (ex.: ao mudar cenário de CBS) | Adicionar nas configurações da empresa (`Empresa/ConfiguracoesEmpresa.tsx`) um botão "Recalcular todas as notas". |

**Correção:** Criar chamadas nos serviços frontend (ex.: em `api/tribia.ts` ou novos serviços) e conectar aos componentes UI adequados.

## 3. Inconsistências de contrato de dados

### 3.1. Resposta de upload de notas
- Frontend espera `UploadResultado` (de `tribia.ts`): `{ importadas: NotaResumoDto[], rejeitadas: UploadNotasDto.Rejeicao[] }`.
- Backend retorna exatamente isso (`UploadNotasDto`). ✅ Compatível.

### 3.2. Detalhamento de nota
- Frontend tipo `NotaDetalhe` (de `tribia.ts`) contém campos: `chave`, `valorTotal`, `valorProdutos`, `itens: ItemDto[]` onde `ItemDto` tem `ClassificacaoDto` e `CalculoDto`.
- Backend `NotaDetalheDto` fornece esses mesmos campos. ✅ Compatível.

### 3.3. Relatório
- Frontend `Relatorio` (de `tribia.ts`) provavelmente contém campos para download URL ou dados.
- Backend `RelatorioController` retorna `byte[]` como CSV ou objeto `RelatorioDto` (para `/api/clientes/{clienteId}/relatorio`). Frontend `gerarRelatorio` espera `Relatorio` (definido em `tribia.ts`). Precisa verificar se os campos batem. Olhando `tribia.ts` não mostra definição de `Relatorio`; está em `api/types.ts`. Vamos conferir rapidamente.

### 3.4. Tipos de análise fiscal
Frontend define muitos tipos (AnaliseResumo, AnaliseDetalhe, etc.) que devem ser replicados no backend como DTOs e entidades.

**Correção:** Criar DTOs e entidades no backend que correspondam exatamente aos tipos do frontend (ou usar um contrato comum via OpenAPI). Manter nomes de campos idênticos para evitar necessidade de mapeamento excessivo.

## 4. Multitenância e segurança

Todos os endpoints backend já utilizam `AcessoService` para verificar que o usuário só pode acessar recursos do próprio cliente (ou como admin). Os novos endpoints de análise fiscal devem seguir o mesmo padrão: incluir `clienteId` no path e checar acesso.

**Correção:** Ao implementar controladores de análise fiscal, reutilizar `AcessoService.clienteAcessivel(id)` ou semelhante.

## 5. Fluxo de trabalho esperado

1. Usuário faz login.
2. Seleciona empresa.
3. Envia notas XML (upload) → backend salva notas, retorna sucesso.
4. (Opcional) Usuário dispara classificação das notas recém‑importadas via endpoint `/api/notas/{id}/classificar`.
5. Usuário inicia análise fiscal de uma mercadoria (NovaAnalise) → backend cria análise, dispara processamento assíncrono (Gemini + JEV), retorna ID.
6. Frontend redireciona para tela de acompanhamento (`AnaliseFiscal.tsx`) que consulta periodicamente `/api/analises-fiscais/{id}` até status `CONCLUIDA` ou situação especial.
7. Ao concluir, frontend exibe NCM sugerida, fundamentação, validação fiscal e permite download do relatório.
8. Admin pode ver análises de todas as empresas via tela admin.

**Inconsistência atual:** Passo 4 (classificação) não é disparado; Passo 5‑7 (análise fiscal) não têm backend.

## 6. Resumo de ações necessárias

| Prioridade | Ação | Descrição |
|---|---|---|
| Crítica | Implementar serviço de análise fiscal (controlador, repositório, serviço) integrando Gemini e JEV AI. | Criar endpoints listados na seção 1. |
| Alta | Integrar chamada de classificação no fluxo de notas (após upload ou via botão). | Adicionar service method e UI. |
| Alta | Implementar endpoint de indicadores de análise fiscal e exibir no painel da empresa. |
| Média | Adicionar opções de recálculo, confirmação de pagamento e recálculo em lote nos locais adequados. |
| Baixa | Ajustar tipos de dados se houver divergência menor (verificar contrato de relatório). |
| Baixa | Manter páginas estáticas (landing, exemplo) como estão. |
