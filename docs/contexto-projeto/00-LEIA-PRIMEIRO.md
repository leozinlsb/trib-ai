# 00 — Leia primeiro

TribIA compara o imposto de hoje (PIS/Cofins) com o de 2027 (CBS/IBS/IS) a partir de XMLs de NF-e, com
classificação **CST + cClassTrib** feita por XML, cache, IA (Gemini) ou regra, e revisão humana. Backend Java 21 /
Spring Boot em `backend/`, frontend React 19 / Vite em `frontend/`. Documento central: [`../../CONTEXTO_COMPLETO_TRIBIA.md`](../../CONTEXTO_COMPLETO_TRIBIA.md).

## Ordem de leitura

Consulte [plano mestre](../../PLANO_MESTRE_TRIBIA.md) e o documento central primeiro;
leia esta pasta na ordem numérica 00 até 09, conforme AGENTS.md, e depois HANDOFF/PENDENCIAS
e documentos legados. O mestre define exatamente quatro etapas; o roadmap detalha backlog.

## Regras para continuar

- Preserve a arquitetura; mudanças pequenas, verificáveis e reversíveis; explique e peça aprovação antes de mudança
  estrutural ou de comportamento de segurança.
- Não altere regra fiscal sem justificativa e validação; não troque resposta real por dado fictício; não remova nem
  enfraqueça teste para obter verde; não enfraqueça a segurança.
- Não faça chamadas pagas (Gemini) sem autorização; nunca grave chave/senha em arquivo versionado.
- Código ≠ funcionalidade testada: confira a evidência antes de declarar algo resolvido.
- Não confunda **NCM** (mercadoria) com **CST/cClassTrib** (tratamento CBS/IBS).
- Documentos do Hermes na raiz têm erros conhecidos; em conflito, vale o código (ver divergências em 06).

## Em uma frase: onde estamos

**Etapa 1 CONCLUÍDA COM RESSALVAS no ambiente isolado; não avançar automaticamente à 2.**
B3/B4/cache e contratos verdes, fallback se abstém sem evidência. 120 direcionados locais,
3 contratos RTC offline e 10 E2E Chrome aprovados. Suíte: 220 testes, 201 passaram,
15 falhas fiscais preservadas, 0 erros, 4 ignorados. Build/lint passam. Cache privado,
catálogo SEED/legado preservados. Antes de uso real: revogação da chave, histórico do cache
e validação fiscal. [Execução e limites](CONCLUSAO-ETAPA-1-2026-10-08.md); relatórios anteriores históricos.

## API pública v1 (09/10/2026)

API REST para integradores sobre o motor da Inteligência Fiscal, fora das quatro etapas: [API-PUBLICA/](API-PUBLICA/07-HANDOFF-FINAL.md).

## Informação não recuperada

O histórico integral das conversas anteriores não estava disponível; ver limite em [05](05-HISTORICO-DECISOES.md).
O significado/integração de JEV não consta no repositório. Não verificados: Gemini real,
revogação da chave, dados históricos reais, ensaio-demo.ps1 e atualidade dos CSVs embutidos.
E2E Chrome dos fluxos de base e RTC offline real foram verificados; não é certificação fiscal.
