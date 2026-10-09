package br.com.tribia.apipublica.web;

import br.com.tribia.apipublica.seguranca.FiltroRequestId;
import br.com.tribia.apipublica.seguranca.RespostaErroPublica;
import br.com.tribia.exception.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Erros dos controllers da API pública: ProblemDetail com {@code codigo} estável e {@code requestId}. Vem antes do
 * ApiExceptionHandler da plataforma (que continua valendo para as rotas internas). Erro inesperado vira 500
 * ERRO_INTERNO sem detalhe técnico na resposta; o detalhe fica no log do servidor, com o mesmo requestId.
 */
@RestControllerAdvice(assignableTypes = ApiPublicaAnaliseController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ApiPublicaExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiPublicaExceptionHandler.class);

    @ExceptionHandler(ApiPublicaException.class)
    public ResponseEntity<ProblemDetail> publica(ApiPublicaException e, HttpServletRequest req) {
        ProblemDetail pd = problema(req, e.getStatus(), e.getCodigo(), e.getTitulo(), e.getMessage());
        e.getExtras().forEach(pd::setProperty);
        ResponseEntity.BodyBuilder r = ResponseEntity.status(e.getStatus());
        e.getCabecalhos().forEach((k, v) -> r.header(k, v));
        return r.body(pd);
    }

    /** Erros do motor que já têm mensagem segura para o usuário (ex.: 503 de fila cheia). */
    @ExceptionHandler(ApiException.class)
    public ProblemDetail motor(ApiException e, HttpServletRequest req) {
        String codigo = e.getStatus() == HttpStatus.SERVICE_UNAVAILABLE ? "SERVICO_OCUPADO"
                : e.getStatus() == HttpStatus.NOT_FOUND ? "ANALISE_NAO_ENCONTRADA"
                : e.getStatus().is4xxClientError() ? "REQUISICAO_INVALIDA" : "ERRO_INTERNO";
        return problema(req, e.getStatus(), codigo, e.getTitulo(), e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail validacao(MethodArgumentNotValidException e, HttpServletRequest req) {
        Map<String, String> campos = new LinkedHashMap<>();
        e.getBindingResult().getFieldErrors().forEach(f -> campos.putIfAbsent(f.getField(), f.getDefaultMessage()));
        ProblemDetail pd = problema(req, HttpStatus.BAD_REQUEST, "DADOS_INVALIDOS", "Dados inválidos",
                campos.isEmpty() ? "Confira os dados enviados." : campos.values().iterator().next());
        pd.setProperty("campos", campos);
        return pd;
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail corpoIlegivel(HttpMessageNotReadableException e, HttpServletRequest req) {
        return problema(req, HttpStatus.BAD_REQUEST, "JSON_INVALIDO", "Corpo inválido",
                "O corpo da requisição não é um JSON válido para este endpoint.");
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ProblemDetail tipoConteudo(HttpMediaTypeNotSupportedException e, HttpServletRequest req) {
        return problema(req, HttpStatus.UNSUPPORTED_MEDIA_TYPE, "TIPO_CONTEUDO_NAO_SUPORTADO",
                "Tipo de conteúdo não suportado", "Envie o corpo como application/json.");
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ProblemDetail parametro(MethodArgumentTypeMismatchException e, HttpServletRequest req) {
        return problema(req, HttpStatus.BAD_REQUEST, "PARAMETRO_INVALIDO", "Parâmetro inválido",
                "Valor inválido para o parâmetro '" + e.getName() + "'.");
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail inesperado(Exception e, HttpServletRequest req) {
        log.error("API pública: erro inesperado (requestId {})", FiltroRequestId.de(req), e);
        return problema(req, HttpStatus.INTERNAL_SERVER_ERROR, "ERRO_INTERNO", "Erro interno",
                "Erro inesperado no servidor. Informe o requestId ao suporte.");
    }

    private static ProblemDetail problema(HttpServletRequest req, HttpStatus status, String codigo, String titulo,
                                          String detalhe) {
        ProblemDetail pd = RespostaErroPublica.problema(status, codigo, titulo, detalhe, FiltroRequestId.de(req));
        pd.setInstance(URI.create(req.getRequestURI()));
        return pd;
    }
}
