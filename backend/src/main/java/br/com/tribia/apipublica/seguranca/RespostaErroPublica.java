package br.com.tribia.apipublica.seguranca;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;

import java.io.IOException;
import java.util.Map;

/**
 * Corpo de erro da API pública: ProblemDetail (RFC 9457), como o resto do TribIA, com {@code codigo} (estável, para
 * tratar por máquina) e {@code requestId} (o mesmo do cabeçalho X-Request-Id e dos logs do servidor).
 */
public final class RespostaErroPublica {

    private RespostaErroPublica() {
    }

    public static ProblemDetail problema(HttpStatus status, String codigo, String titulo, String detalhe,
                                         String requestId) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detalhe);
        pd.setTitle(titulo);
        pd.setProperty("codigo", codigo);
        if (requestId != null) {
            pd.setProperty("requestId", requestId);
        }
        return pd;
    }

    /** Escreve o erro direto na resposta (usado pelos filtros, antes de chegar a um controller). */
    public static void escrever(HttpServletRequest req, HttpServletResponse res, ObjectMapper json, HttpStatus status,
                                String codigo, String titulo, String detalhe, Map<String, String> cabecalhos)
            throws IOException {
        ProblemDetail pd = problema(status, codigo, titulo, detalhe, FiltroRequestId.de(req));
        pd.setInstance(java.net.URI.create(req.getRequestURI()));
        res.setStatus(status.value());
        cabecalhos.forEach(res::setHeader);
        res.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        res.setCharacterEncoding("UTF-8");
        json.writeValue(res.getOutputStream(), pd);
    }
}
