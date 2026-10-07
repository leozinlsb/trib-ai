package br.com.tribia.service.apuracao;

import java.math.BigDecimal;

/**
 * Efeito da classificação (CST + cClassTrib) no cálculo de 2027, em percentual.
 * Vem da tabela oficial de cClassTrib (pRedCBS / pRedIBS) e, para o IS, da alíquota do produto.
 *
 * @param reducaoCbs percentual de redução da CBS (60 = paga 40% da alíquota; 100 = alíquota zero)
 * @param reducaoIbs percentual de redução do IBS
 * @param aliquotaIs alíquota do Imposto Seletivo; zero quando o item não está sujeito ao IS
 */
public record ParametrosClassificacao(BigDecimal reducaoCbs, BigDecimal reducaoIbs, BigDecimal aliquotaIs) {

    /** Tributação integral, sem IS. */
    public static final ParametrosClassificacao INTEGRAL =
            new ParametrosClassificacao(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);

    public ParametrosClassificacao {
        validarPercentual("reducaoCbs", reducaoCbs);
        validarPercentual("reducaoIbs", reducaoIbs);
        if (aliquotaIs == null || aliquotaIs.signum() < 0) {
            throw new IllegalArgumentException("aliquotaIs deve ser >= 0");
        }
    }

    private static void validarPercentual(String nome, BigDecimal v) {
        if (v == null || v.signum() < 0 || v.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new IllegalArgumentException(nome + " deve estar entre 0 e 100: " + v);
        }
    }
}
