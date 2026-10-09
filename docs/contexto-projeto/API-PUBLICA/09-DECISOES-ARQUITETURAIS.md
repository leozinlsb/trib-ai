# 09 — Decisões arquiteturais

Tomadas de forma autônoma em 09/10/2026 dentro do escopo autorizado; revisáveis pelo responsável.

| # | Decisão | Alternativas consideradas | Por quê |
|---|---|---|---|
| D1 | API pública **dentro do mesmo backend**, pacote `apipublica`, cadeia de segurança própria | Microsserviço/gateway separado | Missão proíbe microsserviço desnecessário; reaproveita o motor sem rede extra |
| D2 | Cada solicitação **reaproveita `AnaliseFiscal`** (1:1 com `SolicitacaoApi`) | Entidade de análise própria da API | Um só motor, uma só fila, retomada após reinício e revisão humana valem para os dois canais; a análise aparece na plataforma para a empresa revisar |
| D3 | Chave presa a **uma empresa** | Chave de integrador com várias empresas + campo empresa no corpo | Elimina por construção a escolha arbitrária de empresa; integrador com várias empresas recebe uma chave por empresa |
| D4 | Id público **UUID** separado do id interno | Expor o id numérico | Não enumerável; não revela volume; desacopla contrato do banco |
| D5 | Estado público `INFORMACOES_INSUFICIENTES` além dos 5 sugeridos | Fundir em `FALHOU` | O motor já distingue; para o integrador a ação é outra (mandar mais dados) |
| D6 | `X-API-Key` + SHA-256 + prefixo indexado | Bearer/JWT, OAuth2 client credentials, BCrypt | Simples para ERPs; 256 bits de entropia dispensam hash lento; OAuth2 fica como evolução |
| D7 | Gestão de chaves em rota **interna** de ADMIN (sessão + CSRF) + script local | Comando CLI no jar, endpoint público | Reusa autenticação existente; nada público emite chave; o script automatiza a demo |
| D8 | `ddl-auto=update` para as tabelas novas | Introduzir Flyway | Padrão atual do projeto; introduzir migrações agora mudaria a operação de todas as tabelas (fora do escopo). Registrado como pendência para produção |
| D9 | Idempotência: trava por chave (memória) + restrição única no banco; análise e solicitação na **mesma transação**; despacho **depois** do commit | Só restrição única; tabela de idempotência separada | A trava evita trabalho desperdiçado; a restrição protege entre instâncias; a perdedora faz rollback sem análise órfã e sem chamar a IA |
| D10 | Hash do corpo **normalizado** (trim, vazio=ausente, NCM só dígitos) | Hash do texto cru | Reenvio do mesmo pedido com formatação diferente não vira conflito falso |
| D11 | Repetição idempotente **não consome cota** e responde 202 com `Idempotent-Replayed: true` | 200; consumir cota | Reenvio por rede instável não deve custar nada; 202 mantém o mesmo tratamento no cliente |
| D12 | Limite por minuto **em memória**; cota diária e simultâneas **no banco** | Redis/Bucket4j | Sem dependência nova; o que custa dinheiro (análises) é contado de forma persistente |
| D13 | Sem anexos na v1 (JSON apenas) | Multipart como na tela | ERPs mandam dados estruturados; anexos exigem limites de tamanho/abuso próprios para a API; evolução registrada |
| D14 | Revisão humana **só na plataforma**; a API expõe a situação e a decisão | Endpoint público de revisão | A revisão é ato de pessoa responsável; quem revisou não sai na API (minimização) |
| D15 | `etapa` (status interno) exposta como campo **informativo** | Esconder | Ajuda o integrador a mostrar progresso; documentado que pode ganhar valores |
| D16 | Erros com `ProblemDetail` + `codigo` + `requestId` | Formato próprio | Mantém o padrão da plataforma (RFC 9457) e dá código estável para máquina |
| D17 | Testes da API pública em **contexto Spring próprio** (propriedade marcadora) | `@Transactional` nos testes | As transações precisam confirmar de verdade (concorrência/idempotência); o contexto próprio impede vazamento de dados para outros testes (problema real encontrado e corrigido) |
| D18 | Cliente de exemplo em **Node** | Python | Python não está instalado na máquina; Node 24 está (já usado pelo front) |
