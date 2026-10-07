package br.com.tribia.service.apuracao;

import java.math.BigDecimal;

/**
 * Débito e crédito de um período. Quando o crédito supera o débito, o resultado é saldo credor,
 * não imposto negativo: {@link #aPagar()} fica zero e {@link #saldoCredor()} mostra o excedente.
 */
public record Apuracao(BigDecimal debito, BigDecimal credito) {

    public static final Apuracao ZERO = new Apuracao(BigDecimal.ZERO.setScale(2), BigDecimal.ZERO.setScale(2));

    /** débito - crédito. Pode ser negativo. */
    public BigDecimal liquido() {
        return debito.subtract(credito);
    }

    public BigDecimal aPagar() {
        return liquido().max(BigDecimal.ZERO.setScale(2));
    }

    public BigDecimal saldoCredor() {
        return liquido().negate().max(BigDecimal.ZERO.setScale(2));
    }

    public boolean temSaldoCredor() {
        return liquido().signum() < 0;
    }

    public Apuracao somar(Apuracao outra) {
        return new Apuracao(debito.add(outra.debito), credito.add(outra.credito));
    }
}
