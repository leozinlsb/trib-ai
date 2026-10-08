package br.com.tribia.dto;

import br.com.tribia.model.RegimeTributario;
import br.com.tribia.service.tabelas.TabelaCClassTrib.CClassTrib;

/**
 * Uma opção de classificação para a tela de revisão.
 *
 * @param exigeNcmNaLista benefício de anexo: só vale para NCMs da lista oficial
 * @param sugeridaPeloNcm a lista oficial associa este código ao NCM do item
 */
public record OpcaoClassificacaoDto(
        String cClassTrib,
        String cst,
        String nome,
        RegimeTributario regime,
        String descricaoRegime,
        String anexo,
        boolean exigeNcmNaLista,
        boolean sugeridaPeloNcm
) {
    public static OpcaoClassificacaoDto de(CClassTrib c, boolean exigeNcmNaLista, boolean sugeridaPeloNcm) {
        return new OpcaoClassificacaoDto(c.codigo(), c.cst(), c.nome(), c.regime(), c.descricaoRegime(),
                c.anexo().isBlank() ? null : c.anexo(), exigeNcmNaLista, sugeridaPeloNcm);
    }
}
