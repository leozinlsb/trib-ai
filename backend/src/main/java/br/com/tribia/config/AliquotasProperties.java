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
     * PIS/Cofins de hoje.
     *
     * @param pisNaoCumulativo/cofinsNaoCumulativo Lucro Real: crédito das compras e estorno na devolução
     * @param pisCumulativo/cofinsCumulativo       Lucro Presumido: estorno do débito na devolução de venda
     * @param cstComCredito                        CSTs de PIS/Cofins tributados (dão crédito na compra)
     */
    public record Hoje(BigDecimal pisNaoCumulativo, BigDecimal cofinsNaoCumulativo, BigDecimal pisCumulativo,
                       BigDecimal cofinsCumulativo, Set<String> cstComCredito) {
    }

    /**
     * @param cbs                  alíquota de referência da CBS
     * @param ibsUf                IBS estadual (simbólico em 2027)
     * @param ibsMun               IBS municipal (simbólico em 2027)
     * @param cbsEstimativa        true enquanto a alíquota da CBS não for oficial (gera aviso no resumo)
     * @param reducaoCbsTransicao  pontos percentuais descontados da CBS em 2027-2028, se a regra de transição
     *                             se confirmar (fontes secundárias citam 0,1 p.p.; o texto legal não foi conferido)
     */
    public record Ano2027(BigDecimal cbs, BigDecimal ibsUf, BigDecimal ibsMun, boolean cbsEstimativa,
                          BigDecimal reducaoCbsTransicao) {

        /** Alíquota nominal da CBS efetivamente usada no cálculo. */
        public BigDecimal cbsEfetiva() {
            return reducaoCbsTransicao == null ? cbs : cbs.subtract(reducaoCbsTransicao);
        }
    }
}
