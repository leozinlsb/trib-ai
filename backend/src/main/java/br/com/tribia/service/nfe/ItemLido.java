package br.com.tribia.service.nfe;

import br.com.tribia.model.IbsCbsDestacado;

import java.math.BigDecimal;

/**
 * Item (det) lido do XML. Valores ausentes (desconto, frete, ICMS, PIS, Cofins...) vêm como zero.
 *
 * @param ibsCbs grupo IBS/CBS destacado; null quando o item não traz o grupo
 */
public record ItemLido(
        int nItem,
        String codigo,
        String descricao,
        String ncm,
        String cfop,
        String unidade,
        BigDecimal quantidade,
        BigDecimal valorUnitario,
        BigDecimal valorTotal,
        BigDecimal vDesc,
        BigDecimal vFrete,
        BigDecimal vSeg,
        BigDecimal vOutro,
        BigDecimal vIcms,
        String cstPis,
        BigDecimal vPis,
        String cstCofins,
        BigDecimal vCofins,
        IbsCbsDestacado ibsCbs
) {
}
