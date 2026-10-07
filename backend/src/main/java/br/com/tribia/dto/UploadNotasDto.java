package br.com.tribia.dto;

import java.util.List;

/**
 * Resultado do upload de um ou mais XMLs. Cada arquivo é processado de forma independente:
 * um arquivo rejeitado não impede a importação dos outros.
 */
public record UploadNotasDto(
        List<NotaResumoDto> importadas,
        List<Rejeicao> rejeitadas
) {
    /** @param status HTTP que o arquivo teria recebido sozinho (422 inválida, 409 duplicada) */
    public record Rejeicao(String arquivo, int status, String motivo) {
    }
}
