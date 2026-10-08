# 01 — Visão do produto

> Legenda usada em todos os documentos: **[código]** existe no código; **[testado]** coberto por teste automatizado
> ou verificado manualmente (a evidência é citada); **[planejado]** só existe como intenção/contrato; **[validar]**
> depende de conferência fiscal por fonte oficial ou especialista.

## O que é

TribIA é uma aplicação web para **escritórios de contabilidade e suas empresas-clientes** medirem o impacto da
reforma tributária brasileira (EC 132/2023, LC 214/2025 e LC 227/2026, conforme o prompt do classificador) sobre o
que cada empresa compra e vende. A empresa envia os XMLs das NF-e; o sistema classifica cada item nas regras de
2027 (**CST + cClassTrib** do IBS/CBS, não NCM) e compara o imposto líquido de hoje (PIS/Cofins) com o de 2027
(CBS, IBS e Imposto Seletivo).

Origem: projeto universitário/hackathon. O backend foi construído por leozinlsb (etapas 1–8); a autenticação,
multiempresas, relatório por empresa e todo o frontend por pratalidev (autor desta linha de trabalho). Ver
[05-HISTORICO-DECISOES.md](05-HISTORICO-DECISOES.md).

## Público-alvo e proposta de valor

- **Administrador (escritório de contabilidade):** cadastra empresas, cria acessos, vê todas, envia notas e analisa.
- **Usuário de empresa:** vê somente a própria empresa.
- Valor: transformar um lote de XMLs em "quanto pagamos hoje x quanto pagaríamos em 2027, por produto e por
  fornecedor", com trilha do que foi classificado por XML, cache, IA ou regra, e revisão humana do que é incerto.

## Funcionalidades — planejado x implementado x testado

| Capacidade | Backend | Frontend | Testado / evidência |
|---|---|---|---|
| Login com sessão + CSRF | [código] | [código] usa | [testado] `AcessoEmpresasTest` (login/logout) |
| Multiempresas (cadastro, edição, desativar/reativar) | [código] | [código] usa | [testado] `AcessoEmpresasTest`; Playwright em sessão anterior |
| Acessos por empresa (criar/remover usuário) | [código] | [código] usa | [testado] `AcessoEmpresasTest` |
| Isolamento entre empresas | [código] guardas P0.1 + cache privado/catálogo SEED | navegação protegida, ativo/inativo | [testado] segurança/cache/HTTP e 10 E2E Chrome de base verdes; histórico real não auditado |
| Upload de XML NF-e (modelo 55) | [código] | [código] usa | [testado] 19 NotaController verdes, upload válido/inválido pela UI |
| Classificação XML → cache → IA → regra | [código] | **não usa** | [testado] `ClassificacaoIaControllerTest` com IA simulada; IA real só no `GeminiContratoTest` (exige chave, não rodado nesta fase) |
| Revisão manual (aceitar/corrigir/uso e consumo) | [código] | **não usa** | [testado] `RevisaoControllerTest` (3 falhas por mudança de regra, ver B6) |
| Cálculo 2027 (oficial ou simplificado) | [código] | **não usa** | [testado] 3 contratos RTC real offline verdes; Apuração mantém 4 divergências fiscais S5 |
| Painel do cliente (hoje x 2027) | [código] `/dashboard` | **não usa** (usa `/relatorio`) | [testado] `DashboardControllerTest` (6 falhas, ver B6) |
| Relatório por empresa (apuração PIS/Cofins atual) | [código] `/relatorio` | [código] usa | verificado em sessão anterior (Playwright); sem teste automático dedicado localizado |
| CSV (empresa e nota) | [código] | **não usa** | [testado] contrato estrutural verde; referência fiscal preservada falha por S5 |
| Profile `demo` (respostas gravadas da IA) | [código] | — | [testado] `RoteiroDemoTest` (1 falha por regra) |
| **Inteligência Fiscal** (análise de mercadoria/NCM) | **não existe** | telas prontas | [planejado] contrato em `frontend/docs/inteligencia-fiscal-api.md` |

## Inteligência Fiscal (módulo planejado)

Tela onde o usuário descreve uma mercadoria (nome, descrição, composição, finalidade, anexos) e recebe uma
**sugestão de NCM** com fundamentação, alternativas, validação e fontes. É um módulo **diferente** da classificação
das notas: aqui o resultado é **NCM** (código da mercadoria); na classificação das notas o resultado é **CST +
cClassTrib** (tratamento de CBS/IBS). Não confundir.

- Frontend: pronto, sem simulação em produção (mostra "Análise fiscal ainda não disponível" quando a rota 404);
  existe tela de exemplo só em `npm run dev` com dados marcados como fictícios.
- Backend: nenhum endpoint existe. Decisão do projeto: **sem dados fictícios** apresentados como reais.
- O papel do "JEV AI" (pontuação de alternativas) aparece apenas no contrato e no tipo TypeScript
  (`pontuacao` com `valor`, `escala`, `significado`). **O que é o JEV e como seria integrado não está definido em
  nenhum arquivo do repositório**; confirmar com a equipe antes de implementar.

## Fluxo esperado do usuário

1. Administrador entra → cadastra empresa → cria acesso para a empresa.
2. Envia XMLs (entradas e saídas) → o sistema importa e valida (chave de acesso, finalidade, tipo).
3. **[ainda não ligado ao front]** classifica (XML/cache/IA/regra) → itens incertos vão para revisão → cálculo 2027.
4. **[parcial]** vê o painel e o relatório; exporta CSV.
5. **[planejado]** consulta Inteligência Fiscal para mercadorias específicas.

Hoje, no front, os passos 1, 2 e o relatório de apuração atual funcionam; o passo 3 só acontece por API/Swagger
ou no seed (o seed classifica pelo cache e calcula na inicialização, `tribia.seed.calcular=true`).

## O que depende de validação fiscal

Lista completa em [04-INTELIGENCIA-FISCAL.md](04-INTELIGENCIA-FISCAL.md) e `PENDENCIAS.md` (V1–V6, S1–S10).
Destaques: exclusão de ICMS/ISS/PIS/Cofins da base de 2027; alíquota CBS 9,43% (estimativa); redução da CBS na
transição (0 por ora); crédito de fornecedor do Simples; classificação de medicamentos.
