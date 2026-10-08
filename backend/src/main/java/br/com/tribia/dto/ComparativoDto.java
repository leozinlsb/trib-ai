package br.com.tribia.dto;

import br.com.tribia.service.apuracao.Apuracao;
import br.com.tribia.service.apuracao.Comparativo;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;

/** Hoje (PIS/Cofins) x 2027 (CBS/IBS/IS), no formato do adendo ("hoje" e "2027"). */
public record ComparativoDto(
        ApuracaoDto hoje,
        @JsonProperty("2027") ApuracaoDto ano2027,
        BigDecimal variacaoPct
) {
    public static ComparativoDto de(Comparativo c) {
        return new ComparativoDto(ApuracaoDto.de(c.hoje()), ApuracaoDto.de(c.ano2027()), c.variacaoPct());
    }

    /**
     * @param liquido     débito - crédito (pode ser negativo)
     * @param aPagar      líquido quando positivo, senão zero
     * @param saldoCredor excedente de crédito; maior que zero indica saldo credor, não imposto negativo
     */
    public record ApuracaoDto(BigDecimal debito, BigDecimal credito, BigDecimal liquido, BigDecimal aPagar,
                              BigDecimal saldoCredor) {

        static ApuracaoDto de(Apuracao a) {
            return new ApuracaoDto(a.debito(), a.credito(), a.liquido(), a.aPagar(), a.saldoCredor());
        }
    }
}
