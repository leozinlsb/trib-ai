package br.com.tribia.dto;

import br.com.tribia.model.TipoNota;
import br.com.tribia.service.classificacao.CriterioRevisao.Motivo;

import java.math.BigDecimal;
import java.util.List;

/** Aba "Revisão" do cliente: itens sem classificação, não aceitos ou com confiança baixa. */
public record RevisaoDto(
        Long clienteId,
        BigDecimal confiancaMinima,
        int total,
        List<ItemRevisaoDto> itens
) {
    /**
     * @param classificacao   null quando o item ainda não foi classificado
     * @param motivos         por que está na revisão
     * @param opcoesSugeridas códigos que a lista oficial associa ao NCM (a lista completa está em
     *                        GET /api/classificacoes/opcoes)
     */
    public record ItemRevisaoDto(
            Long itemId,
            Long notaId,
            Long notaNumero,
            TipoNota tipo,
            String competencia,
            String contraparteNome,
            Integer nItem,
            String codigo,
            String descricao,
            String ncm,
            BigDecimal valorTotal,
            boolean creditavel,
            ClassificacaoDto classificacao,
            List<Motivo> motivos,
            List<OpcaoClassificacaoDto> opcoesSugeridas
    ) {
    }
}
