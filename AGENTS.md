# Instruções de continuidade — TribIA

Antes de decisões ou alterações:

1. Leia `PLANO_MESTRE_TRIBIA.md` e identifique a etapa autorizada. Existem exatamente quatro
   etapas principais; não avance automaticamente para a seguinte.
2. Leia `CONTEXTO_COMPLETO_TRIBIA.md` e os documentos de `docs/contexto-projeto/`, na ordem
   `00-LEIA-PRIMEIRO.md` até `09-ROADMAP.md`.
3. Consulte `HANDOFF.md`, `PENDENCIAS.md`, `MAPA_FUNCIONAL.md`, `INTEGRACAO_FRONT_BACK.md`,
   `FLUXO_FISCAL.md`, `PLANO_CORRECOES.md` e `README.md`; planos legados não substituem o mestre.
4. Verifique código, Git e testes reais. Preserve mudanças locais; não confunda implementado,
   testado, parcial e planejado. Evidência verificável prevalece sobre documentação divergente.

## Limites e qualidade

- Priorize segurança e autorização por empresa nos serviços, consultas e operações em lote.
- Preserve autenticação, sessões, CSRF e funcionalidades existentes; negativas não podem
  escrever registros nem chamar IA/calculadoras.
- Não faça refatorações fora do escopo, commits/pushes automáticos, operações destrutivas,
  exposição de credenciais, alterações em dados reais ou chamadas pagas sem autorização.
- Não invente nem altere regras fiscais/expectativas para deixar testes verdes: registre
  fundamento, incerteza e necessidade de validação oficial/profissional.
- Use banco e credenciais sintéticos para testes; não execute reset demo contra dados reais.
- Teste proporcionalmente ao risco e registre resultados reais, inclusive falhas e ignorados.

## Encerramento

Atualize pontualmente `HANDOFF.md`, `PENDENCIAS.md` e contexto afetado. Atualize o plano mestre
somente com progresso comprovado ou decisão aprovada. Informe critérios satisfeitos,
ressalvas, bloqueios e próxima tarefa conectada ao plano; aguarde aprovação para mudar de etapa.
