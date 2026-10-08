package br.com.tribia.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Corpo de PUT /api/itens/{id}/classificacao. Informe ao menos um de: aceitar, cClassTrib, creditavel.
 *
 * - Aceitar a sugestão: {"aceitar": true}
 * - Corrigir: {"cClassTrib": "200032", "justificativa": "..."} (o CST é deduzido da tabela oficial se omitido)
 * - Marcar uso e consumo (sem crédito): {"creditavel": false}
 */
public record RevisarItemRequest(
        @Schema(description = "Confirma a classificação atual do item", example = "true")
        Boolean aceitar,
        @Schema(description = "CST da correção; se omitido, vem da tabela oficial", example = "200")
        String cst,
        @Schema(description = "cClassTrib da correção (tabela oficial)", example = "200032")
        String cClassTrib,
        @Schema(description = "Motivo da correção")
        String justificativa,
        @Schema(description = "Só para itens de entrada: false = uso e consumo, sem crédito")
        Boolean creditavel,
        @Schema(description = "Aplica a aceitação/correção aos itens idênticos (NCM + descrição) do cliente ainda "
                + "não revisados. Padrão: true")
        Boolean aplicarAosIguais
) {
    public boolean vazio() {
        return aceitar == null && (cClassTrib == null || cClassTrib.isBlank()) && creditavel == null;
    }
}
