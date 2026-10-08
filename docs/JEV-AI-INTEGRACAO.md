# Integração da JEV AI

Estado em 08/10/2026. A Inteligência Fiscal (sugestão de NCM) funciona de ponta a ponta com o Gemini. Para a JEV AI
existe agora um **adaptador HTTP implementado e testado contra a API pública documentada do Jev (TypeSafe)**, desligado
por padrão. Ele **não foi validado contra a API real**: nenhuma chamada foi feita (custo por token, sem autorização).

> **Confirmar com a equipe:** este adaptador assume que a "JEV AI" do projeto é o **Jev da TypeSafe**
> (`https://docs.typesafe.ai`), o único produto com esse nome que encontramos com API pública. Se a JEV AI for outro
> serviço (ex.: um modelo próprio do colaborador), o adaptador HTTP não serve; a interface `AvaliadorJev` continua
> sendo o ponto de encaixe (§4).

## 0. Para ativar hoje (passo a passo)

1. **No painel da TypeSafe** (`https://console.typesafe.ai/keys`): entrar na conta, criar uma API key e copiá-la.
   A documentação não descreve plano gratuito: confira na conta se há crédito/forma de pagamento ativa (o
   `/v1/systemone` é cobrado por token de entrada). Não há outra configuração no painel: o "avaliador" (as perguntas
   sim/não por NCM) é montado pelo TribIA a cada requisição.
2. **No servidor** (nunca no front nem no Git): `.env` da raiz com `JEV_API_KEY=<chave>` (ou variável de ambiente;
   no Render, em Environment). Reiniciar a API.
3. **Conferir sem custo:** `Configurações` (administrador) → card **JEV AI** → "Chave no servidor: Configurada".
   (ou `GET /api/admin/jev/status`; a chave nunca aparece).
4. **Teste real autorizado (cobrado, poucas centenas de tokens):** no mesmo card, "Testar conexão" → confirmar.
   Lista os modelos da conta e avalia um sabonete fictício contra duas NCMs (uma compatível, uma de celular). O
   resultado mostra modelo, tempo, tokens e se a JEV separou as duas. Alternativa por linha de comando:
   `.\mvnw.cmd test "-Dtest=JevContratoTest" "-Djev.contrato=true"` (com `JEV_API_KEY` no ambiente).
5. **Ativar nas análises:** variável de ambiente `TRIBIA_JEV_MODO=HTTP` (o Spring a mapeia para `tribia.jev.modo`;
   também pode ir no `.env` da raiz) e reiniciar. O card passa a mostrar "Ativa".
   Cada análise passa a fazer **uma** chamada à JEV.

## 1. O que existe

| Parte | Onde | Situação |
|---|---|---|
| Ponto de encaixe | `service/fiscal/AvaliadorJev` | Pronto |
| Adaptador HTTP do Jev (TypeSafe) | `service/fiscal/JevHttp` + `config/JevProperties`, `config/JevConfig` | **Implementado e testado com servidor simulado; não validado na API real** |
| Modo de desenvolvimento | `service/fiscal/JevSimulado` | Pontuações fictícias, marcadas como SIMULAÇÃO; recusado no profile `prod` |
| Sem JEV (padrão) | `service/fiscal/JevIndisponivel` | A análise segue sem pontuação e registra a limitação |
| Telas | `frontend/src/components/fiscal/Resultado.tsx` (`Alternativas`) | Mostram "0,92 (escala 0 a 1)", nunca porcentagem |

## 2. Contrato usado (fonte: https://docs.typesafe.ai/api, consultado em 08/10/2026)

- `POST https://api.typesafe.ai/v1/systemone`, cabeçalhos `Authorization: Bearer <chave>` e `Content-Type: application/json`.
- Corpo: `model` (fixado em `jev-1.13.0`; `jev-latest` muda sozinho), `state` (objeto com a mercadoria) e `questions`
  (mapa de perguntas). O TribIA faz **uma pergunta sim/não (`noul`) por NCM candidata**, numa única requisição:

```json
{
  "model": "jev-1.13.0",
  "state": { "mercadoria": "Sabonete", "descricao": "Sabonete em barra 90 g",
             "finalidade": "higiene pessoal", "caracteristicas_interpretadas": ["sabão em barra"] },
  "questions": {
    "ncm_34011190": { "type": "noul",
      "instructions": "A mercadoria descrita é compatível com o código NCM 3401.11.90 (...), considerando sua natureza, composição e finalidade?",
      "criteria": { "true": "A descrição da mercadoria corresponde ao texto e ao alcance do código.",
                    "false": "A mercadoria pertence a outro código ou a descrição não sustenta este." } }
  }
}
```

- Resposta: `{"model": "...", "answers": {"ncm_34011190": {"type": "noul", "noul": 0.92}}, "usage": {...}}`.
  O valor `noul` (0 a 1) vira `Pontuacao(valor, "0 a 1", significado)`, com o significado: *"Grau em que a JEV AI
  (jev-1.13.0) considera a descrição compatível com o código, numa pergunta sim/não. Não é a probabilidade de a NCM
  estar correta nem substitui a revisão profissional."*
- Validação da resposta: sem `answers` → falha da JEV (análise segue sem pontuação); pergunta com `type` diferente de
  `noul` ou valor fora de 0–1 → aquela NCM fica sem pontuação (nada é inventado).

**Semântica (documentação oficial, `primitives/noul`):** o `noul` é a probabilidade de "sim" à pergunta. Aqui a
pergunta é "a descrição é compatível com este código?": mede a compatibilidade do texto, **não** a probabilidade de a
classificação fiscal estar correta. O significado gravado em cada pontuação diz isso.

**Política (08/10/2026, pedida pela responsável):** a JEV **nunca troca a sugestão nem confirma a classificação**.
Ela entra na validação como verificação "Avaliação da JEV AI":

| Situação | Resultado |
|---|---|
| Sugestão do Gemini é a mais bem pontuada (ou a diferença é menor que `tribia.jev.margem-divergencia`, 0,20) e nota ≥ `tribia.jev.limite-baixo` (0,50) | OK, com o texto "isso não confirma a classificação fiscal". Não muda o status |
| Outra candidata supera a sugestão em 0,20 ou mais | ALERTA + **divergência** com as duas notas + pendência → análise vai para **revisão humana** |
| Sugestão com nota abaixo de 0,50 | ALERTA + pendência → revisão humana |
| JEV não pontuou a sugestão | NÃO REALIZADA |

As pontuações de todas as candidatas ficam gravadas (evidência) e aparecem na tabela "Classificações avaliadas" e no PDF.

## 3. Erros, tentativas e tempos

| Situação | Comportamento |
|---|---|
| 401/403 | Sem nova tentativa; limitação "A JEV AI recusou a chave". A chave nunca aparece em log ou mensagem |
| 422 | Sem nova tentativa ("pedido recusado") |
| 429, 529 (e 502/503/504) | Nova tentativa com espera crescente (0,5 s → 1 s …, teto 4 s), até `novas-tentativas` (2) |
| Timeout / fora do ar | Sem nova tentativa (a fila de análises tem só 2 threads) |
| Resposta ilegível | Falha da JEV; análise segue sem pontuação |

Timeouts: conexão 2 s, resposta 15 s. Logs registram só status e contagem, nunca o texto da mercadoria ou a chave.

## 4. Como ativar

```properties
# application.properties (já presente; padrão DESLIGADO)
tribia.jev.modo=HTTP            # DESLIGADO | HTTP | SIMULADO
tribia.jev.api-key=${JEV_API_KEY:${TYPESAFE_API_KEY:}}
tribia.jev.modelo=jev-1.13.0
```

- A chave vai **só** em variável de ambiente ou no `.env` da raiz (fora do Git): `JEV_API_KEY=...`.
- **Custo:** cobrado por token de entrada (US$ 0,042 por milhão segundo a documentação em 08/10/2026). Ativar o modo
  HTTP exige autorização do responsável.
- Em modo HTTP sem chave, nada é chamado e a análise registra que a JEV não está disponível.
- `SIMULADO` serve só para ver as telas: as pontuações são fictícias e a análise diz isso. O profile `prod` não sobe com ele.
- Se a JEV AI for outro serviço: crie um `@Component` que implemente `AvaliadorJev` (só pode haver um bean) e deixe
  `tribia.jev.modo=DESLIGADO`.

## 5. Testes (nenhum chama a API real sem autorização)

- `TesteConexaoJevTest` (6) e `JevControllerTest` (3): status sem chamada e sem expor a chave; teste exige
  `confirmarCusto=true`, ADMIN e CSRF; empresa recebe 403; 401 vira 502 sem a chave na mensagem.
- `ValidadorNcmJevTest` (5) e `AnaliseFiscalControllerTest.ComJevDivergente`: política de divergência.
- `JevContratoTest`: **API real**, pulado por padrão (exige `-Djev.contrato=true` e `JEV_API_KEY`).
- E2E `frontend/scripts/etapa6-if-e2e.mjs`: fluxo completo no navegador com **dublês locais** da TypeSafe e do Gemini
  (`scripts/stubs-ia-e2e.mjs`), exercitando o `JevHttp` real por HTTP: 7/7 em 08/10/2026.

- `JevHttpTest` (13): formato da requisição, cabeçalho, modelo fixado, 401/422 sem repetir, 429/529 com espera
  crescente, desistência, timeout, resposta sem `answers`, ilegível, valores fora do formato, NCM malformada, chave fora
  do `toString`, modo simulado identificado.
- `JevConfigTest` (2): modo simulado recusado no profile `prod`.
- `AnaliseFiscalControllerTest`: `ComJev` (bean de teste), `ComJevSimulada` e `ComJevHttpSemChave`.
- Os testes forçam `tribia.jev.modo=DESLIGADO` e chave vazia (`src/test/resources/config/application.properties`).

**Validação externa pendente:** uma chamada real autorizada, com uma mercadoria sintética, para confirmar que a API
aceita o formato e devolve `answers` como documentado.

## 6. Decisões que continuam com o responsável

1. A JEV AI é o Jev da TypeSafe? (premissa do adaptador)
2. ~~A pontuação deve reordenar ou mandar para revisão?~~ Decidido em 08/10: não reordena; divergência ou nota baixa
   mandam para revisão humana (§2). Os limites 0,50 e 0,20 são **valores iniciais**, ajustáveis por configuração.
3. Os limites iniciais (0,50 e 0,20) fazem sentido fiscalmente? Calibrar depois das primeiras análises reais.
4. Autorização de custo para o teste real e para ativar o modo HTTP.

## 7. Arquivos

`service/fiscal/AvaliadorJev.java`, `JevHttp.java`, `JevSimulado.java`, `JevIndisponivel.java`,
`config/JevProperties.java`, `config/JevConfig.java`, `ProcessadorAnaliseFiscal.avaliarComJev`,
`ValidadorNcm.avaliacaoDaJev`, `TesteConexaoJev.java`, `controller/JevController.java`; front
`components/fiscal/CardJev.tsx`. Testes: `JevHttpTest`, `JevConfigTest`, `TesteConexaoJevTest`, `JevControllerTest`,
`ValidadorNcmJevTest`, `JevContratoTest` (real, opt-in).
