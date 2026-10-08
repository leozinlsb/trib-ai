package br.com.tribia.dto;

import java.util.List;

/**
 * Resultado de PUT /api/itens/{id}/classificacao.
 *
 * @param itensAtualizados  quantos itens receberam a aceitação/correção (o próprio + idênticos do cliente)
 * @param notasRecalculadas notas recalculadas para o painel refletir a mudança
 */
public record RevisaoResultadoDto(
        ItemDto item,
        int itensAtualizados,
        List<Long> notasRecalculadas,
        List<String> avisos
) {
}
