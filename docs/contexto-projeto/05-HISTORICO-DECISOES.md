# 05 — Histórico de decisões

**Limite das fontes:** a conversa original (várias sessões) não está disponível por inteiro. Esta lista combina o
`git log`, o código, `HANDOFF.md`/`PENDENCIAS.md` e um resumo de contexto das sessões da autora do frontend. Onde a
fonte é só esse resumo, está marcado *(resumo de sessão)*. Nada foi inventado para preencher lacunas.

## Linha do tempo (git)

| Data | Commit | Autor | O quê |
|---|---|---|---|
| 06/10 | ddf9919 | leozinlsb | first commit |
| 07/10 | 3a5a4ad … 330de63 | leozinlsb | Etapas 1–4: Spring Boot, parser NF-e, seed, regras de apuração, calculadora RTC |
| 07/10 | b52c531, 59d1557 | leozinlsb | Etapa 4b (classificação XML+cache, cálculo 2027) e 5 (Gemini) |
| 08/10 | ea16b47, a6993bb | leozinlsb | Painel, revisão, CSV, profile demo |
| 08/10 | 126355f | pratalidev | Backend: login, multiempresas com isolamento, relatório por empresa |
| 08/10 | bccfed7 | pratalidev | Frontend: landing, login, multiempresas, relatórios, Inteligência Fiscal |
| 08/10 | 8ba383d | leozinlsb | "Salva mudanças locais no backend" (grande: H2 em arquivo, novas regras fiscais, devoluções, etc.) |
| 08/10 | a556671, 94e7b2f, ca65483 | leozinlsb | Merge do front, ajuste de testes, HANDOFF.md |

## Decisões

Atualização de 08/10/2026, execução autônoma ainda Etapa 1: contratos confirmados no código
e testes corrigidos; fallback passa a se abster sem associação única, sem inferir integral.
Sugestões REGRA continuam 0,40/não aceitas/não cache. Gabaritos fiscais antigos preservados,
inclusive CSV/cache-only antes mascarados; UI/API mostram limites dos comparativos. Dez
fluxos Chrome e três contratos RTC real offline aprovados. S5 consultada na fonte legal,
mas motor completo exige revisão profissional; nenhuma fórmula/alíquota/tabela alterada.
Etapa 1 concluída com ressalvas no ambiente isolado; não libera produção nem Etapa 2
automaticamente. [Decisões, auditoria e resultados](CONCLUSAO-ETAPA-1-2026-10-08.md).

| # | Decisão | Por quê | Implementação / estado |
|---|---|---|---|
| D1 | Classificar em **CST + cClassTrib**, não NCM | É o que a NF-e/IBS-CBS exige; NCM já vem no XML | `ClassificacaoService`, prompt. Vigente |
| D2 | Cascata XML → cache → IA → regra | Barato e determinístico primeiro; IA só no que sobra | Regra só com associação única permitida; ausência/ambiguidade mantém pendência, nunca presume integral |
| D3 | IA só escolhe de lista fechada; benefício de anexo exige NCM na lista oficial | Evitar códigos inventados e benefício presumido | `ClassificadorIa`. Vigente |
| D4 | Revisão humana para confiança < 0,70 / não aceito | Adendo do projeto | `CriterioRevisao`. Vigente |
| D5 | Cálculo oficial (RTC) com fallback simplificado marcado como "simulado" | Calculadora offline pode estar fora do ar na demo | Modo AUTO. Vigente |
| D6 | Profile `demo` com respostas gravadas da IA | Plano B de apresentação sem internet/cota | Vigente; `ferramentas/ensaio-demo.ps1` **não faz login** → provavelmente quebrado desde o login (inferido, não executado) |
| D7 | Autenticação por sessão (cookie HttpOnly) + CSRF, sem cadastro público | Segurança sem token no navegador; contas criadas pelo admin | `SecurityConfig`. Vigente |
| D8 | Dois papéis: ADMIN (escritório) e EMPRESA | Escritório atende várias empresas | `Papel`, `AcessoService`. P0.1 completa checagens em serviços/consultas/lote; testes verdes |
| D9 | "Remover" empresa = desativar (exclusão lógica) | Preservar notas e acessos | `Cliente.ativo`. Vigente |
| D10 | Administrador criado na inicialização sem senha no código; senha por env/arquivo local fora do Git | Não versionar segredo | `AdminSeeder`, `application-local.properties`. Vigente. Com banco persistente a senha não muda depois de criada |
| D11 | H2 em **arquivo** em vez de memória | Persistir uploads, revisões e cache aprendido | 8ba383d. Vigente. Substituiu a decisão anterior (memória); README ainda diz memória |
| D12 | Frontend sem classificação/cálculo na primeira entrega *(resumo de sessão)* | Escopo "frontend sobre o backend existente" | O backend já tinha as rotas; ligar o front é P1 |
| D13 | Inteligência Fiscal **somente frontend** primeiro; sem simulação em produção; contrato documentado | Não inventar resultados fiscais; backend dependia de decisões | Telas prontas, rota de exemplo só em DEV. Vigente |
| D14 | Linguagem da interface sem jargão técnico/de backend *(resumo de sessão: pedido da autora)* | Cliente final não deve ler detalhes de implementação | Aplicada no front |
| D15 | Gráfico de rede/relacionamentos mais refinado, com animação no hover *(resumo de sessão)* | Pedido de design | `components/charts/GrafoRede.tsx` |
| D16 | Aceitar devoluções/complementares e `tpNF` 0/1 (antes rejeitadas) | Mudança do autor do backend em 8ba383d | `Operacao`; ajuste "ajuste/finNFe=3" continua rejeitado. 2 testes desatualizados |
| D17 | Excluir tributos da base 2027 | Autor cita LC 214 art. 12 §2º V; conferir também campos efetivamente extraídos do XML | **[validar]**; diagnóstico atual separa 13 divergências numéricas e 2 de fallback |
| D18 | Nova tentativa no mesmo modelo Gemini em 429/503 | Cota/sobrecarga costumam passar em segundos | 2 testes desatualizados |
| D19 | Estabilização antes de funcionalidades novas (fase atual) | Pedido da autora: não corrigir tudo de uma vez; mudanças pequenas e reversíveis; não enfraquecer segurança nem fazer teste passar artificialmente | Testes de auth corrigidos só no lado do teste |
| D20 | P0.1: autorização central também nos serviços, antes de efeitos externos/persistência; lista só visíveis + ativo | Corrigir B3/B4 pela causa, incluindo consumidores internos e lote | Implementado após autorização em 08/10/2026; 36 testes direcionados verdes, sem mudanças fiscais |
| D21 | Demo e console H2 somente ADMIN; seed inicial com ADMIN persistido e contexto temporário restaurado | Operações globais não pertencem a usuário EMPRESA; impedir acesso direto ao banco | Propriedade demo e CSRF preservados; sem sessão HTTP no seed; restauração testada inclusive em falha |
| D22 | Plano mestre com exatamente quatro etapas, sem avanço automático | Estratégia aprovada pelo usuário em 08/10/2026 | PLANO_MESTRE_TRIBIA.md e AGENTS.md; planos anteriores apenas histórico/backlog |
| D23 | Não encerrar Etapa 1 enquanto B3-CACHE existir | Regressão comprovou justificativa/validação MANUAL de A no item de B | Correção autorizada e testada; 21 falhas/E2E visual/histórico ainda impedem encerramento |
| D24 | Cache IA/revisão/aceite privado; catálogo público somente SEED | Eliminar vazamento indireto pela causa sem ocultar DTO | EMPRESA/CATALOGO + hash na chave existente, sem alteração de schema; 63 testes direcionados verdes; CORRECAO-B3-CACHE-2026-10-08.md |
| D25 | Legado IA/MANUAL sem dono não reutilizado nem reatribuído/apagado | Não é seguro inferir proprietário; preservar dados históricos | Legado SEED permanece legível; eventual classificação já contaminada exige avaliação/remediação autorizada antes de uso real |

## Decisões abandonadas ou alteradas

- H2 em memória → arquivo (D11).
- Rejeitar devoluções → aceitar (D16); dependência de fornecedor Simples: "sem limite de crédito" → `SEM_CREDITO`.
- Base 2027 = valor do item → base sem tributos (D17).
- `PLANO_CORRECOES.md` propôs mock fixo de JEV: **rejeitado** (contraria D13).
- HANDOFF antigo sugeria `@WithMockUser`: **não funciona** (o `AcessoService` exige `UsuarioLogado`); usa-se
  `@WithUserDetails("admin@tribia.local")`.

## Tentativas que não funcionaram *(resumo de sessão, exceto onde indicado)*

- Scratchpad com JDK parcialmente apagado: foi preciso reextrair (ambiente, sem efeito no repositório).
- Testes E2E (Playwright) precisaram de esperas e seletores exatos.
- Fazer `@WithUserDetails` sozinho não bastava nos testes que alteram dados: precisam também de `.with(csrf())`
  (evidência: 401/403 → após o ajuste, zero 401/403 na execução final).
- Conferir regras fiscais: ao desligar `excluir-tributos-da-base` e `fallback-regra` por linha de comando, 15 testes
  passam (prova de causa, não correção).
