package br.com.tribia.dto;

import br.com.tribia.model.IbsCbsDestacado;
import br.com.tribia.model.Item;

import java.math.BigDecimal;

public record ItemDto(
        Long id,
        Integer nItem,
        String codigo,
        String descricao,
        String ncm,
        String cfop,
        String unidade,
        BigDecimal quantidade,
        BigDecimal valorUnitario,
        BigDecimal valorTotal,
        BigDecimal vIcms,
        String cstPisCofins,
        BigDecimal vPis,
        BigDecimal vCofins,
        boolean creditavel,
        IbsCbsDestacadoDto ibsCbsDestacado
) {
    public static ItemDto de(Item i) {
        return new ItemDto(i.getId(), i.getNItem(), i.getCodigo(), i.getDescricao(), i.getNcm(), i.getCfop(),
                i.getUnidade(), i.getQuantidade(), i.getValorUnitario(), i.getValorTotal(), i.getVIcms(),
                i.getCstPisCofins(), i.getVPis(), i.getVCofins(), i.isCreditavel(),
                IbsCbsDestacadoDto.de(i.getIbsCbsDestacado()));
    }

    /** null quando a nota não trouxe o grupo IBS/CBS no item. */
    public record IbsCbsDestacadoDto(
            String cst,
            String cClassTrib,
            BigDecimal vBc,
            BigDecimal pIbsUf,
            BigDecimal vIbsUf,
            BigDecimal pIbsMun,
            BigDecimal vIbsMun,
            BigDecimal vIbs,
            BigDecimal pCbs,
            BigDecimal vCbs
    ) {
        static IbsCbsDestacadoDto de(IbsCbsDestacado g) {
            if (g == null) {
                return null;
            }
            return new IbsCbsDestacadoDto(g.getCst(), g.getCClassTrib(), g.getVBc(), g.getPIbsUf(), g.getVIbsUf(),
                    g.getPIbsMun(), g.getVIbsMun(), g.getVIbs(), g.getPCbs(), g.getVCbs());
        }
    }
}
