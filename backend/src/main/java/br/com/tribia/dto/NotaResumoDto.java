package br.com.tribia.dto;

import br.com.tribia.model.Nota;
import br.com.tribia.model.Operacao;
import br.com.tribia.model.TipoNota;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Linha da lista de notas do cliente (sem itens).
 *
 * @param tipo                sentido da mercadoria (SAIDA ou ENTRADA)
 * @param operacao            VENDA, COMPRA, DEVOLUCAO_DE_VENDA ou DEVOLUCAO_DE_COMPRA
 * @param fornecedorSimples   compra de fornecedor do Simples Nacional (crédito de 2027 limitado)
 * @param pagamentoConfirmado só compras: sem pagamento confirmado, não há crédito de 2027
 */
public record NotaResumoDto(
        Long id,
        Long clienteId,
        TipoNota tipo,
        Operacao operacao,
        String chave,
        Long numero,
        Integer serie,
        LocalDate dataEmissao,
        String competencia,
        String contraparteCnpj,
        String contraparteNome,
        BigDecimal valorTotal,
        int quantidadeItens,
        boolean fornecedorSimples,
        boolean pagamentoConfirmado
) {
    public static NotaResumoDto de(Nota n) {
        boolean compra = n.getOperacao().natureza() == br.com.tribia.model.Natureza.CREDITO;
        return new NotaResumoDto(n.getId(), n.getCliente().getId(), n.getTipo(), n.getOperacao(), n.getChave(),
                n.getNumero(), n.getSerie(), n.getDataEmissao(), n.getCompetencia(), n.getContraparteCnpj(),
                n.getContraparteNome(), n.getValorTotal(), n.getItens().size(), compra && n.emitenteDoSimples(),
                n.isPagamentoConfirmado());
    }
}
