package br.com.tribia.apipublica.seguranca;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Identificador de rastreio de cada requisição da API pública: usa o X-Request-Id do integrador se for seguro
 * (8 a 64 caracteres [A-Za-z0-9._-]); senão gera um UUID. Volta no cabeçalho da resposta, no corpo dos erros e no
 * MDC dos logs ({@code requestId}), para o suporte achar a requisição sem pedir dados fiscais.
 */
public class FiltroRequestId extends OncePerRequestFilter {

    public static final String CABECALHO = "X-Request-Id";
    private static final String ATRIBUTO = FiltroRequestId.class.getName();
    private static final Pattern SEGURO = Pattern.compile("^[A-Za-z0-9._-]{8,64}$");

    public static String de(HttpServletRequest req) {
        Object id = req.getAttribute(ATRIBUTO);
        return id == null ? null : id.toString();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        String recebido = req.getHeader(CABECALHO);
        String id = recebido != null && SEGURO.matcher(recebido).matches() ? recebido : UUID.randomUUID().toString();
        req.setAttribute(ATRIBUTO, id);
        res.setHeader(CABECALHO, id);
        MDC.put("requestId", id);
        try {
            chain.doFilter(req, res);
        } finally {
            MDC.remove("requestId");
        }
    }
}
