package br.com.tribia.service.apuracao;

import br.com.tribia.model.Item;

import java.math.BigDecimal;

/**
 * O que as regras de apuração precisam saber de um item.
 *
 * @param valor        valor do item (vProd)
 * @param cstPisCofins CST de PIS/Cofins como veio na nota
 * @param vPis         PIS destacado na nota
 * @param vCofins      Cofins destacada na nota
 * @param creditavel   false para bens de uso e consumo pessoal (sem crédito hoje nem em 2027)
 */
public record ItemTributavel(BigDecimal valor, String cstPisCofins, BigDecimal vPis, BigDecimal vCofins,
                             boolean creditavel) {

    public static ItemTributavel de(Item i) {
        return new ItemTributavel(i.getValorTotal(), i.getCstPisCofins(), i.getVPis(), i.getVCofins(), i.isCreditavel());
    }
}
