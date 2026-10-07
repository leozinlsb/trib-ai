package br.com.tribia.exception;

import org.springframework.http.HttpStatus;

/**
 * Erro de negócio com status HTTP definido. O {@link ApiExceptionHandler} converte em ProblemDetail.
 */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String titulo;

    public ApiException(HttpStatus status, String titulo, String mensagem) {
        super(mensagem);
        this.status = status;
        this.titulo = titulo;
    }

    public static ApiException requisicaoInvalida(String mensagem) {
        return new ApiException(HttpStatus.BAD_REQUEST, "Requisição inválida", mensagem);
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getTitulo() {
        return titulo;
    }
}
