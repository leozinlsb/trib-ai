package br.com.tribia.dto;

import br.com.tribia.model.Nota;
import br.com.tribia.model.TipoNota;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Linha da lista de notas do cliente (sem itens). */
public record NotaResumoDto(
        Long id,
        Long clienteId,
        TipoNota tipo,
        String chave,
        Long numero,
        Integer serie,
        LocalDate dataEmissao,
        String competencia,
        String contraparteCnpj,
        String contraparteNome,
        BigDecimal valorTotal,
        int quantidadeItens
) {
    public static NotaResumoDto de(Nota n) {
        return new NotaResumoDto(n.getId(), n.getCliente().getId(), n.getTipo(), n.getChave(), n.getNumero(),
                n.getSerie(), n.getDataEmissao(), n.getCompetencia(), n.getContraparteCnpj(),
                n.getContraparteNome(), n.getValorTotal(), n.getItens().size());
    }
}
