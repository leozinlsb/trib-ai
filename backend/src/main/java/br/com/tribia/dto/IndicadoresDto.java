package br.com.tribia.dto;

import java.math.BigDecimal;

/**
 * Indicadores do painel (prioridade 1 do adendo).
 *
 * @param faturamento      soma das notas de saída (vNF)
 * @param compras          soma das notas de entrada (vNF)
 * @param liquidoHoje      PIS/Cofins: débito - crédito
 * @param liquido2027      CBS/IBS/IS: débito - crédito. Negativo = saldo credor (ver {@link #saldoCredor})
 * @param variacaoPct      (líquido 2027 - líquido hoje) / líquido hoje, em %; null quando hoje não há imposto a pagar
 * @param credito2027      crédito de CBS/IBS gerado pelas compras
 * @param saldoCredor      true quando, em 2027, o crédito supera o débito (não é imposto negativo)
 * @param pendentesRevisao itens sem classificação, não aceitos ou com confiança baixa
 */
public record IndicadoresDto(
        BigDecimal faturamento,
        BigDecimal compras,
        BigDecimal liquidoHoje,
        BigDecimal liquido2027,
        BigDecimal variacaoPct,
        BigDecimal credito2027,
        boolean saldoCredor,
        int pendentesRevisao
) {
}
