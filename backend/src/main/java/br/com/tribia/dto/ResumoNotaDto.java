package br.com.tribia.dto;

import br.com.tribia.dto.DashboardDto.FaixaRegimeDto;
import br.com.tribia.dto.DashboardDto.ItemImpactoDto;
import br.com.tribia.dto.DashboardDto.SujeitoIsDto;

import java.util.List;

/**
 * Resumo de uma nota (GET /api/notas/{id}/resumo, do documento base): comparativo hoje x 2027, regimes e o efeito
 * de cada produto. Lê os cálculos gravados.
 */
public record ResumoNotaDto(
        NotaResumoDto nota,
        ComparativoDto comparativo,
        List<FaixaRegimeDto> porRegime,
        SujeitoIsDto sujeitoIs,
        List<ItemImpactoDto> itens,
        int pendentesRevisao,
        List<String> avisos
) {
}
