# 10 — Pendências e evolução

## Pendências (não bloqueiam o uso local/demo)

| Id | Pendência | Por quê | Sugestão |
|---|---|---|---|
| API-1 | ~~Validar o fluxo com Gemini real~~ **Feito em 09/10/2026**: análise de NCM (~10 s, foi para revisão por divergência com a NCM informada) e envio de NF-e (12,3 s, 8 itens pela IA) | — | — |
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
- Fase 1 de notas e comparativo **implementada** em 09/10/2026 ([11](11-FASE-1-NOTAS-E-COMPARATIVO.md)), com os valores
  de 2027 marcados como projeção. Fase 2 (classificação avulsa e calculadora sem nota) **implementada** em 09/10/2026 a pedido da
  responsável ([12](12-FASE-2-CLASSIFICADOR-E-CALCULADORA.md)), com respostas marcadas como sugestão/projeção; as regras
  continuam pendentes de validação profissional (V1–V6/S5/R2).
- ~~API-10~~ **Resolvido em 09/10/2026:** classificação avulsa agora é assíncrona (POST em 151 ms no ensaio real,
  antes 69 s).
