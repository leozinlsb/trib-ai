package br.com.tribia.dto;

import br.com.tribia.model.OrigemClassificacao;

import java.util.List;
import java.util.Map;

/**
 * Resultado de POST /api/notas/{id}/classificar.
 *
 * @param porOrigem quantos itens estão classificados por origem (XML, CACHE, IA, MANUAL)
 * @param pendentes nItem dos itens ainda sem classificação
 * @param avisos    ex.: IA indisponível ou item que a IA não conseguiu classificar
 */
public record ClassificacaoNotaDto(
        Long notaId,
        int totalItens,
        int classificados,
        Map<OrigemClassificacao, Long> porOrigem,
        List<Integer> pendentes,
        List<String> avisos
) {
}
