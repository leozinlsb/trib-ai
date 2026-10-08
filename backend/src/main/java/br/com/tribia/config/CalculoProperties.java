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
 * @param creditoCompraDivergente  crédito de 2027 numa compra cujo enquadramento (cClassTrib) corrigido pela revisão
 *                                 difere do que o fornecedor destacou na nota. Padrão MENOR (conservador)
 */
@ConfigurationProperties(prefix = "tribia.calculo")
public record CalculoProperties(Modo modo, LocalDate dataFatoGerador, boolean excluirTributosDaBase,
                                CreditoSimples creditoFornecedorSimples, CreditoDivergente creditoCompraDivergente) {

    public CalculoProperties {
        if (creditoCompraDivergente == null) {
            creditoCompraDivergente = CreditoDivergente.MENOR;
        }
    }

    public enum Modo {
        AUTO, OFICIAL, SIMPLIFICADA
    }

    public enum CreditoDivergente {
        /** O crédito segue o código destacado na nota; a correção vira só divergência. */
        NOTA,
        /** O crédito segue o código corrigido na revisão (otimista: supõe que o fornecedor corrija a nota). */
        REVISAO,
        /** O menor crédito entre os dois (conservador). */
        MENOR
    }

    public enum CreditoSimples {
        /** Conservador: não considera crédito (imposto de 2027 nunca fica subestimado). */
        SEM_CREDITO,
        /** Otimista: credita como se o fornecedor fosse do regime regular. */
        INTEGRAL
    }
}
