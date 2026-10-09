# 04 — Segurança e isolamento entre empresas

## Chaves de API

| Aspecto | Implementação | Evidência |
|---|---|---|
| Geração | `SecureRandom`: 6 bytes de prefixo (12 hex) + 32 bytes de segredo (base64url). Formato `tribia_<prefixo>_<segredo>`, 63 caracteres | `ApiPublicaUnidadeTest.chaveGerada...` (200 chaves distintas, formato) |
| Armazenamento | Só o prefixo e o **SHA-256 da chave inteira**. Com 256 bits aleatórios, hash rápido é adequado (BCrypt serve para senhas de baixa entropia) | `GestaoDeChaves`: hash gravado = SHA-256, ≠ chave |
| Exibição | Chave completa só na resposta 201 da emissão, com `Cache-Control: no-store` | idem |
| Verificação | Formato → prefixo indexado → `MessageDigest.isEqual` (tempo constante) | `Autenticacao.chaveMalformadaOuComSegredoErrado...` |
| Respostas | Desconhecida e segredo errado: mesma resposta `CHAVE_INVALIDA`. `CHAVE_REVOGADA`/`CHAVE_EXPIRADA` só para quem apresentou a chave inteira certa | testes de revogada/expirada |
| Revogação | Definitiva, idempotente, registra data e autor | `GestaoDeChaves` |
| Validade | Padrão e máximo `tribia.api-publica.validade-maxima-dias` (365); 0 = sem validade | `soAdministradorGerenciaChaves` (9999 dias → 400) |
| Escopos | `ANALISES_CRIAR`, `ANALISES_LER`; cobrados na cadeia de segurança por rota | `escoposMinimos` |
| Logs | Só o prefixo (emissão, revogação, uso de revogada, criação de análise). A chave nunca é escrita | `GestaoDeChaves` captura o log inteiro e confere que a chave não aparece |
| Emissão | Somente ADMIN, rota interna com sessão + CSRF. Nenhuma rota pública emite chave | `soAdministradorGerenciaChaves` (EMPRESA 403, sem login 401, sem CSRF 403) |

## Cadeias de segurança separadas

- `/api/v1/**` → `ApiPublicaSecurityConfig` (`@Order(1)`): stateless, `RequestAttributeSecurityContextRepository`,
  sem sessão, sem CSRF, sem cache de requisição. Um cookie de sessão de usuário **não** autentica a API pública
  (testado: ADMIN logado sem chave → 401). Nenhum cookie é criado (testado).
- Resto de `/api/**` → `SecurityConfig` existente, intocado. Uma chave de API **não** abre rotas internas
  (testado: `/api/clientes`, `/api/admin/chaves-api`, `/api/analises-fiscais/{id}` com chave → 401).
- O principal da API pública (`IntegradorAutenticado`) não é `UsuarioLogado`: se algum código interno fosse
  alcançado com ele, `AcessoService.atual()` recusaria (defesa em profundidade).

## Isolamento entre empresas

1. A chave pertence a **uma** empresa (`chave_api.cliente_id`), definida pelo ADMIN na emissão.
2. O corpo não tem campo de empresa; campos extras (`clienteId`, `cnpj`) são ignorados — testado que a análise fica
   na empresa da chave.
3. A solicitação copia a empresa da chave; **toda** leitura pública filtra por `publicoId` **e** `cliente_id`
   (`SolicitacaoApiRepository.buscarDaEmpresa`, `listarDaEmpresa`).
4. Id público é UUID aleatório (não enumerável); o id numérico interno não é aceito nem exposto.
5. Id de outra empresa responde exatamente como id inexistente (404, mesmo `detail`), sem dados da análise.
6. Chaves diferentes da mesma empresa enxergam as análises da empresa (o "tenant" é a empresa); a idempotência é por
   chave.
7. Empresa desativada: todas as chamadas da chave → 403 `EMPRESA_DESATIVADA`, sem criar nada.

Testes (`ApiPublicaV1Test$Multiempresa`): empresa A vê a própria; outra chave de A vê; empresa B recebe 404 e
listagem vazia; ids manipulados (id numérico interno, maiúsculas, `%00`, `..%2F`, injeção SQL, UUID zerado) → 400
(firewall) ou 404, nunca 200. **Mutação de controle:** com o filtro de empresa removido da consulta, 2 desses testes
falharam (404 esperado, 200 recebido); o filtro foi restaurado.

## Negativas não escrevem nem chamam IA

Chave ausente/inválida/revogada/expirada, escopo insuficiente, empresa desativada, corpo inválido, Idempotency-Key
inválida, cota/simultâneas excedidas: nenhuma `AnaliseFiscal`/`SolicitacaoApi` é gravada e o `LlmClient` não é
chamado (verificado com `verify(llm, never())` e contagens antes/depois).

## Abuso e consumo

| Controle | Onde | Persistência |
|---|---|---|
| Requisições/minuto por chave (padrão 60) | `FiltroChaveApi` | Memória (por instância) |
| Falhas de autenticação por IP/minuto (padrão 20) | `FiltroChaveApi` | Memória |
| Cota diária de análises por chave (padrão 100, dia de Brasília) | `ApiPublicaService` | Banco (sobrevive a reinício) |
| Análises simultâneas por chave (padrão 5) | `ApiPublicaService` | Banco |
| Fila global do motor (2 threads, 20 na fila) | `AnalisesFiscaisConfig` (existente) | — |
| Tamanho dos campos | Bean Validation (mesmos limites da tela) | — |

Limitações conhecidas: contadores por minuto não são compartilhados entre instâncias; o bloqueio por IP pode afetar
integradores atrás do mesmo NAT (dura 1 minuto); atrás de proxy, o IP real depende de
`server.forward-headers-strategy` (já `framework` no profile `prod`).

## Dados expostos

Saem: dados da mercadoria enviados pelo próprio integrador, status, resultado do motor (NCM, textos oficiais,
fundamentação, verificações, fontes públicas), situação da revisão. Não saem: id numérico interno, empresa/cliente
id, nome/e-mail de quem revisou, prompt, texto de anexos, chaves (Gemini/JEV), stack traces, dados de outras empresas.
A mensagem de falha do motor pode citar o nome da variável `GEMINI_API_KEY` (nunca o valor).

## Riscos remanescentes

- Rotação de chave: a idempotência é por chave; um reenvio após trocar de chave cria outra análise.
- Sem WAF nem limite de tamanho total do corpo além dos limites por campo (o Tomcat aplica o padrão dele).
- Texto da mercadoria vai para a IA (mesmo risco de injeção de prompt da plataforma; o motor marca trechos suspeitos e
  manda para revisão).
- Não houve teste de carga.
