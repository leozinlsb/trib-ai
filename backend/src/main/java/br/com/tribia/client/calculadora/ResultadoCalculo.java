package br.com.tribia.client.calculadora;

import br.com.tribia.service.apuracao.Tributos2027;

import java.math.BigDecimal;
import java.util.List;

/**
 * Resultado do cálculo.
 *
 * @param simulado true quando a calculadora usou alíquotas informadas por nós (CBS 2027 ainda não é oficial)
 * @param avisos   mensagens para o resumo (ex.: NCM desconhecido pela calculadora)
 */
public record ResultadoCalculo(OrigemCalculo origem, boolean simulado, List<ItemCalculado> itens, List<String> avisos) {

    /** Valores e alíquotas de um item, para guardar no Calculo e mostrar na tela. */
    public record ItemCalculado(int numero, Tributos2027 tributos, AliquotasAplicadas aliquotas) {
    }

    /**
     * Alíquotas usadas no cálculo, em %. Reduções null quando o item não tem redução.
     * Alíquotas efetivas = nominal x (100 - redução) / 100.
     */
    public record AliquotasAplicadas(BigDecimal pCbs, BigDecimal pIbsUf, BigDecimal pIbsMun,
                                     BigDecimal reducaoCbs, BigDecimal reducaoIbs, BigDecimal pIs) {
    }
}
