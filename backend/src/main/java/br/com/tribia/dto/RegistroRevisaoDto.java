package br.com.tribia.dto;

import br.com.tribia.model.RegistroRevisao;

import java.time.Instant;

/** Um passo do histórico de revisão de um item (GET /api/itens/{id}/historico). */
public record RegistroRevisaoDto(
        Instant quando,
        RegistroRevisao.Acao acao,
        String autor,
        String antes,
        String depois,
        String justificativa,
        Long aplicadoAPartirDoItem
) {
    public static RegistroRevisaoDto de(RegistroRevisao r) {
        return new RegistroRevisaoDto(r.getQuando(), r.getAcao(), r.getAutor(), r.getAntes(), r.getDepois(),
                r.getJustificativa(), r.getAplicadoAPartirDoItem());
    }
}
