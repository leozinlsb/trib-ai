# 07 — Handoff final: TribIA Public API v1

Data: 09/10/2026. Branch: `dev/prataliyann-hue`. Commit: `feat(api): implement TribIA public API v1` — o hash não
pode constar dentro do próprio commit; obtenha com `git log --oneline --grep "implement TribIA public API v1"`.
Sem push. Fora das quatro etapas do plano mestre (pedido explícito); nenhuma etapa mudou.

## 1. Resumo

O TribIA ganhou uma API REST pública (`/api/v1`) para ERPs e sistemas contábeis consumirem a Inteligência Fiscal
(sugestão de NCM com fundamentação, alternativas, verificação na NCM vigente e situação de revisão humana). Roda no
mesmo backend e reaproveita o motor existente: cada pedido cria uma `AnaliseFiscal` processada pelo mesmo
`ProcessadorAnaliseFiscal` (Gemini → JEV opcional → `ValidadorNcm`) e visível na plataforma para revisão humana.

## 2. Arquitetura

Cadeia de segurança própria para `/api/v1/**` (stateless, `X-API-Key`) → controller público → `ApiPublicaService`
(idempotência, cota, simultâneas, empresa da chave) → `AnaliseFiscalService.registrarNova/despachar` → fila existente.
Detalhes: [01](01-CONTEXTO-E-ARQUITETURA.md), decisões: [09](09-DECISOES-ARQUITETURAIS.md).

## 3–4. Arquivos

Criados: 22 classes em `backend/src/main/java/br/com/tribia/apipublica/`; 3 de teste em
`backend/src/test/java/br/com/tribia/apipublica/`; `exemplos/api-publica/` (cliente, emissor de chave, produto);
10 documentos nesta pasta. Modificados: `AnaliseFiscalService.java` (extração de métodos, mesmo comportamento),
`OpenApiConfig.java` (grupos), `application.properties` (`tribia.api-publica.*`), `HANDOFF.md`, `PENDENCIAS.md`,
`PLANO_MESTRE_TRIBIA.md` (nota fora das etapas), `README.md`, `00-LEIA-PRIMEIRO.md`, `02-ARQUITETURA.md`.
Lista completa e responsabilidades: [02](02-IMPLEMENTACAO.md).

## 5. Endpoints

`POST /api/v1/analises` (202), `GET /api/v1/analises/{id}`, `GET /api/v1/analises`, `GET /api/v1/uso`; gestão
interna de ADMIN em `/api/admin/chaves-api`. Contrato: [03](03-CONTRATOS-E-ENDPOINTS.md).

## 6–8. Autenticação, segurança, empresas

Chave `tribia_<prefixo>_<segredo>` (256 bits), guardada como SHA-256, exibida uma vez, revogável, com validade e
escopos; presa a uma empresa; sessão da plataforma não abre a API pública e a chave não abre rotas internas; id de outra
empresa = 404 idêntico a inexistente; negativas não gravam nem chamam IA. Detalhes e evidências: [04](04-SEGURANCA-E-MULTITENANCY.md).

## 9. Integração fiscal

Mesmo motor, sem regra fiscal alterada. Estados públicos mapeados da máquina existente (+`INFORMACOES_INSUFICIENTES`).
Resultado distingue `SUGESTAO_AUTOMATICA_VERIFICADA`, `SUGESTAO_AUTOMATICA_PENDENTE_REVISAO` e
`DECISAO_REVISAO_HUMANA`; pontuação da JEV exposta como compatibilidade, nunca probabilidade; avisos fixos.

## 10–12. Testes

| Execução | Resultado |
|---|---|
| Base (antes) | 318 casos, 0 falhas, 8 ignorados |
| Final, ordem padrão | 353 casos (Maven 352), **0 falhas, 0 erros**, 8 ignorados |
| Final, ordem inversa | Maven 352, 0 falhas, 0 erros, 8 ignorados |
| Novos | 35 aprovados (7 unitários + 28 de integração) |
| Reprovados | Nenhum no fim. Durante o trabalho, 1 regressão (teste existente contaminado por contexto compartilhado) — corrigida isolando o contexto dos testes novos, sem mexer no teste existente |
| Frontend `npm test` | 10/10 (front não alterado) |
| Ensaio ao vivo (servidor real, sem IA) | Todos os passos como esperado; chave ausente do log |

Detalhes: [05](05-TESTES-E-RESULTADOS.md).

## 13–14. Limitações e pendências

Não validado com Gemini real (custo); sem IA a análise termina `FALHOU` honestamente. Limite por minuto em memória;
tabelas via `ddl-auto=update`; sem anexos/webhooks/lote; sem tela de chaves. Pendências API-1…API-7:
[10](10-PENDENCIAS-E-EVOLUCAO.md) e `PENDENCIAS.md`.

## 15–16. Documentação e uso

Guia: [06](06-GUIA-DE-USO.md). Demo: [08](08-ROTEIRO-DEMO.md). Em resumo:

```bash
cd backend && ./mvnw spring-boot:run                     # ou o modo isolado do guia
export TRIBIA_ADMIN_SENHA="..."; export TRIBIA_API_URL=http://localhost:8090
export TRIBIA_API_KEY=$(node exemplos/api-publica/emitir-chave-local.mjs 1 "ERP Demo")
# Swagger: http://localhost:8090/swagger-ui.html → "API pública v1 (integradores)"
node exemplos/api-publica/cliente-tribia.mjs
```

## Próxima tarefa sugerida

Com autorização de custo, executar API-1 (uma análise real pela API pública com o Gemini) e decidir API-2 (API pública
no profile `prod`). Não há mudança de etapa do plano mestre a aprovar.
