package br.com.tribia.dto;

import br.com.tribia.client.calculadora.OrigemCalculo;
import br.com.tribia.model.Calculo;
import br.com.tribia.model.Natureza;

import java.math.BigDecimal;

/** Cálculo de um item (ver {@link Calculo}). */
public record CalculoDto(
        Natureza natureza,
        OrigemCalculo origemValores,
        BigDecimal vCbs,
        BigDecimal vIbsUf,
        BigDecimal vIbsMun,
        BigDecimal vIs,
        BigDecimal pCbs,
        BigDecimal pIbsUf,
        BigDecimal pIbsMun,
        BigDecimal reducaoCbs,
        BigDecimal reducaoIbs,
        BigDecimal pIs,
        boolean sujeitoIs,
        BigDecimal impostoHoje,
        BigDecimal imposto2027,
        boolean simulado
) {
    public static CalculoDto de(Calculo c) {
        if (c == null) {
            return null;
        }
        return new CalculoDto(c.getNatureza(), c.getOrigemValores(), c.getVCbs(), c.getVIbsUf(), c.getVIbsMun(),
                c.getVIs(), c.getPCbs(), c.getPIbsUf(), c.getPIbsMun(), c.getReducaoCbs(), c.getReducaoIbs(),
                c.getPIs(), c.isSujeitoIs(), c.getImpostoHoje(), c.getImposto2027(), c.isSimulado());
    }
}
