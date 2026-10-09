package br.com.tribia.apipublica.seguranca;

import br.com.tribia.apipublica.ApiPublicaProperties;
import br.com.tribia.apipublica.model.ChaveApi;
import br.com.tribia.apipublica.repository.ChaveApiRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

/**
 * Autentica a API pública pelo cabeçalho X-API-Key: formato → prefixo → linha da chave → SHA-256 comparado em tempo
 * constante → revogada? expirada? empresa ativa? → limite por minuto. Só então o integrador entra no contexto de
 * segurança, apenas durante a requisição.
 *
 * Chave desconhecida e segredo errado dão a mesma resposta (CHAVE_INVALIDA). Revogada/expirada só são ditas a quem
 * apresentou a chave inteira certa. Falhas de autenticação contam por IP: passou do limite, 429 antes de ir ao banco.
 * A chave nunca é registrada em log; só o prefixo.
 */
public class FiltroChaveApi extends OncePerRequestFilter {

    public static final String CABECALHO = "X-API-Key";
    private static final Logger log = LoggerFactory.getLogger(FiltroChaveApi.class);
    private static final Duration INTERVALO_REGISTRO_USO = Duration.ofMinutes(1);

    private final ChaveApiRepository chaves;
    private final TransactionTemplate tx;
    private final ObjectMapper json;
    private final ApiPublicaProperties props;
    private final LimitadorRequisicoes porChave;
    private final LimitadorRequisicoes falhasPorIp;
    private final Clock relogio;

    public FiltroChaveApi(ChaveApiRepository chaves, TransactionTemplate tx, ObjectMapper json,
                          ApiPublicaProperties props, LimitadorRequisicoes porChave, LimitadorRequisicoes falhasPorIp,
                          Clock relogio) {
        this.chaves = chaves;
        this.tx = tx;
        this.json = json;
        this.props = props;
        this.porChave = porChave;
        this.falhasPorIp = falhasPorIp;
        this.relogio = relogio;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        String ip = "ip:" + req.getRemoteAddr();
        if (falhasPorIp.excedido(ip, props.falhasAutenticacaoPorMinuto())) {
            RespostaErroPublica.escrever(req, res, json, HttpStatus.TOO_MANY_REQUESTS, "MUITAS_FALHAS_AUTENTICACAO",
                    "Muitas falhas de autenticação",
                    "Muitas tentativas com chave ausente ou inválida a partir deste endereço. Aguarde um minuto.",
                    Map.of("Retry-After", "60"));
            return;
        }

        String recebida = req.getHeader(CABECALHO);
        if (recebida == null || recebida.isBlank()) {
            falhar(req, res, ip, "CHAVE_AUSENTE", "Chave de API ausente",
                    "Envie a chave de API no cabeçalho " + CABECALHO + ".");
            return;
        }
        String chave = recebida.trim();
        Optional<ChaveApi> encontrada = ChavesApi.prefixo(chave)
                .flatMap(p -> tx.execute(s -> chaves.buscarPorPrefixo(p)))
                .filter(c -> ChavesApi.confere(chave, c.getHashSegredo()));
        if (encontrada.isEmpty()) {
            falhar(req, res, ip, "CHAVE_INVALIDA", "Chave de API inválida", "A chave de API informada não é válida.");
            return;
        }
        ChaveApi c = encontrada.get();
        Instant agora = relogio.instant();
        if (c.revogada()) {
            log.info("API pública: chave revogada usada (prefixo {})", c.getPrefixo());
            RespostaErroPublica.escrever(req, res, json, HttpStatus.UNAUTHORIZED, "CHAVE_REVOGADA",
                    "Chave de API revogada", "Esta chave de API foi revogada. Peça uma nova ao administrador.", Map.of());
            return;
        }
        if (c.expirada(agora)) {
            RespostaErroPublica.escrever(req, res, json, HttpStatus.UNAUTHORIZED, "CHAVE_EXPIRADA",
                    "Chave de API expirada", "Esta chave de API expirou. Peça uma nova ao administrador.", Map.of());
            return;
        }
        if (!c.getCliente().isAtivo()) {
            RespostaErroPublica.escrever(req, res, json, HttpStatus.FORBIDDEN, "EMPRESA_DESATIVADA",
                    "Empresa desativada", "A empresa desta chave está desativada no TribIA.", Map.of());
            return;
        }

        IntegradorAutenticado integrador = new IntegradorAutenticado(c.getId(), c.getPrefixo(), c.getNomeIntegrador(),
                c.getCliente().getId(), c.getEscopos(),
                valor(c.getRequisicoesPorMinuto(), props.requisicoesPorMinuto()),
                valor(c.getCotaDiariaAnalises(), props.cotaDiariaAnalises()),
                valor(c.getMaxAnalisesSimultaneas(), props.maxAnalisesSimultaneas()),
                valor(c.getCotaDiariaItensIa(), props.cotaDiariaItensIa()));

        LimitadorRequisicoes.Resultado limite = porChave.consumir("chave:" + c.getId(), integrador.requisicoesPorMinuto());
        res.setHeader("X-RateLimit-Limit", String.valueOf(limite.limite()));
        res.setHeader("X-RateLimit-Remaining", String.valueOf(limite.restante()));
        res.setHeader("X-RateLimit-Reset", String.valueOf(limite.segundosParaNova()));
        if (!limite.permitido()) {
            RespostaErroPublica.escrever(req, res, json, HttpStatus.TOO_MANY_REQUESTS, "LIMITE_REQUISICOES",
                    "Limite de requisições excedido",
                    "Esta chave passou de " + limite.limite() + " requisições por minuto. Aguarde e tente de novo.",
                    Map.of("Retry-After", String.valueOf(limite.segundosParaNova())));
            return;
        }

        tx.executeWithoutResult(s -> chaves.registrarUso(c.getId(), agora, agora.minus(INTERVALO_REGISTRO_USO)));

        SecurityContext contexto = SecurityContextHolder.createEmptyContext();
        contexto.setAuthentication(new IntegradorAutenticado.Autenticacao(integrador));
        SecurityContextHolder.setContext(contexto);
        try {
            chain.doFilter(req, res);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private void falhar(HttpServletRequest req, HttpServletResponse res, String ip, String codigo, String titulo,
                        String detalhe) throws IOException {
        falhasPorIp.consumir(ip, props.falhasAutenticacaoPorMinuto());
        RespostaErroPublica.escrever(req, res, json, HttpStatus.UNAUTHORIZED, codigo, titulo, detalhe,
                Map.of("WWW-Authenticate", "ApiKey header=\"" + CABECALHO + "\""));
    }

    private static int valor(Integer daChave, int padrao) {
        return daChave == null || daChave < 1 ? padrao : daChave;
    }
}
