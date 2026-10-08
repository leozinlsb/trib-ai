package br.com.tribia.client.llm;

import br.com.tribia.config.LlmProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.never;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class GeminiClientTest {

    static final String BASE = "http://gemini.teste/v1beta";
    static final String PRIMEIRO = BASE + "/models/modelo-a:generateContent";
    static final String SEGUNDO = BASE + "/models/modelo-b:generateContent";
    static final Map<String, Object> ESQUEMA = Map.of("type", "ARRAY");

    static String resposta(String texto) {
        return "{\"candidates\":[{\"content\":{\"role\":\"model\",\"parts\":[{\"text\":" + json(texto)
                + "}]},\"finishReason\":\"STOP\"}],\"usageMetadata\":{\"totalTokenCount\":10}}";
    }

    static String json(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    MockRestServiceServer servidor;

    GeminiClient cliente(String apiKey) {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE);
        servidor = MockRestServiceServer.bindTo(builder).build();
        var props = new LlmProperties(BASE, List.of("modelo-a", "modelo-b"), apiKey, Duration.ofSeconds(1),
                Duration.ofSeconds(1), 40);
        return new GeminiClient(builder.build(), props);
    }

    @Test
    void enviaChaveNoCabecalhoJsonEstruturadoETemperaturaZero() {
        GeminiClient c = cliente("chave-secreta");
        servidor.expect(requestTo(PRIMEIRO))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("x-goog-api-key", "chave-secreta"))
                .andExpect(jsonPath("$.systemInstruction.parts[0].text").value("regras"))
                .andExpect(jsonPath("$.contents[0].role").value("user"))
                .andExpect(jsonPath("$.contents[0].parts[0].text").value("itens"))
                .andExpect(jsonPath("$.generationConfig.temperature").value(0))
                .andExpect(jsonPath("$.generationConfig.responseMimeType").value("application/json"))
                .andExpect(jsonPath("$.generationConfig.responseSchema.type").value("ARRAY"))
                .andRespond(withSuccess(resposta("[{\"nItem\":1}]"), MediaType.APPLICATION_JSON));

        assertThat(c.gerarJson("regras", "itens", ESQUEMA)).isEqualTo("[{\"nItem\":1}]");
        servidor.verify();
    }

    @Test
    void chaveNaoVaiNaUrlEPartesDePensamentoSaoIgnoradas() {
        GeminiClient c = cliente("chave-secreta");
        servidor.expect(requestTo(PRIMEIRO)).andRespond(withSuccess(
                "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"raciocinando...\",\"thought\":true},{\"text\":\"[]\"}]},"
                        + "\"finishReason\":\"STOP\"}]}", MediaType.APPLICATION_JSON));

        assertThat(c.gerarJson("r", "p", ESQUEMA)).isEqualTo("[]");
    }

    @Test
    void semChaveNaoFazNenhumaChamada() {
        GeminiClient c = cliente("");
        servidor.expect(never(), requestTo(PRIMEIRO));

        assertThatThrownBy(() -> c.gerarJson("r", "p", ESQUEMA))
                .isInstanceOfSatisfying(LlmException.class, e -> assertThat(e.getTipo()).isEqualTo(LlmException.Tipo.NAO_CONFIGURADO))
                .hasMessageContaining("GEMINI_API_KEY");
        servidor.verify();
    }

    @Test
    void sobrecargaNoPrimeiroModeloPassaParaOSegundo() {
        GeminiClient c = cliente("k");
        servidor.expect(requestTo(PRIMEIRO)).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE).body("{}"));
        servidor.expect(requestTo(SEGUNDO)).andRespond(withSuccess(resposta("[1]"), MediaType.APPLICATION_JSON));

        assertThat(c.gerarJson("r", "p", ESQUEMA)).isEqualTo("[1]");
        servidor.verify();
    }

    @Test
    void cotaEsgotadaETimeoutTambemPassamParaOProximo() {
        GeminiClient c = cliente("k");
        servidor.expect(requestTo(PRIMEIRO)).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).body("{}"));
        servidor.expect(requestTo(SEGUNDO)).andRespond(req -> {
            throw new SocketTimeoutException("Read timed out");
        });

        assertThatThrownBy(() -> c.gerarJson("r", "p", ESQUEMA))
                .isInstanceOfSatisfying(LlmException.class, e -> assertThat(e.getTipo()).isEqualTo(LlmException.Tipo.INDISPONIVEL))
                .hasMessageContaining("modelo-b");
    }

    @Test
    void chaveRecusadaInterrompeSemTentarOutroModelo() {
        GeminiClient c = cliente("errada");
        servidor.expect(requestTo(PRIMEIRO)).andRespond(withStatus(HttpStatus.FORBIDDEN).body("{}"));
        servidor.expect(never(), requestTo(SEGUNDO));

        assertThatThrownBy(() -> c.gerarJson("r", "p", ESQUEMA))
                .isInstanceOfSatisfying(LlmException.class, e -> assertThat(e.getTipo()).isEqualTo(LlmException.Tipo.NAO_CONFIGURADO))
                .hasMessageContaining("recusou a chave");
        servidor.verify();
    }

    @Test
    void respostaCortadaOuVaziaEhInutilizavelETentaOProximoModelo() {
        GeminiClient c = cliente("k");
        servidor.expect(requestTo(PRIMEIRO)).andRespond(withSuccess(
                "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"[{\"}]},\"finishReason\":\"MAX_TOKENS\"}]}",
                MediaType.APPLICATION_JSON));
        servidor.expect(requestTo(SEGUNDO)).andRespond(withSuccess("{\"candidates\":[]}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> c.gerarJson("r", "p", ESQUEMA))
                .isInstanceOfSatisfying(LlmException.class, e -> assertThat(e.getTipo()).isEqualTo(LlmException.Tipo.INDISPONIVEL))
                .hasMessageContaining("sem candidatos");
    }

    @Test
    void propriedadesNaoVazamAChaveNoToString() {
        var props = new LlmProperties(BASE, List.of("m"), "chave-secreta", Duration.ofSeconds(1), Duration.ofSeconds(1), 40);

        assertThat(props.toString()).doesNotContain("chave-secreta").contains("***");
    }
}
