package br.com.tribia.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;

/**
 * API de IA (Gemini). A chave vem SEMPRE de variável de ambiente (GEMINI_API_KEY): nunca vai para arquivo do projeto.
 *
 * @param modelos        em ordem de preferência; se um falhar (sobrecarga, cota, indisponível), tenta o próximo
 * @param itensPorChamada máximo de produtos por chamada; notas maiores são divididas em lotes
 */
@ConfigurationProperties(prefix = "tribia.llm")
public record LlmProperties(String url, List<String> modelos, String apiKey, Duration timeoutConexao,
                            Duration timeoutResposta, int itensPorChamada) {

    public boolean configurada() {
        return apiKey != null && !apiKey.isBlank();
    }

    /** Evita vazar a chave em logs. */
    @Override
    public String toString() {
        return "LlmProperties[url=" + url + ", modelos=" + modelos + ", apiKey=" + (configurada() ? "***" : "(vazia)") + "]";
    }
}
