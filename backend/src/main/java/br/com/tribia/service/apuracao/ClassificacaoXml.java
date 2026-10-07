package br.com.tribia.service.apuracao;

import br.com.tribia.model.IbsCbsDestacado;
import br.com.tribia.model.Item;

import java.util.Optional;

/**
 * Classificação que já veio no grupo IBS/CBS do XML.
 *
 * Decisão do projeto (07/10/2026): do grupo destacado aproveitamos só o CST e o cClassTrib, o que dispensa a IA
 * para o item. Os valores (vCBS, vIBS) NÃO são usados: em 2026 eles saem com a alíquota-teste (0,9% + 0,1%) e
 * subestimariam 2027. O imposto é sempre recalculado com as alíquotas de 2027.
 */
public record ClassificacaoXml(String cst, String cClassTrib) {

    public static Optional<ClassificacaoXml> de(Item item) {
        return de(item.getIbsCbsDestacado());
    }

    public static Optional<ClassificacaoXml> de(IbsCbsDestacado g) {
        if (g == null || vazio(g.getCst()) || vazio(g.getCClassTrib())) {
            return Optional.empty();
        }
        return Optional.of(new ClassificacaoXml(g.getCst(), g.getCClassTrib()));
    }

    private static boolean vazio(String s) {
        return s == null || s.isBlank();
    }
}
