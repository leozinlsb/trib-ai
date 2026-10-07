package br.com.tribia.exception;

import org.springframework.http.HttpStatus;

/** HTTP 404. */
public class RecursoNaoEncontradoException extends ApiException {

    public RecursoNaoEncontradoException(String mensagem) {
        super(HttpStatus.NOT_FOUND, "Recurso não encontrado", mensagem);
    }
}
