package br.com.tribia.apipublica.web;

import org.springframework.http.HttpStatus;

import java.util.Map;

/**
 * Erro da API pública: status HTTP, código estável para o integrador tratar por máquina ({@link #getCodigo()}) e
 * mensagem segura (nunca dados de outra empresa, chave, prompt ou stack trace).
 */
public class ApiPublicaException extends RuntimeException {

    private final HttpStatus status;
    private final String codigo;
    private final String titulo;
    private final Map<String, Object> extras;
    private final Map<String, String> cabecalhos;

    public ApiPublicaException(HttpStatus status, String codigo, String titulo, String mensagem) {
        this(status, codigo, titulo, mensagem, Map.of(), Map.of());
    }

    public ApiPublicaException(HttpStatus status, String codigo, String titulo, String mensagem,
                               Map<String, Object> extras, Map<String, String> cabecalhos) {
        super(mensagem);
        this.status = status;
        this.codigo = codigo;
        this.titulo = titulo;
        this.extras = extras;
        this.cabecalhos = cabecalhos;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCodigo() {
        return codigo;
    }

    public String getTitulo() {
        return titulo;
    }

    public Map<String, Object> getExtras() {
        return extras;
    }

    public Map<String, String> getCabecalhos() {
        return cabecalhos;
    }
}
