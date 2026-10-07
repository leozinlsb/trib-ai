package br.com.tribia.exception;

import org.springframework.http.HttpStatus;

/**
 * Nota fiscal recusada no upload: XML inválido, nota de outro cliente, finalidade fora do MVP (422)
 * ou nota duplicada (409).
 */
public class NotaRejeitadaException extends ApiException {

    private NotaRejeitadaException(HttpStatus status, String mensagem) {
        super(status, "Nota rejeitada", mensagem);
    }

    public static NotaRejeitadaException invalida(String mensagem) {
        return new NotaRejeitadaException(HttpStatus.UNPROCESSABLE_ENTITY, mensagem);
    }

    public static NotaRejeitadaException duplicada(String mensagem) {
        return new NotaRejeitadaException(HttpStatus.CONFLICT, mensagem);
    }
}
