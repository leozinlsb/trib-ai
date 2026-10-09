# 10 — Pendências e evolução

## Pendências (não bloqueiam o uso local/demo)

| Id | Pendência | Por quê | Sugestão |
|---|---|---|---|
| API-1 | Validar o fluxo com **Gemini real** pela API pública | Não executado (custo, sem autorização) | Uma análise com o produto de exemplo, autorizada, registrando tempo e resultado |
| API-2 | **Profile `prod`**: decidir se a API pública fica ligada na hospedagem e com quais limites | Hoje fica ligada em todos os profiles (sem Swagger em prod) | Revisar limites e emitir chaves só para integradores reais |
| API-3 | Contadores por minuto e falhas por IP **em memória** | Várias instâncias multiplicam o limite | Contador compartilhado (banco/Redis) se houver escala horizontal |
| API-4 | Migrações versionadas | `ddl-auto=update` cria as tabelas novas; padrão atual do projeto | Flyway antes de produção com dados reais (vale para todo o projeto) |
| API-5 | Tela de gestão de chaves no front | Gestão hoje por API/script de ADMIN | Tela em Configurações do admin (fora do escopo: front não podia ser alterado) |
| API-6 | Retenção de `Idempotency-Key` | Sem expiração (guardada com a solicitação) | Definir política (ex.: 30 dias) se o volume crescer |
| API-7 | Revisão jurídica/contratual | Termos de uso, LGPD e responsabilidade do integrador ao usar sugestões de IA | Antes de oferecer comercialmente |

## Evolução sugerida (não implementada)

- **Webhooks** de conclusão (evita consulta periódica), com assinatura HMAC e reenvio.
- **Lote**: `POST /api/v1/analises/lote` com várias mercadorias e Idempotency-Key por item.
- **Anexos** (ficha técnica PDF/DOCX/XLSX) reaproveitando `LeitorAnexos`, com limites próprios da API.
- **Relatório PDF** público (`GET /api/v1/analises/{id}/relatorio`) reaproveitando `RelatorioAnalisePdf`.
- **OAuth2 client credentials** para integradores corporativos que exigem tokens de curta duração.
- **Rotação** assistida de chave (duas chaves válidas em sobreposição) e idempotência por integrador.
- Medição de uso para cobrança (a contagem por chave já existe em `solicitacao_api`).
- Endpoints para CST/cClassTrib e projeção CBS/IBS de itens de NF-e, **somente** depois da validação profissional
  das regras (pendências V1–V6/S5 da plataforma).
