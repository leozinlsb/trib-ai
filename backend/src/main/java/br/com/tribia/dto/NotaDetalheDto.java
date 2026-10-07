package br.com.tribia.dto;

import br.com.tribia.model.Nota;
import br.com.tribia.model.TipoNota;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Nota com itens. Classificações e cálculos entram nas próximas etapas. */
public record NotaDetalheDto(
        Long id,
        Long clienteId,
        TipoNota tipo,
        String chave,
        Long numero,
        Integer serie,
        LocalDate dataEmissao,
        String competencia,
        String emitenteCnpj,
        String emitenteNome,
        String destinatarioDocumento,
        String destinatarioNome,
        String contraparteCnpj,
        String contraparteNome,
        BigDecimal valorProdutos,
        BigDecimal valorTotal,
        List<ItemDto> itens
) {
    public static NotaDetalheDto de(Nota n) {
        return new NotaDetalheDto(n.getId(), n.getCliente().getId(), n.getTipo(), n.getChave(), n.getNumero(),
                n.getSerie(), n.getDataEmissao(), n.getCompetencia(), n.getEmitenteCnpj(), n.getEmitenteNome(),
                n.getDestinatarioDocumento(), n.getDestinatarioNome(), n.getContraparteCnpj(),
                n.getContraparteNome(), n.getValorProdutos(), n.getValorTotal(),
                n.getItens().stream().map(ItemDto::de).toList());
    }
}
