package br.com.tribia.dto;

import br.com.tribia.model.Cliente;
import br.com.tribia.model.Regime;

public record ClienteDto(
        Long id,
        String cnpj,
        String razaoSocial,
        String nomeFantasia,
        Regime regime,
        String setor,
        String uf,
        String municipio,
        String codigoMunicipio,
        String email,
        String telefone,
        String responsavel,
        String observacoes,
        boolean ativo
) {
    public static ClienteDto de(Cliente c) {
        return new ClienteDto(c.getId(), c.getCnpj(), c.getRazaoSocial(), c.getNomeFantasia(),
                c.getRegime(), c.getSetor(), c.getUf(), c.getMunicipio(), c.getCodigoMunicipio(),
                c.getEmail(), c.getTelefone(), c.getResponsavel(), c.getObservacoes(), c.isAtivo());
    }
}
