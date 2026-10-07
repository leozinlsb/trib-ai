package br.com.tribia.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;
import java.util.Set;

/**
 * Alíquotas usadas nas regras de apuração, em percentual (9.43 = 9,43%).
 * Ficam em application.properties porque a alíquota da CBS de 2027 ainda não é oficial.
 */
@ConfigurationProperties(prefix = "tribia.aliquotas")
public record AliquotasProperties(Hoje hoje, Ano2027 ano2027) {

    /**
     * PIS/Cofins não cumulativo (Lucro Real), usados para o crédito das entradas.
     *
     * @param cstComCredito CSTs de PIS/Cofins da nota de compra que dão direito a crédito.
     *                      Itens com alíquota zero, monofásicos na revenda, isentos etc. não geram crédito.
     */
    public record Hoje(BigDecimal pisNaoCumulativo, BigDecimal cofinsNaoCumulativo, Set<String> cstComCredito) {
    }

    /**
     * @param cbs           alíquota de referência da CBS
     * @param ibsUf         IBS estadual (simbólico em 2027)
     * @param ibsMun        IBS municipal (simbólico em 2027)
     * @param cbsEstimativa true enquanto a alíquota da CBS não for oficial (gera aviso no resumo)
     */
    public record Ano2027(BigDecimal cbs, BigDecimal ibsUf, BigDecimal ibsMun, boolean cbsEstimativa) {
    }
}
