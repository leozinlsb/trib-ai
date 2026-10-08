package br.com.tribia.model;

import java.math.BigDecimal;

/**
 * Efeito da classificação (cClassTrib) na CBS/IBS, derivado da tabela oficial.
 * Usado no painel ("distribuição do faturamento por regime").
 */
public enum RegimeTributario {
    /** Tributação integral (redução 0%). */
    INTEGRAL,
    /** Alíquota reduzida (ex.: 30%, 40%, 60%). */
    REDUZIDA,
    /** Redução de 100% (ex.: cesta básica nacional, Anexo I). */
    ALIQUOTA_ZERO,
    /** Sem alíquota na tabela oficial: isenção, imunidade, suspensão, não incidência. */
    SEM_INCIDENCIA,
    /** Alíquota fixa ou uniforme (regimes específicos): fora do escopo do cálculo simplificado. */
    OUTRO;

    public static RegimeTributario de(String tipoAliquota, BigDecimal reducaoCbs) {
        if (tipoAliquota != null && tipoAliquota.startsWith("Sem al")) {
            return SEM_INCIDENCIA;
        }
        if (tipoAliquota == null || !tipoAliquota.startsWith("Padr")) {
            return OUTRO;
        }
        int r = reducaoCbs.compareTo(BigDecimal.ZERO);
        if (r == 0) {
            return INTEGRAL;
        }
        return reducaoCbs.compareTo(BigDecimal.valueOf(100)) >= 0 ? ALIQUOTA_ZERO : REDUZIDA;
    }
}
