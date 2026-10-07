package br.com.tribia.service.apuracao;

import java.math.BigDecimal;

/** CBS, IBS (estadual e municipal) e IS de um item em 2027, já arredondados em 2 casas. */
public record Tributos2027(BigDecimal vCbs, BigDecimal vIbsUf, BigDecimal vIbsMun, BigDecimal vIs) {

    public static final Tributos2027 ZERO = new Tributos2027(
            BigDecimal.ZERO.setScale(2), BigDecimal.ZERO.setScale(2), BigDecimal.ZERO.setScale(2), BigDecimal.ZERO.setScale(2));

    public BigDecimal vIbs() {
        return vIbsUf.add(vIbsMun);
    }

    /** O que a saída gera de débito: CBS + IBS + IS. */
    public BigDecimal totalDebito() {
        return vCbs.add(vIbs()).add(vIs);
    }

    /** O que a entrada gera de crédito: CBS + IBS. O IS não gera crédito. */
    public BigDecimal totalCredito() {
        return vCbs.add(vIbs());
    }
}
