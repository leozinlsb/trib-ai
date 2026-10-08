package br.com.tribia.client.llm;

import br.com.tribia.config.LlmProperties;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Cliente da API Gemini (generateContent) com saída JSON estruturada e temperatura zero.
 * Tenta os modelos de tribia.llm.modelos em ordem: qualquer falha que não seja de credencial passa para o próximo.
 * Erros 401/403 interrompem na hora (a chave está errada, trocar de modelo não ajuda).
 */
@Component
public class GeminiClient implements LlmClient {

    private static final Logger log = LoggerFactory.getLogger(GeminiClient.class);

    private final RestClient http;
    private final LlmProperties props;

    public GeminiClient(@Qualifier("llmRestClient") RestClient http, LlmProperties props) {
        this.http = http;
        this.props = props;
    }

    @Override
    public String gerarJson(String instrucoes, String pedido, Map<String, Object> esquema) {
        if (!props.configurada()) {
            throw new LlmException(LlmException.Tipo.NAO_CONFIGURADO,
                    "IA não configurada: defina a variável de ambiente GEMINI_API_KEY.");
        }
        Requisicao req = new Requisicao(
                new Conteudo(null, List.of(new Parte(instrucoes))),
                List.of(new Conteudo("user", List.of(new Parte(pedido)))),
                new Geracao(0, "application/json", esquema));

        String ultimaFalha = "nenhum modelo configurado";
        for (String modelo : props.modelos()) {
            boolean novaTentativa = !props.esperaMaxima().isZero();
            for (int tentativa = 1; ; tentativa++) {
                try {
                    Resposta r = http.post().uri("/models/{modelo}:generateContent", modelo)
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("x-goog-api-key", props.apiKey())
                            .body(req)
                            .retrieve()
                            .body(Resposta.class);
                    return texto(r, modelo);
                } catch (RestClientResponseException e) {
                    int status = e.getStatusCode().value();
                    if (status == 401 || status == 403) {
                        throw new LlmException(LlmException.Tipo.NAO_CONFIGURADO,
                                "A API de IA recusou a chave (HTTP " + status + "). Confira a variável GEMINI_API_KEY.", e);
                    }
                    ultimaFalha = modelo + ": HTTP " + status;
                    // cota (429) e sobrecarga (503) costumam passar em segundos: espera e tenta o mesmo modelo uma vez
                    if ((status == 429 || status == 503) && novaTentativa && tentativa == 1) {
                        Duration espera = espera(e);
                        log.warn("IA: {} respondeu HTTP {}; nova tentativa em {} ms", modelo, status, espera.toMillis());
                        dormir(espera);
                        continue;
                    }
                    log.warn("IA: {} falhou (HTTP {}); tentando o próximo modelo", modelo, status);
                } catch (RestClientException e) {
                    ultimaFalha = modelo + ": " + e.getClass().getSimpleName();
                    log.warn("IA: {} indisponível ({}); tentando o próximo modelo", modelo, e.getMessage());
                } catch (LlmException e) {
                    if (e.getTipo() != LlmException.Tipo.RESPOSTA_INVALIDA) {
                        throw e;
                    }
                    ultimaFalha = modelo + ": " + e.getMessage();
                    log.warn("IA: {} deu resposta inutilizável ({}); tentando o próximo modelo", modelo, e.getMessage());
                }
                break;
            }
        }
        throw new LlmException(LlmException.Tipo.INDISPONIVEL,
                "A IA está indisponível no momento (" + ultimaFalha + "). Tente novamente em instantes.");
    }

    private static final Pattern RETRY_DELAY = Pattern.compile("\"retryDelay\"\\s*:\\s*\"(\\d+(?:\\.\\d+)?)s\"");

    /** O que a API pediu (cabeçalho Retry-After ou "retryDelay" no corpo), limitado a tribia.llm.espera-maxima. */
    private Duration espera(RestClientResponseException e) {
        Duration pedida = Duration.ofMillis(1500);
        String retryAfter = e.getResponseHeaders() == null ? null : e.getResponseHeaders().getFirst("Retry-After");
        try {
            if (retryAfter != null && retryAfter.matches("\\d+")) {
                pedida = Duration.ofSeconds(Long.parseLong(retryAfter));
            } else {
                Matcher m = RETRY_DELAY.matcher(e.getResponseBodyAsString());
                if (m.find()) {
                    pedida = Duration.ofMillis((long) (Double.parseDouble(m.group(1)) * 1000));
                }
            }
        } catch (NumberFormatException ignorada) {
            // fica a espera padrão
        }
        return pedida.compareTo(props.esperaMaxima()) > 0 ? props.esperaMaxima() : pedida;
    }

    private static void dormir(Duration d) {
        try {
            Thread.sleep(d.toMillis());
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    private static String texto(Resposta r, String modelo) {
        if (r == null || r.candidates() == null || r.candidates().isEmpty()) {
            String motivo = r != null && r.promptFeedback() != null ? r.promptFeedback().blockReason() : null;
            throw new LlmException(LlmException.Tipo.RESPOSTA_INVALIDA,
                    "resposta sem candidatos" + (motivo != null ? " (bloqueada: " + motivo + ")" : ""));
        }
        Candidato c = r.candidates().get(0);
        if (c.finishReason() != null && !"STOP".equals(c.finishReason())) {
            throw new LlmException(LlmException.Tipo.RESPOSTA_INVALIDA, "resposta interrompida (" + c.finishReason() + ")");
        }
        if (c.content() == null || c.content().parts() == null) {
            throw new LlmException(LlmException.Tipo.RESPOSTA_INVALIDA, "resposta vazia");
        }
        StringBuilder sb = new StringBuilder();
        for (Parte p : c.content().parts()) {
            if (p.text() != null && !Boolean.TRUE.equals(p.thought())) {
                sb.append(p.text());
            }
        }
        if (sb.isEmpty()) {
            throw new LlmException(LlmException.Tipo.RESPOSTA_INVALIDA, "resposta sem texto");
        }
        log.debug("IA: resposta de {} com {} caracteres", modelo, sb.length());
        return sb.toString();
    }

    // ---- contrato JSON da API ----

    record Requisicao(Conteudo systemInstruction, List<Conteudo> contents, Geracao generationConfig) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Conteudo(String role, List<Parte> parts) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Parte(String text, Boolean thought) {
        Parte(String text) {
            this(text, null);
        }
    }

    record Geracao(double temperature, String responseMimeType, Map<String, Object> responseSchema) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Resposta(List<Candidato> candidates, Feedback promptFeedback) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Candidato(Conteudo content, String finishReason) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Feedback(String blockReason) {
    }
}
