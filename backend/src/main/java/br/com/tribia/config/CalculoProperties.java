package br.com.tribia.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.LocalDate;

/**
 * @param modo                     AUTO = calculadora oficial e, se ela falhar, a simplificada; OFICIAL = só a oficial;
 *                                 SIMPLIFICADA = só o plano B (útil sem a calculadora rodando e nos testes)
 * @param dataFatoGerador          data usada para simular as regras de 2027 em todas as notas
 * @param excluirTributosDaBase    tira ICMS, PIS e Cofins da base do IBS/CBS (LC 214, art. 12, § 2º, V: vale de
 *                                 2026 a 2032)
 * @param creditoFornecedorSimples crédito de 2027 nas compras de fornecedor do Simples Nacional: o real é limitado
 *                                 ao que ele recolheu no Simples (LC 214, art. 47), valor que a nota não informa
 */
@ConfigurationProperties(prefix = "tribia.calculo")
public record CalculoProperties(Modo modo, LocalDate dataFatoGerador, boolean excluirTributosDaBase,
                                CreditoSimples creditoFornecedorSimples) {

    public enum Modo {
        AUTO, OFICIAL, SIMPLIFICADA
    }

    public enum CreditoSimples {
        /** Conservador: não considera crédito (imposto de 2027 nunca fica subestimado). */
        SEM_CREDITO,
        /** Otimista: credita como se o fornecedor fosse do regime regular. */
        INTEGRAL
    }
}
