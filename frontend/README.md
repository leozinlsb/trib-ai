# TribIA — Frontend

React 19 + TypeScript + Vite. Consome a API do backend em [`../backend`](../backend).

```bash
npm install
npm run dev        # http://localhost:5173  (o backend precisa estar em http://localhost:8090)
npm run build      # checagem de tipos + build de produção em dist/
npm run lint
npm run preview    # serve o dist/ com o mesmo proxy de /api
```

O navegador chama `/api/...` no próprio Vite, que repassa para o backend (`TRIBIA_BACKEND_URL`, padrão
`http://localhost:8090`). Veja [`.env.example`](.env.example). A sessão é um cookie HttpOnly do servidor: o
navegador não guarda senha nem token; só o token CSRF (cookie `XSRF-TOKEN`), enviado no header `X-XSRF-TOKEN`.

Para entrar, use o administrador criado pelo backend (veja "Acesso" no [README principal](../README.md)).

## Rotas

| Rota | Quem | Conteúdo |
|---|---|---|
| `/` | público | Landing page |
| `/login` | público | Login (`/cadastro` redireciona para cá: as contas são criadas pelo administrador) |
| `/entrar` | público | "Acessar TribIA": vai ao sistema se houver sessão, senão ao login |
| `/dashboard` | admin | Visão geral de todas as empresas (usuário de empresa é levado à própria empresa) |
| `/dashboard/empresas` | admin | Cadastro, edição, desativação/reativação e busca de empresas |
| `/dashboard/documentos` | admin | Notas de todas as empresas, com filtro |
| `/dashboard/relatorios` | admin | Relatório de cada empresa |
| `/dashboard/configuracoes` | admin | Conta, preferências e dados |
| `/dashboard/empresas/:id` | admin e a própria empresa | Início da empresa (indicadores, próximo passo, último período) |
| `/dashboard/empresas/:id/documentos[/:nota]` | admin e a própria empresa | Envio de notas, envios recentes, lista e detalhe |
| `/dashboard/empresas/:id/analises` | admin e a própria empresa | Relatório, classificação dos itens e relacionamentos |
| `/dashboard/empresas/:id/configuracoes` | admin e a própria empresa | Dados cadastrais, acessos (admin) e preferências |

| `/dashboard/inteligencia-fiscal` | admin | Escolha da empresa para as análises fiscais |
| `/dashboard/empresas/:id/inteligencia-fiscal[/nova\|/:analise]` | admin e a própria empresa | Inteligência Fiscal: histórico, nova análise, acompanhamento e resultado |

**Inteligência Fiscal:** ligada ao backend (contrato em [`docs/inteligencia-fiscal-api.md`](docs/inteligencia-fiscal-api.md)
e `src/api/inteligenciaFiscal.ts`): análise com Gemini, vigência da NCM pela tabela oficial, relatório em PDF e
indicadores no início da empresa. Se o servidor não tiver o serviço, as telas mostram "Análise fiscal ainda não disponível". Em
`npm run dev` existe a rota `.../inteligencia-fiscal/exemplo`, com dados fictícios marcados, para revisar o
layout do resultado. Ela não entra no build de produção.

As rotas são protegidas no front (`RotaProtegida`, `SoAdmin`, `AmbienteEmpresa`), mas quem garante o isolamento
é o backend: cada endpoint verifica a empresa do usuário da sessão.

A prévia do hero usa capturas reais do painel em `public/landing/` (`painel.jpg`, `rede.jpg`, `kpi-documentos.jpg`).

## Estrutura

```
src/
  api/          client.ts (fetch, CSRF, sessão expirada), tribia.ts (endpoints), types.ts (DTOs do backend)
  state/        contextos: autenticação, dados, envios/notificações, preferências, toasts
  components/   layout (sidebar, topbar com seletor de empresa), ui, charts, upload, empresas,
                relatorio, analises, painel (blocos do painel)
  hooks/        useEmpresa (empresa da URL), useDetalhes (cache de notas), useCobertura (itens classificados)
  lib/          formatação pt-BR, rotas, CNPJ, agregações por período, grafo
  pages/        Login, landing/, admin/ (VisaoGeral, Empresas, Relatorios, Configuracoes),
                empresa/ (InicioEmpresa, AnalisesRelatorios, ConfiguracoesEmpresa), Documentos, NotaDetalhe
  styles/       tokens, base, layout, components, relatorio (inclui impressão), auth, landing
```

## Endpoints usados

| Uso | Endpoint |
|---|---|
| Sessão | `POST /api/auth/login`, `POST /api/auth/logout`, `GET /api/auth/me`, `GET /api/auth/csrf` |
| Empresas | `GET/POST /api/clientes`, `GET/PUT/DELETE /api/clientes/{id}`, `POST /api/clientes/{id}/reativar` |
| Acessos | `GET/POST /api/clientes/{id}/usuarios`, `DELETE /api/usuarios/{id}` |
| Notas | `GET /api/clientes/{id}/notas?tipo=&competencia=`, `GET /api/notas/{id}`, `POST /api/clientes/{id}/notas` |
| Relatório | `GET /api/clientes/{id}/relatorio?de=AAAA-MM&ate=AAAA-MM` |
| Classificação e cálculo | `POST /api/notas/{id}/classificar`, `POST /api/notas/{id}/calcular`, `PUT /api/notas/{id}/pagamento?confirmado=`, `POST /api/clientes/{id}/calcular`, `GET /api/clientes/{id}/dashboard` |
| Revisão | `GET /api/clientes/{id}/revisao`, `PUT /api/itens/{id}/classificacao`, `GET /api/classificacoes/opcoes` |
| Inteligência Fiscal | `GET/POST /api/clientes/{id}/analises-fiscais`, `.../indicadores`, `GET /api/analises-fiscais/{id}`, `GET /api/analises-fiscais/{id}/relatorio` (PDF) |

## Limitações

- Os valores de 2027 são simulação (alíquota da CBS estimada); a tela avisa.
- Relatórios são gerados na hora (não ficam armazenados). PDF: pelo "Imprimir / salvar PDF" do navegador.
- Não há exclusão definitiva de empresas (só desativação), recuperação de senha nem troca de senha pelo usuário.
- Notificações e a lista de envios ficam no navegador (por usuário); a API não guarda histórico de uploads.
- O banco padrão do backend é H2 em arquivo (`backend/data/`): os dados persistem entre reinícios.
