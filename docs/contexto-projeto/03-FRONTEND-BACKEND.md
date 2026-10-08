# 03 — Integração frontend ↔ backend e contratos

Etapa vigente: **1, bloqueada por B3-CACHE**; não implementar integrações da Etapa 2 ainda.
Autorização direta nos serviços está testada, mas cache global pode levar justificativa privada
ao detalhe autorizado de outra empresa. Ver [validação](VALIDACAO-ETAPA-1-2026-10-08.md).

## Como se comunicam

- O navegador chama caminhos relativos `/api/...`; em dev o Vite (`frontend/vite.config.ts`) faz proxy para
  `http://localhost:8090`. Cookie de sessão HttpOnly (`credentials: 'same-origin'`).
- CSRF: `frontend/src/api/client.ts` lê o cookie `XSRF-TOKEN` e envia `X-XSRF-TOKEN` nas requisições que alteram
  dados; antes do primeiro POST chama `GET /api/auth/csrf`.
- Erros: backend devolve `ProblemDetail` (`title`, `detail`, `status`); o front mostra `detail`. Sessão expirada
  dispara o evento `tribia:sessao-expirada`. Para a Inteligência Fiscal, o título `Recurso não encontrado`
  distingue "análise inexistente" de "rota inexistente".
- Tipos do front: `frontend/src/api/types.ts` (espelha os DTOs de `backend/.../dto/`).

## Endpoints do backend: uso pelo front e verificação de empresa

"Acesso" = política central aplicada no serviço (P0.1), além de guardas preexistentes nos controllers.
Verificado por testes HTTP e chamadas diretas aos serviços com duas empresas; ver [07](07-TESTES-EXECUCAO.md).

| Endpoint | Front usa? | Acesso | Observação |
|---|---|---|---|
| `POST /api/auth/login`, `GET /api/auth/me`, `GET /api/auth/csrf`, `POST /api/auth/logout` | sim | públicos (login/csrf/me) | logout é do Spring Security |
| `GET /api/clientes` | sim | sim, só visíveis | `ClienteListaDto` inclui `ativo` booleano (B3/B4 corrigidos) |
| `GET /api/clientes/{id}` | sim | sim | |
| `POST /api/clientes`, `PUT/DELETE /api/clientes/{id}`, `POST .../reativar` | sim | admin | |
| `GET/POST /api/clientes/{id}/usuarios`, `DELETE /api/usuarios/{id}` | sim | admin | |
| `GET /api/clientes/{id}/dashboard` | não | sim | painel hoje x 2027 |
| `POST /api/clientes/{id}/notas` (multipart `arquivos`) | sim | sim (+ 409 se desativada) | não classifica |
| `GET /api/clientes/{id}/notas` | sim | sim | filtros `tipo`, `competencia` |
| `GET /api/notas/{id}` | sim | sim | item traz `classificacao` e `calculo` quando existem |
| `GET /api/notas/{id}/resumo` | não | sim | |
| `POST /api/notas/{id}/classificar` | não | sim | antes de IA e gravação |
| `POST /api/notas/{id}/calcular` (`?cbs=`) | não | sim | antes da calculadora e gravação |
| `PUT /api/notas/{id}/pagamento` | não | sim | antes da mudança/recalcular |
| `POST /api/clientes/{id}/calcular` | não | sim | empresa e cada nota |
| `GET /api/clientes/{id}/revisao` | não | sim | |
| `PUT /api/itens/{id}/classificacao` | não | sim | empresa derivada do item; idênticos só do cliente |
| `GET /api/classificacoes/opcoes` | não | usuário persistido | catálogo oficial global; não tem dados de empresas |
| `GET /api/clientes/{id}/relatorio` (JSON, `de`/`ate` AAAA-MM) | sim | sim | |
| `GET /api/clientes/{id}/relatorio.csv`, `GET /api/notas/{id}/export.csv` | não | sim | |
| `GET /api/demo/status`, `POST /api/demo/reiniciar` | não | ADMIN | só existem com `tribia.demo.habilitado=true`; reset destrutivo |

Endpoints que o front espera e **não existem**: `GET/POST /api/clientes/{id}/analises-fiscais`,
`GET /api/clientes/{id}/analises-fiscais/indicadores`, `GET /api/analises-fiscais/{id}`, `.../relatorio`
(contrato em `frontend/docs/inteligencia-fiscal-api.md`, client em `frontend/src/api/inteligenciaFiscal.ts`).

## Contratos que importam

- **Upload** `POST /api/clientes/{id}/notas` → `201` com `{ importadas: [NotaResumoDto], rejeitadas: [{arquivo,
  status, motivo}] }` (`NotaController.java`). Rejeição por nota: 422 com `detail` (chave que não confere, finalidade
  não suportada, etc.).
- **Nota** `GET /api/notas/{id}` → `NotaDetalheDto` com `itens[]`; cada item: `ibsCbsDestacado` (do XML),
  `classificacao` ({cst, cClassTrib, regime, origem, confianca, aceita, revisada, justificativa}) e `calculo`
  ({natureza, vCbs, vIbsUf, vIbsMun, vIs, impostoHoje, imposto2027, simulado}).
- **Lista** `GET /api/clientes` → `ClienteListaDto` {id, cnpj, razaoSocial, nomeFantasia, regime, setor, uf,
  municipio, codigoMunicipio, ativo, notas, indicadores}; `ativo` é booleano, compatível com o tipo existente
  em `frontend/src/api/types.ts`. Indicadores só agregam empresas visíveis.
- **Relatório** `GET /api/clientes/{id}/relatorio` → `RelatorioDto` (apuração PIS/Cofins atual, competências,
  documentos, contrapartes, verificações).

## Fluxos

| Fluxo | Estado |
|---|---|
| Login → visão geral → cadastrar empresa → criar acesso | funciona (testado: `AcessoEmpresasTest` + Playwright anterior) |
| Upload de XML → lista → detalhe da nota | funciona; item aparece "Pendente" por B5 |
| Relatório da empresa (apuração atual) | funciona |
| Lista de empresas mostrando status | B4 verificado na API e E2E Chrome: inativar/reativar, bloqueio/habilitação de upload |
| Classificar → revisar → calcular 2027 → painel → CSV | **sem tela**; só por Swagger/API |
| Inteligência Fiscal | **sem backend** |

## Inconsistências conhecidas

1. B4 corrigido na P0.1: presença de `ativo` testada na API; build e lint do frontend passam, sem editar o front.
2. B5: front lê `ibsCbsDestacado`, ignora `classificacao`/`calculo`.
3. Os documentos `INTEGRACAO_FRONT_BACK.md` e `MAPA_FUNCIONAL.md` (Hermes) não listam o risco B3 e erram detalhes;
   ver [06](06-BUGS-PENDENCIAS.md) §Divergências.
4. README corrigido pontualmente: H2 arquivo, escopo avaliado de isolamento, histórico e limitações.
5. UI/API exibem aviso de estimativa/revisão fiscal; preservar esse aviso ao integrar resultados.
