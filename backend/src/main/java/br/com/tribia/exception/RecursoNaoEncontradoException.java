package br.com.tribia.exception;

/** Vira HTTP 404 no {@link ApiExceptionHandler}. */
public class RecursoNaoEncontradoException extends RuntimeException {

    public RecursoNaoEncontradoException(String mensagem) {
        super(mensagem);
    }
}
