# 06 — Guia de uso

Comandos para Windows (Git Bash) a partir da raiz do repositório; em PowerShell troque `export X=...` por
`$env:X = "..."`. Requisitos: Java 21 (o projeto usa `%USERPROFILE%\.jdks\temurin-21*`) e Node 18+.

## 1. Subir o backend

Uso normal (lê o `.env` da raiz, banco em arquivo `backend/data/`):

```bash
cd backend && ./mvnw spring-boot:run
```

Ensaio isolado, **sem dados reais e sem chamadas pagas** (banco em memória, sem ler `.env`, IA e JEV desligadas):

```bash
cd backend
export TRIBIA_ADMIN_SENHA="<senha-sintetica-com-12+-caracteres>"
./mvnw spring-boot:run -Dspring-boot.run.arguments="--server.port=8091 --spring.datasource.url=jdbc:h2:mem:apidemo --tribia.seed.enabled=false --tribia.arquivo-local=nao-existe --tribia.arquivo-env-raiz=nao-existe --tribia.arquivo-env-backend=nao-existe --tribia.llm.api-key= --tribia.jev.modo=DESLIGADO"
```

Nesse modo a análise termina em `FALHOU` com a mensagem "A IA não está configurada neste ambiente" — é o
comportamento honesto do motor (nenhum resultado é inventado). Para ver uma sugestão de NCM real é preciso o Gemini
configurado (`GEMINI_API_KEY`), o que **gera custo/cota** e exige autorização do responsável.

## 2. Emitir uma chave (ADMIN)

**Pela tela (recomendado):** entre como administrador → **Configurações** → seção **Integrações (API pública)** →
**Nova chave**: empresa, nome do integrador, permissões (todas marcadas por padrão) e, opcionalmente, validade e
limites. A chave completa aparece uma única vez, com botão de copiar; a janela só fecha depois de confirmar que ela
foi guardada. Na mesma seção ficam a lista (situação, uso do dia, último uso), o botão **Revogar** e um guia curto para
o integrador. A tela só existe para o administrador (rota `SoAdmin`), e o backend confere de novo.

**Por linha de comando** (automação local):

```bash
export TRIBIA_API_URL=http://localhost:8091          # ou 8090
export TRIBIA_API_KEY=$(node exemplos/api-publica/emitir-chave-local.mjs 1 "ERP Demo")
```

- Argumentos: `clienteId` (empresa à qual a chave fica presa) e nome do integrador.
- Opcionais: `TRIBIA_ESCOPOS=ANALISES_LER` (chave só de leitura), `TRIBIA_VALIDADE_DIAS=30`.
- A chave sai uma única vez no stdout. Guarde-a num cofre/variável de ambiente; nunca em arquivo versionado.
- Equivalente manual: logar como ADMIN na plataforma e chamar `POST /api/admin/chaves-api` com o cookie de sessão e o
  cabeçalho `X-XSRF-TOKEN` (ver Swagger, documento "Plataforma (interna)").
- Listar: `GET /api/admin/chaves-api?clienteId=1`. Revogar: `POST /api/admin/chaves-api/{id}/revogar`.

## 3. Abrir a documentação

`http://localhost:8091/swagger-ui.html` → seletor no topo → **API pública v1 (integradores)** → *Authorize* →
cole a chave em `chaveApi`. JSON bruto: `http://localhost:8091/v3/api-docs/publica-v1`.

## 4. Rodar o cliente de exemplo

```bash
node exemplos/api-publica/cliente-tribia.mjs                                   # produto padrão (sabonete)
node exemplos/api-publica/cliente-tribia.mjs exemplos/api-publica/produto-exemplo.json   # detergente
TRIBIA_SAIDA_JSON=1 node exemplos/api-publica/cliente-tribia.mjs               # JSON completo
TRIBIA_IDEMPOTENCY_KEY=pedido-1 node exemplos/api-publica/cliente-tribia.mjs   # rode 2x: a 2ª reaproveita
```

Códigos de saída: `0` com resultado; `2` terminou sem resultado (`FALHOU`/`INFORMACOES_INSUFICIENTES`); `1` erro.

## 5. Chamadas diretas (curl)

```bash
curl -s -X POST "$TRIBIA_API_URL/api/v1/analises" \
  -H "X-API-Key: $TRIBIA_API_KEY" -H "Idempotency-Key: $(node -e 'console.log(crypto.randomUUID())')" \
  -H "Content-Type: application/json" \
  -d '{"referenciaExterna":"SKU-1","mercadoria":{"nome":"Sabonete 90 g","descricao":"Sabonete em barra de glicerina para higiene pessoal."}}'

curl -s "$TRIBIA_API_URL/api/v1/analises/<id>" -H "X-API-Key: $TRIBIA_API_KEY"
curl -s "$TRIBIA_API_URL/api/v1/analises?referenciaExterna=SKU-1" -H "X-API-Key: $TRIBIA_API_KEY"
curl -s "$TRIBIA_API_URL/api/v1/uso" -H "X-API-Key: $TRIBIA_API_KEY"
```

## 6. Recomendações ao integrador

- Uma `Idempotency-Key` por pedido lógico (ex.: `SKU + versão do cadastro`); reenvie com a mesma em falhas de rede,
  429 de minuto e 503 só depois de nova chave (o 503 registra a solicitação como `FALHOU`).
- Consulte com intervalo crescente (1 s → 5 s); respeite `Retry-After` e `X-RateLimit-*`.
- Trate `resultado.natureza` e `revisaoHumana.situacao`: só `DECISAO_REVISAO_HUMANA` reflete decisão de pessoa.
- Exiba os `avisos` a quem usa o resultado. Pontuação da JEV não é probabilidade.

## 7. Configuração (`application.properties`)

| Propriedade | Padrão | Efeito |
|---|---|---|
| `tribia.api-publica.requisicoes-por-minuto` | 60 | Por chave, todas as rotas `/api/v1` |
| `tribia.api-publica.cota-diaria-analises` | 100 | Análises novas por chave por dia (Brasília) |
| `tribia.api-publica.max-analises-simultaneas` | 5 | Em processamento ao mesmo tempo, por chave |
| `tribia.api-publica.falhas-autenticacao-por-minuto` | 20 | Por IP, antes de 429 |
| `tribia.api-publica.validade-maxima-dias` | 365 | Padrão e teto da validade; 0 = sem validade |

Cada chave pode ter limites próprios na emissão (`requisicoesPorMinuto`, `cotaDiariaAnalises`,
`maxAnalisesSimultaneas`).
