package br.com.tribia.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Calculadora RTC oficial (offline). Ver README: ela roda à parte, em http://localhost:8080/api.
 *
 * @param url              endereço base da API, já com o context-path /api
 * @param timeoutConexao   tempo máximo para abrir a conexão
 * @param timeoutResposta  tempo máximo para a resposta de uma chamada
 */
@ConfigurationProperties(prefix = "tribia.calculadora")
public record CalculadoraProperties(String url, Duration timeoutConexao, Duration timeoutResposta) {
}
