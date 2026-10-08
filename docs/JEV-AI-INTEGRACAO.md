# Integração da JEV AI — guia para quem vai implementar

Estado em 08/10/2026. A Inteligência Fiscal (sugestão de NCM) já funciona de ponta a ponta com o Gemini; a JEV AI
é a única peça que falta. Este guia diz onde ela entra, o que recebe, o que devolve e o que ainda precisa ser decidido.

## 1. O que já está pronto

| Parte | Onde | Situação |
|---|---|---|
| 4 endpoints do contrato do front | `controller/AnaliseFiscalController` | Prontos e testados |
| Processamento em etapas, em segundo plano | `service/fiscal/ProcessadorAnaliseFiscal` | Pronto |
| Gemini interpreta a mercadoria e propõe até 4 NCMs | `service/fiscal/PesquisaNcmIa` + `resources/prompt-ncm.txt` | Pronto (testado com o Gemini real) |
| Verificações da NCM sugerida | `service/fiscal/ValidadorNcm` | Pronto (vigência na TIPI: não realizada, ver §7) |
| **Ponto de encaixe da JEV** | `service/fiscal/AvaliadorJev` (interface) | **Pronto, falta a implementação** |
| Telas (nova análise, acompanhamento, resultado, histórico) | `frontend/src/pages/fiscal/` | Prontas, já mostram a pontuação quando ela existir |

Contrato da API com o front: `frontend/docs/inteligencia-fiscal-api.md`.

## 2. Onde a JEV entra no fluxo

```
AGUARDANDO → INTERPRETANDO → PESQUISANDO_NCM (Gemini) → AVALIANDO (JEV) → VALIDANDO → GERANDO_RELATORIO → CONCLUIDA
                                                                                              ou AGUARDANDO_REVISAO
```

Na etapa AVALIANDO, o processador chama `AvaliadorJev.avaliar(...)` uma vez por análise, com todas as candidatas
que o Gemini propôs. A pontuação devolvida aparece na tabela "Classificações avaliadas" da tela de resultado.

## 3. O contrato (`AvaliadorJev`)

```java
public interface AvaliadorJev {
    boolean disponivel();
    Map<String, Pontuacao> avaliar(MercadoriaParaJev mercadoria, List<Candidata> candidatas);
}
```

**Entrada**

| Campo | Conteúdo |
|---|---|
| `MercadoriaParaJev.nome`, `descricao` | O que a pessoa digitou (obrigatórios) |
| `composicao`, `finalidade`, `caracteristicasInformadas` | Opcionais, podem vir `null` |
| `caracteristicasInterpretadas` | Lista de características que o Gemini extraiu (ex.: "sabão em barra") |
| `Candidata.ncm` | 8 dígitos, sem pontos (ex.: `34011190`) |
| `Candidata.descricao` | Texto do código segundo o Gemini (não é o texto oficial da TIPI) |

A NCM que a empresa usa hoje e os anexos **não** são passados para a JEV hoje. Se ela precisar deles, peça e
acrescentamos ao `MercadoriaParaJev`.

**Saída:** `Map<ncm, Pontuacao>`, com `Pontuacao(valor, escala, significado)`:

- `valor`: `BigDecimal` (ex.: `0.82`);
- `escala`: texto (ex.: `"0 a 1"`);
- `significado`: o que a nota mede, em uma frase (ex.: "Compatibilidade entre a descrição e o texto do código").

Uma NCM ausente no mapa aparece sem pontuação ("—"). O front mostra "0,82 (escala 0 a 1)", nunca porcentagem,
e avisa que não é probabilidade de acerto. **Não devolva a pontuação como chance de a NCM estar certa.**

## 4. Como plugar

1. Crie uma classe em `backend/src/main/java/br/com/tribia/service/fiscal/` (ou num pacote seu) que implemente
   `AvaliadorJev` e anote com `@Component`.
2. Pronto: o processador passa a usá-la sozinho. Enquanto ela não existir, ele usa `JevIndisponivel` (sem
   pontuação, com a limitação "JEV AI ainda não disponível" na tela). Só pode haver **um** bean `AvaliadorJev`.
3. `disponivel()` devolve `false` quando a JEV não estiver configurada (ex.: sem URL/chave): a análise segue sem
   pontuação, como hoje.

Exemplo, se a JEV for um serviço HTTP (siga o padrão de `config/RestClientConfig` e `config/LlmProperties`):

```java
@ConfigurationProperties(prefix = "tribia.jev")
public record JevProperties(String url, String apiKey, Duration timeoutConexao, Duration timeoutResposta) {}

@Component
public class JevHttp implements AvaliadorJev {
    private final RestClient http;          // bean com timeouts, criado como o calculadoraRestClient
    private final JevProperties props;
    // construtor...

    @Override public boolean disponivel() {
        return props.url() != null && !props.url().isBlank();
    }

    @Override public Map<String, Pontuacao> avaliar(MercadoriaParaJev m, List<Candidata> candidatas) {
        try {
            // chame a JEV e converta a resposta em Map<ncm, Pontuacao>
        } catch (RestClientException e) {
            throw new JevIndisponivelException("JEV fora do ar", e);
        }
    }
}
```

## 5. Regras que a implementação precisa respeitar

- **Timeouts são obrigatórios.** O processador não corta a chamada. As análises rodam num pool de 2 threads
  (`tribia.fiscal.threads`): uma JEV travada segura a fila inteira. Use algo como 2 s de conexão e 20 s de resposta.
- **Falha não derruba a análise.** Qualquer exceção é capturada: a análise termina sem pontuação e com a limitação
  "A JEV AI não respondeu". Prefira lançar `JevIndisponivelException` com uma mensagem clara.
- **Roda em segundo plano, sem usuário logado.** Não use `AcessoService` nem `SecurityContextHolder` dentro da JEV:
  a autorização da empresa já foi feita antes de a análise começar.
- **Nada de segredo no repositório.** Chave e URL vão em variáveis de ambiente: no `.env` da raiz para rodar local
  (ex.: `JEV_API_KEY=...`) e no painel do Render em produção. Use `tribia.jev.api-key=${JEV_API_KEY:}` no
  `application.properties`, como já é feito com o Gemini.
- **Testes não chamam a JEV real**, assim como já não chamam o Gemini real (custo e resultado variável).

## 6. Decisões que ainda precisam ser tomadas (com o responsável)

1. **A JEV muda a NCM sugerida?** Hoje não: a sugestão principal é a de maior confiança do Gemini e a JEV só exibe a
   nota de cada candidata. Se a JEV deve reordenar, desempatar ou rebaixar a sugestão (ex.: pontuação baixa →
   "Aguardando revisão"), isso é uma mudança pequena em `ProcessadorAnaliseFiscal`, mas é uma regra de produto.
2. **A JEV entra na validação?** Ex.: verificação "Compatibilidade pela JEV" com ALERTA abaixo de um limite.
3. **Escala e limites:** qual a escala oficial da pontuação e a partir de que valor ela é considerada baixa.

## 7. O que ainda falta na Inteligência Fiscal (não depende da JEV)

| Item | Efeito hoje | O que resolveria |
|---|---|---|
| Base da TIPI | "Existência e vigência na TIPI" aparece como **não realizada** em toda análise | Tabela oficial da NCM/TIPI com vigência no projeto |
| Leitura de PDF/imagem/Office | A IA só lê anexos `.txt`; os outros são listados como não lidos | Enviar os arquivos ao Gemini (o `LlmClient` hoje só aceita texto) |
| Texto oficial do código | A descrição vem do Gemini (com aviso) | A mesma base da TIPI |
| Relatório em PDF | `relatorio.disponivel` vem `false` | Endpoint opcional `GET /api/analises-fiscais/{id}/relatorio` |
| Processamento em memória | Reinício do servidor marca as análises em andamento como FALHA | Fila persistente, se o volume crescer |

## 8. Como rodar e testar

- **Gemini local:** `GEMINI_API_KEY=...` no `.env` da raiz (fora do Git). Sem a chave, a análise termina em FALHA
  com a mensagem "A IA não está configurada".
- **Backend:** `cd backend; .\mvnw.cmd spring-boot:run` → API em http://localhost:8090.
- **Front:** `cd frontend; npm run dev` → http://localhost:5173, menu "Inteligência Fiscal" da empresa.
- **Testes do backend:** `.\mvnw.cmd test "-Dtest=AnaliseFiscalControllerTest*"`. A classe aninhada `ComJev` já
  mostra como simular uma JEV num teste (`@TestConfiguration` com um bean `AvaliadorJev`); use-a como modelo.
- **E2E no navegador:** `frontend/scripts/inteligencia-fiscal-e2e.mjs` (instruções no cabeçalho). Usa a IA real
  se a chave estiver configurada.
- **Suíte completa** (deve continuar com 0 falhas): `.\mvnw.cmd test`.

## 9. Arquivos para ler, nesta ordem

1. `service/fiscal/AvaliadorJev.java`: o contrato.
2. `service/fiscal/ProcessadorAnaliseFiscal.java`: método `avaliarComJev` e o fluxo.
3. `dto/fiscal/ResultadoAnaliseFiscal.java`: `Pontuacao` e `Alternativa`.
4. `test/.../controller/AnaliseFiscalControllerTest.java`: classe `ComJev`.
5. `frontend/src/components/fiscal/Resultado.tsx`: função `Alternativas`, como a nota aparece na tela.
