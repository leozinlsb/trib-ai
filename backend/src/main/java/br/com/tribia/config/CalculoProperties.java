package br.com.tribia.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.LocalDate;

/**
 * @param modo            AUTO = calculadora oficial e, se ela falhar, a simplificada; OFICIAL = só a oficial;
 *                        SIMPLIFICADA = só o plano B (útil sem a calculadora rodando e nos testes)
 * @param dataFatoGerador data usada para simular as regras de 2027 em todas as notas
 */
@ConfigurationProperties(prefix = "tribia.calculo")
public record CalculoProperties(Modo modo, LocalDate dataFatoGerador) {

    public enum Modo {
        AUTO, OFICIAL, SIMPLIFICADA
    }
}
