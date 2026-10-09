package br.com.tribia.service.fiscal;

import br.com.tribia.config.JevProperties;
import br.com.tribia.dto.fiscal.ResultadoAnaliseFiscal.Pontuacao;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Adaptador da JEV AI pela API pública da TypeSafe (POST /v1/systemone, documentada em https://docs.typesafe.ai/api).
 *
 * Para cada NCM candidata faz uma pergunta sim/não ("noul") sobre a mesma mercadoria, numa única requisição:
 * a resposta é um número de 0 a 1 por pergunta. Ele vira a {@link Pontuacao} da alternativa, com escala e significado
 * explícitos — é o grau de compatibilidade avaliado pela JEV, não a probabilidade de a NCM estar certa.
 *
 * Falhas (chave recusada, pedido inválido, sobrecarga persistente, timeout, resposta fora do formato) viram
 * {@link JevIndisponivelException}: o processador segue a análise sem pontuação. 429 e 529 repetem com espera
 * crescente (como recomenda a documentação); os outros erros não repetem. Logs não levam chave nem texto da mercadoria.
 */
public class JevHttp implements AvaliadorJev {

    private static final Logger log = LoggerFactory.getLogger(JevHttp.class);
    static final String ESCALA = "0 a 1";

    private final RestClient http;
    private final JevProperties props;
    private final Consumer<Duration> espera;

    public JevHttp(RestClient http, JevProperties props) {
        this(http, props, JevHttp::dormir);
    }

    /** @param espera como esperar entre tentativas (os testes não dormem de verdade) */
    JevHttp(RestClient http, JevProperties props, Consumer<Duration> espera) {
        this.http = http;
        this.props = props;
        this.espera = espera;
    }

    @Override
    public boolean disponivel() {
        return props.chaveConfigurada();
    }

    @Override
    public Map<String, Pontuacao> avaliar(MercadoriaParaJev mercadoria, List<Candidata> candidatas) {
        return avaliarComDiagnostico(mercadoria, candidatas).pontuacoes();
    }

    /** O que a JEV respondeu, para o teste de conexão: modelo que respondeu, tokens cobrados e tempo. */
    public record Diagnostico(String modelo, Integer tokensEntrada, Integer tokensSaida, long milissegundos,
                              Map<String, Pontuacao> pontuacoes) {
    }

    public Diagnostico avaliarComDiagnostico(MercadoriaParaJev mercadoria, List<Candidata> candidatas) {
        if (!disponivel()) {
            throw new JevIndisponivelException("JEV AI sem chave configurada (JEV_API_KEY).", null);
        }
        List<Candidata> enviadas = candidatas.stream()
                .filter(c -> c.ncm() != null && c.ncm().matches("\\d{8}"))
                .distinct()
                .limit(props.maxCandidatas())
                .toList();
        if (enviadas.isEmpty()) {
            return new Diagnostico(null, null, null, 0, Map.of());
        }
        Map<String, Object> corpo = Map.of(
                "model", props.modelo(),
                "state", estado(mercadoria),
                "questions", perguntas(enviadas));
        long inicio = System.nanoTime();
        JsonNode resposta = enviar("/v1/systemone", corpo);
        long ms = (System.nanoTime() - inicio) / 1_000_000;
        JsonNode usage = resposta.path("usage");
        return new Diagnostico(resposta.path("model").asText(null),
                usage.path("input_tokens").isNumber() ? usage.get("input_tokens").asInt() : null,
                usage.path("output_tokens").isNumber() ? usage.get("output_tokens").asInt() : null,
                ms, interpretar(resposta, enviadas));
    }

    /**
     * GET /v1/models: modelos que a chave pode usar. Confere chave e conectividade sem enviar dados da mercadoria
     * (a documentação cobra por token de entrada do /v1/systemone e não informa custo para esta rota).
     */
    public List<String> modelos() {
        if (!disponivel()) {
            throw new JevIndisponivelException("JEV AI sem chave configurada (JEV_API_KEY).", null);
        }
        JsonNode r = enviar("/v1/models", null);
        List<String> nomes = new java.util.ArrayList<>();
        r.path("models").forEach(m -> {
            if (m.path("name").isTextual()) {
                nomes.add(m.get("name").asText());
            }
        });
        if (!r.path("models").isArray()) {
            throw new JevIndisponivelException("A JEV AI devolveu uma lista de modelos fora do formato.", null);
        }
        return nomes;
    }

    // ---------------- requisição ----------------

    /** corpo null = GET; senão POST com JSON. */
    private JsonNode enviar(String caminho, Map<String, Object> corpo) {
        Duration proxima = props.esperaInicial();
        for (int tentativa = 0; ; tentativa++) {
            try {
                RestClient.RequestHeadersSpec<?> req = corpo == null
                        ? http.get().uri(caminho)
                        : http.post().uri(caminho).contentType(MediaType.APPLICATION_JSON).body(corpo);
                JsonNode r = req.accept(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + props.apiKey())
                        .retrieve()
                        .body(JsonNode.class);
                if (r == null) {
                    throw new JevIndisponivelException("A JEV AI devolveu resposta vazia.", null);
                }
                return r;
            } catch (RestClientResponseException e) {
                int status = e.getStatusCode().value();
                boolean transitoria = status == 429 || status == 529 || status == 502 || status == 503 || status == 504;
                if (transitoria && tentativa < props.novasTentativas()) {
                    log.warn("JEV AI: HTTP {}; nova tentativa em {} ms", status, proxima.toMillis());
                    espera.accept(proxima);
                    proxima = min(proxima.multipliedBy(2), props.esperaMaxima());
                    continue;
                }
                log.warn("JEV AI: HTTP {} (tentativa {})", status, tentativa + 1);
                throw new JevIndisponivelException(switch (status) {
                    case 401, 403 -> "A JEV AI recusou a chave (HTTP " + status + "). Confira JEV_API_KEY.";
                    case 422 -> "A JEV AI recusou o pedido (HTTP 422): formato não aceito.";
                    case 429, 529 -> "A JEV AI está sobrecarregada ou no limite de uso (HTTP " + status + ").";
                    default -> "A JEV AI respondeu HTTP " + status + ".";
                }, e);
            } catch (ResourceAccessException e) {
                // timeout ou servidor fora do ar: não repete (a fila de análises tem poucas threads)
                log.warn("JEV AI indisponível: {}", e.getClass().getSimpleName());
                throw new JevIndisponivelException("A JEV AI não respondeu a tempo.", e);
            } catch (RestClientException e) {
                log.warn("JEV AI: resposta ilegível ({})", e.getClass().getSimpleName());
                throw new JevIndisponivelException("A JEV AI devolveu uma resposta ilegível.", e);
            }
        }
    }

    /** O "estado" avaliado: os dados que a pessoa informou e as características que a análise extraiu. */
    /** Teto por campo do "state": a JEV cobra por token e o texto é dado não confiável. */
    static final int MAX_CAMPO_ESTADO = 2000;

    /**
     * O "state" avaliado. Na API do Jev ele é separado das perguntas (que o TribIA escreve), mas o texto vem do
     * usuário: passa pela mesma neutralização usada no Gemini (sem marcação nem controles) e tem tamanho limitado.
     */
    static Map<String, Object> estado(MercadoriaParaJev m) {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("mercadoria", dado(m.nome()));
        s.put("descricao", dado(m.descricao()));
        if (m.composicao() != null) {
            s.put("composicao", dado(m.composicao()));
        }
        if (m.finalidade() != null) {
            s.put("finalidade", dado(m.finalidade()));
        }
        if (m.caracteristicasInformadas() != null) {
            s.put("caracteristicas_informadas", dado(m.caracteristicasInformadas()));
        }
        if (m.caracteristicasInterpretadas() != null && !m.caracteristicasInterpretadas().isEmpty()) {
            s.put("caracteristicas_interpretadas", m.caracteristicasInterpretadas().stream().limit(20).map(JevHttp::dado).toList());
        }
        return s;
    }

    static String dado(String texto) {
        String t = PesquisaNcmIa.neutralizar(texto).strip();
        return t.length() > MAX_CAMPO_ESTADO ? t.substring(0, MAX_CAMPO_ESTADO) : t;
    }

    static Map<String, Object> perguntas(List<Candidata> candidatas) {
        Map<String, Object> q = new LinkedHashMap<>();
        for (Candidata c : candidatas) {
            q.put(chave(c.ncm()), Map.of(
                    "type", "noul",
                    "instructions", "A mercadoria descrita é compatível com o código NCM " + formatar(c.ncm())
                            // a descrição vem da análise da IA: entra limpa e curta (as instruções são nossas)
                            + (c.descricao() == null || c.descricao().isBlank() ? ""
                            : " (" + PesquisaNcmIa.limitar(PesquisaNcmIa.neutralizar(c.descricao())) + ")")
                            + ", considerando sua natureza, composição e finalidade?",
                    "criteria", Map.of(
                            "true", "A descrição da mercadoria corresponde ao texto e ao alcance do código.",
                            "false", "A mercadoria pertence a outro código ou a descrição não sustenta este.")));
        }
        return q;
    }

    // ---------------- resposta ----------------

    /**
     * Valida o formato documentado ({"model", "answers": {chave: {"type": "noul", "noul": 0..1}}}). Resposta sem
     * "answers" é inválida (exceção); uma pergunta com tipo ou valor fora do formato fica sem pontuação.
     */
    Map<String, Pontuacao> interpretar(JsonNode r, List<Candidata> candidatas) {
        JsonNode answers = r.get("answers");
        if (answers == null || !answers.isObject()) {
            throw new JevIndisponivelException("A JEV AI devolveu uma resposta sem \"answers\".", null);
        }
        String modelo = r.path("model").asText(props.modelo());
        // a documentação define o "noul" como a probabilidade de "sim" à pergunta feita (aqui: a descrição é compatível
        // com o código?). É a leitura do modelo sobre o texto, não a probabilidade de a classificação fiscal estar certa.
        String significado = "Probabilidade, segundo a JEV AI (" + modelo + "), de \"sim\" à pergunta \"a descrição é "
                + "compatível com este código?\". Mede a compatibilidade do texto informado; não é a probabilidade de a "
                + "classificação fiscal estar correta nem substitui a revisão profissional.";
        Map<String, Pontuacao> pontuacoes = new LinkedHashMap<>();
        int descartadas = 0;
        for (Candidata c : candidatas) {
            JsonNode a = answers.get(chave(c.ncm()));
            if (a == null || !"noul".equals(a.path("type").asText()) || !a.path("noul").isNumber()) {
                descartadas++;
                continue;
            }
            double v = a.get("noul").asDouble();
            if (Double.isNaN(v) || v < 0 || v > 1) {
                descartadas++;
                continue;
            }
            pontuacoes.put(c.ncm(), new Pontuacao(BigDecimal.valueOf(v).setScale(2, RoundingMode.HALF_EVEN), ESCALA, significado));
        }
        if (descartadas > 0) {
            log.warn("JEV AI: {} de {} resposta(s) fora do formato descartada(s)", descartadas, candidatas.size());
        }
        return pontuacoes;
    }

    static String chave(String ncm) {
        return "ncm_" + ncm;
    }

    private static String formatar(String ncm) {
        return ncm.substring(0, 4) + "." + ncm.substring(4, 6) + "." + ncm.substring(6);
    }

    private static Duration min(Duration a, Duration b) {
        return a.compareTo(b) > 0 ? b : a;
    }

    private static void dormir(Duration d) {
        try {
            Thread.sleep(d.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new JevIndisponivelException("Espera interrompida.", e);
        }
    }
}
