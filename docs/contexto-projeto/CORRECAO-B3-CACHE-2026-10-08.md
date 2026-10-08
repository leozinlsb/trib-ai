# Etapa 1 — Correção B3-CACHE (08/10/2026)

Autorização: “pode ir para o proximo passo”, após a recomendação de corrigir o cache privado.
Não autoriza iniciar Etapa 2. A reprodução anterior permanece registrada em
[VALIDACAO-ETAPA-1-2026-10-08.md](VALIDACAO-ETAPA-1-2026-10-08.md).

## Causa e correção

A chave antiga identificava somente NCM + descrição. Uma revisão MANUAL de A sobrescrevia
o cache compartilhado; uma classificação legítima da nota de B copiava justificativa e
validada=true. Guardas de acesso por ID não impediam esse caminho indireto.

A correção separa namespaces na coluna `chave` existente, mantendo a restrição única e
varchar(520), sem adicionar colunas, migrar, apagar ou reatribuir registros históricos:

- `EMPRESA:v1:<clienteId>:<SHA-256>`: aprendizado IA, correção e aceite humanos privados.
  O proprietário vem da nota/item autorizado, nunca de um ID escolhido no pedido ou
  da empresa da sessão ADMIN. A hash usa a mesma normalização NCM/descrição preexistente.
- `CATALOGO:v1:<SHA-256>`: apenas carga administrativa `SEED` curada e pública.
  O método genérico exige ADMIN e rejeita fonte IA/MANUAL.
- Leitura: XML válido → cache da empresa → catálogo SEED novo → SEED legado → IA/fallback
  preexistentes. Legado IA/MANUAL/desconhecido sem dono não é reutilizado por nenhuma empresa.
- Aceitar uma sugestão copia a classificação do item para o cache privado, validado e
  fonte MANUAL. Nunca promove o catálogo nem o cache de outra empresa. IA continua não aceita.
- Carga SEED não sobrescreve cache privado nem a chave legada. Classificações já existentes
  continuam intactas; XML mantém precedência. Regras, alíquotas e fallback fiscal não mudaram.

Arquivos de produção deste incremento: `util/ChaveClassificacao.java`,
`repository/ClassificacaoCacheRepository.java`, `service/classificacao/ClassificacaoService.java`;
comentários atualizados em `model/ClassificacaoCache.java` e `ClassificacoesSeedLoader.java`.
Guardas P0.1, autenticação, sessão, CSRF e demo ADMIN foram preservados.

## Dependências e impactos verificados

1. Todos os consumidores de leitura/gravação de cache foram localizados. IA escreve privado;
   revisão já identifica item autorizado; seed e fixtures administrativas usam fonte SEED.
2. Chaves de produtos para deduplicação, revisão de idênticos e respostas demo mantêm `de()`;
   somente a persistência/consulta do cache usa namespaces.
3. Schema JPA e largura da coluna não mudaram. Testes inserem linhas no formato legado,
   leem SEED seguro e comparam snapshots de linhas inseguras sem alterações. Isso não é
   uma migração validada em banco real: nenhum banco persistente foi aberto.
4. ADMIN revisando uma nota grava no escopo da empresa proprietária. Consultas, negativas
   diretas, lote, exportação, usuários inativos/removidos e seed continuam cobertos.
5. `ClassificacaoIaControllerTest` tinha XML com chave nNF=777, mas ide/nNF antigo: corrigidos
   nNF e cDV coerentemente. O contrato antigo esperava cache global de IA; foi substituído
   por reuso na própria empresa e chamada IA simulada independente na outra, não por ocultação
   da falha. `RevisaoControllerTest` consulta a nova chave privada. Valores fiscais intactos.
6. Menos reuso global implica possíveis novas chamadas IA por empresa. Não há chamadas novas
   em login/upload/carga; classificar usa o fluxo já existente. Nenhuma IA paga foi acionada.

## Evidências reais

JDK 21.0.12.1, Maven Wrapper offline, banco H2 em memória UUID por contexto,
`spring.config.import=` para não importar configuração local e senha ADMIN sintética.
`GEMINI_API_KEY` vazio na sessão de testes. Calculadoras/IA externas simuladas; flags fiscais
de produção não foram sobrescritas nesta validação.

| Execução | Resultado |
|---|---|
| Primeiro recorte após ampliar cobertura | 61 testes, 0 falhas/erros/ignorados |
| Recorte final, incluindo aceite IA e contrato cache entre empresas | **63 passaram, 0 falhas/erros/ignorados** |
| Suíte completa após a correção | **195 testes: 167 passaram, 21 falhas, 0 erros, 7 ignorados** (exit 1) |
| Frontend `npm run build` | exit 0; TypeScript + Vite |
| Frontend `npm run lint` | exit 0 |
| `git diff --check` | exit 0 |

O recorte final compreende: IsolamentoEmpresasTest (28), FluxoHttpEtapa1Test (5),
AcessoEmpresasTest (12), SeedRunnerTest (4), SeedRunnerAutorizacaoTest (2),
ClienteControllerTest (4), ChaveClassificacaoTest (7), um caso de ClassificacaoIaControllerTest.
Não são 63 testes novos: incluem a cobertura anterior. Neste incremento foram adicionadas
17 invocações (13 de cache/isolamento e 4 de chaves), além do ajuste da regressão já existente.

Cobertura nova: produto idêntico em duas empresas; texto privado não aparece no detalhe de B;
reuso/aceite próprios; ADMIN usa dono da nota; IA privada não aceita; aceite IA não altera
cache de B; aceite SEED novo/legado não promove catálogo/B; legado IA/MANUAL/desconhecido
ignorado e preservado; carga curada não sobrescreve privados/legado; XML tem precedência;
chaves normalizadas, distintas e limitadas. Negativas existentes mantêm snapshots de oito
tabelas e verificam zero chamadas IA/calculadoras. Os cinco casos HTTP reais verificam cookies,
login/logout, CSRF, ativo/inativo, upload, consultas/exportação e negativas em Tomcat loopback.
Não equivalem a E2E visual de navegador.

Reproduzir em PowerShell, a partir de `backend/`:

```powershell
$env:JAVA_HOME = 'C:\caminho\para\jdk-21'
$env:GEMINI_API_KEY = ''
.\mvnw.cmd -o test '-Dspring.config.import=' '-Dtribia.admin.senha=senha-apenas-dos-testes' `
  '-Dtest=IsolamentoEmpresasTest,FluxoHttpEtapa1Test,AcessoEmpresasTest,SeedRunnerTest,SeedRunnerAutorizacaoTest,ClienteControllerTest,ChaveClassificacaoTest,ClassificacaoIaControllerTest#cacheReutilizaProdutosNaPropriaEmpresaMasOutraEmpresaConsultaSuaIa'
.\mvnw.cmd -o test '-Dspring.config.import=' '-Dtribia.admin.senha=senha-apenas-dos-testes'
```

Artefatos locais: `backend/target/surefire-reports/`. Uma execução direcionada sobrescreve
relatórios das classes selecionadas: não somar arquivos de rodadas diferentes. Em especial,
`RegrasApuracaoTest` tem 19 casos dinâmicos; usar resumo Maven/casos, não somente atributo
XML `tests` (que está 0 nessa classe).

## Falhas remanescentes e limites

As 21 são anteriores ao B3-CACHE, com as mesmas causas do relatório anterior:

| Grupo | Casos | Causa / próxima decisão |
|---|---|---|
| Base fiscal | 13: Apuração 3, Dashboard 6, Revisão 3, Roteiro 1 | Exclusão de tributos da base; validar S5 com fonte oficial/especialista antes de mudar implementação ou expectativas |
| Fallback | 2: Apuração 1, Classificação IA 1 | Testes esperam pendência, política atual sugere REGRA; confirmar comportamento de produto, sem desabilitar arbitrariamente |
| Contratos | 6: Dashboard 1, CSV 1, Nota 2, Gemini 2 | SUJEITO_IS/cabeçalho; devoluções e tpNF=0; retry do mesmo modelo; confirmar e testar contratos corretos |

Uma das 22 históricas foi resolvida pela fixture/contrato do cache; a regressão de
confidencialidade também passou. Os 7 ignorados continuam: GeminiContrato 2 (sem chave),
CalculadoraOficialContrato 2 e SimplificadaVsOficial 1 (serviço ausente), geradores seed/demo
2 (opt-in desabilitado; gerador de IA também exige chave). Não habilitados para obter verde.

E2E visual continua pendente; navegador estava indisponível na validação anterior, não
foi executado neste incremento. Roteiro isolado está no relatório anterior.

**Ressalva histórica:** o patch não apaga justificativas que já tenham sido copiadas para
classificações de outras empresas pelo código antigo. Antes de uso real, avaliar existência
desse histórico em ambiente autorizado e aprovar uma remediação auditável. Não há evidência
de ocorrência em dados reais e não é seguro inferir dono de cache legado. Sem limpeza automática,
sem rollback para o binário antigo (ele voltaria a ler o cache inseguro) nem reset demo em dados reais.

## Conclusão e continuidade

B3-CACHE corrigido no código e nos caminhos testados. Etapa 1 **avançada, não concluída**:
21 falhas, validação visual/contratos externos e ressalva do histórico ainda pendentes.
Não liberar Etapa 2 automaticamente. Próximo incremento recomendado: estabilizar os seis
contratos remanescentes, validando comportamento antes de alterar testes; tratar fallback
e base fiscal separadamente com aprovação/fundamento, além da validação visual isolada.

Git: branch `dev/prataliyann-hue`, HEAD `ca65483`; árvore local prévia preservada, sem commit/push.
HANDOFF, PENDENCIAS, plano mestre e contexto afetado atualizados.
