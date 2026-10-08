package br.com.tribia.dto;

import br.com.tribia.model.Cliente;
import br.com.tribia.model.Regime;

/** Linha da tela inicial: dados do cliente + indicadores resumidos de todo o período. */
public record ClienteListaDto(
        Long id,
        String cnpj,
        String razaoSocial,
        String nomeFantasia,
        Regime regime,
        String setor,
        String uf,
        String municipio,
        String codigoMunicipio,
        int notas,
        IndicadoresDto indicadores
) {
    public static ClienteListaDto de(Cliente c, int notas, IndicadoresDto indicadores) {
        return new ClienteListaDto(c.getId(), c.getCnpj(), c.getRazaoSocial(), c.getNomeFantasia(), c.getRegime(),
                c.getSetor(), c.getUf(), c.getMunicipio(), c.getCodigoMunicipio(), notas, indicadores);
    }
}
