package br.com.tribia.service.apuracao;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Apuração de hoje (PIS/Cofins) x 2027 (CBS/IBS/IS) para o mesmo conjunto de notas. */
public record Comparativo(Apuracao hoje, Apuracao ano2027) {

    public static final Comparativo ZERO = new Comparativo(Apuracao.ZERO, Apuracao.ZERO);

    /**
     * (líquido 2027 - líquido hoje) / líquido hoje, em percentual com 2 casas (-12.34 = 12,34% a menos).
     * null quando o líquido de hoje é zero ou saldo credor: a variação não tem significado.
     */
    public BigDecimal variacaoPct() {
        BigDecimal base = hoje.liquido();
        if (base.signum() <= 0) {
            return null;
        }
        return ano2027.liquido().subtract(base)
                .multiply(BigDecimal.valueOf(100))
                .divide(base, 2, RoundingMode.HALF_EVEN);
    }

    public Comparativo somar(Comparativo outro) {
        return new Comparativo(hoje.somar(outro.hoje), ano2027.somar(outro.ano2027));
    }
}
