# 01 — Contexto e arquitetura da API pública v1

Levantado em 09/10/2026 no branch `dev/prataliyann-hue` (commit de partida `52519e5`, árvore limpa exceto
`TestEnv.java`/`TestEnv.class` soltos na raiz, que não pertencem a esta missão e não foram tocados).

## Por que existe

ERPs e escritórios contábeis já têm sistemas próprios. Em vez de exigir que usem a plataforma do TribIA, a API
pública v1 expõe o **mesmo motor de Inteligência Fiscal** (sugestão de NCM de mercadoria) para outro sistema enviar
dados de um produto e consultar o resultado em JSON.

## O que já existia e foi reaproveitado (conferido no código)

| Peça | Arquivo | Papel na API pública |
|---|---|---|
| Entidade da análise | `model/AnaliseFiscal.java` | Guarda entrada, status, histórico, resultado. A API pública **não cria outra análise**: cada solicitação aponta para uma `AnaliseFiscal` |
| Máquina de estados | `model/StatusAnalise.java` | AGUARDANDO → INTERPRETANDO → PESQUISANDO_NCM → AVALIANDO → VALIDANDO → GERANDO_RELATORIO → CONCLUIDA; encerra também em FALHA, INFORMACOES_INSUFICIENTES, AGUARDANDO_REVISAO |
| Serviço | `service/fiscal/AnaliseFiscalService.java` | Criação e detalhe. Foi dividido em `registrarNova` / `despachar` / `detalheAutorizado` para a API pública reutilizar sem passar pelo login de usuário |
| Processamento | `service/fiscal/ProcessadorAnaliseFiscal.java` | Gemini (`PesquisaNcmIa`) → JEV (`AvaliadorJev`, opcional) → `ValidadorNcm` (NCM vigente Siscomex). **Inalterado** |
| Fila | `config/AnalisesFiscaisConfig.java` | Pool de 2 threads, fila 20; 503 com fila cheia. **Inalterado** |
| Reserva atômica | `AnaliseFiscalRepository.reservar` | Impede processamento duplicado. **Inalterado** |
| Retomada | `service/fiscal/RetomadaAnalisesFiscais.java` | Análises interrompidas voltam à fila na inicialização. Vale também para as criadas pela API pública |
| Erros | `exception/ApiExceptionHandler.java` | ProblemDetail (RFC 9457). A API pública usa o mesmo formato, com `codigo` e `requestId` a mais |

## Fluxo

```
ERP ──POST /api/v1/analises (X-API-Key, Idempotency-Key)──▶ SecurityFilterChain própria (/api/v1/**, sem sessão, sem CSRF)
   ├─ FiltroRequestId: X-Request-Id (aceita o do cliente se seguro; senão gera) + MDC
   ├─ FiltroChaveApi: prefixo → busca a chave → SHA-256 comparado em tempo constante → ativa? não expirou? empresa ativa?
   │                  → IntegradorAutenticado (chave, empresa fixa, escopos) no SecurityContext da requisição
   ├─ LimitadorRequisicoes: janela de 1 minuto por chave (429 + Retry-After)
   └─ ApiPublicaAnaliseController → ApiPublicaService
        ├─ escopo ANALISES_CRIAR
        ├─ trava por chave (serializa criação: idempotência + cota consistentes)
        ├─ Idempotency-Key: mesma chave + mesmo payload → devolve a solicitação existente; payload diferente → 409
        ├─ cota diária e limite de análises simultâneas (contados no banco) → 429
        ├─ 1 transação: AnaliseFiscal (AGUARDANDO) + SolicitacaoApi (UUID público, hash do payload)
        └─ depois do commit: AnaliseFiscalService.despachar → fila → ProcessadorAnaliseFiscal (Gemini/JEV/NCM)
ERP ──GET /api/v1/analises/{uuid}──▶ mesma cadeia → busca por UUID **e** empresa da chave (outra empresa = 404)
        └─ mapeia AnaliseFiscal + ResultadoAnaliseFiscal → contrato público estável
```

## Separação interna x pública

- Interna (`/api/**` menos `/api/v1/**`): sessão em cookie + CSRF, `AcessoService`, usuário ADMIN/EMPRESA. Intocada.
- Pública (`/api/v1/**`): cadeia de segurança própria, ordenada antes; não lê sessão (um cookie de login não autentica
  a API pública) e não aceita chave de API nas rotas internas.
- Gestão das chaves: rotas internas de administrador (`/api/admin/chaves-api`, sessão + CSRF + ADMIN). Não existe rota
  pública de emissão de chave.

## Componentes novos

Pacote `br.com.tribia.apipublica` (detalhes em [02-IMPLEMENTACAO.md](02-IMPLEMENTACAO.md)).

## Limites conhecidos do motor (herdados, não alterados)

- Sugestão de IA; não é classificação definitiva nem decisão da Receita.
- Sem `GEMINI_API_KEY` a análise termina em FALHOU com mensagem clara (não há resposta inventada).
- Pontuação da JEV (quando ligada) é compatibilidade, não probabilidade de acerto.
- A NCM vigente embarcada não tem histórico de versões; validade da NCM não define tributação.
