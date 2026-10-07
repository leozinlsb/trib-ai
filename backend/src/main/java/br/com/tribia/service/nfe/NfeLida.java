package br.com.tribia.service.nfe;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Resultado da leitura do XML, antes de qualquer regra de negócio (cliente, tipo, duplicidade).
 *
 * @param finalidade   finNFe: 1 normal, 2 complementar, 3 ajuste, 4 devolução
 * @param tipoOperacao tpNF: 0 entrada, 1 saída (do ponto de vista do emitente)
 */
public record NfeLida(
        String chave,
        String modelo,
        Integer serie,
        Long numero,
        Integer finalidade,
        Integer tipoOperacao,
        Integer ambiente,
        LocalDate dataEmissao,
        Participante emitente,
        Participante destinatario,
        BigDecimal valorProdutos,
        BigDecimal valorTotal,
        List<ItemLido> itens
) {
    /** @param documento CNPJ (14) ou CPF (11), somente dígitos; null se ausente */
    public record Participante(String documento, String nome, String uf) {
    }
}
