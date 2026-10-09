package br.com.tribia.apipublica.web;

import br.com.tribia.apipublica.dto.ApiPublicaDtos.AnalisePublica;
import br.com.tribia.apipublica.dto.ApiPublicaDtos.PaginaPublica;
import br.com.tribia.apipublica.dto.ApiPublicaDtos.ResumoAnalise;
import br.com.tribia.apipublica.dto.ApiPublicaDtos.SolicitacaoAnalise;
import br.com.tribia.apipublica.dto.ApiPublicaDtos.UsoChave;
import br.com.tribia.apipublica.seguranca.IntegradorAutenticado;
import br.com.tribia.apipublica.servico.ApiPublicaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

/**
 * API pública v1: análise fiscal (sugestão de NCM) de mercadorias para sistemas externos. Autenticação pela chave de
 * API (cabeçalho X-API-Key); a empresa é sempre a da chave. O motor é o mesmo da plataforma.
 */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "API pública v1 — Análises fiscais",
        description = "Envie a mercadoria, receba o id (202) e consulte até o status ser final.")
@ApiResponse(responseCode = "401", description = "Chave ausente (CHAVE_AUSENTE), inválida (CHAVE_INVALIDA), "
        + "revogada (CHAVE_REVOGADA) ou expirada (CHAVE_EXPIRADA)",
        content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
@ApiResponse(responseCode = "403", description = "Chave sem o escopo da operação (ESCOPO_INSUFICIENTE) ou empresa "
        + "desativada (EMPRESA_DESATIVADA)",
        content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
@ApiResponse(responseCode = "429", description = "Limite por minuto (LIMITE_REQUISICOES), cota diária "
        + "(COTA_DIARIA_EXCEDIDA), análises simultâneas (LIMITE_ANALISES_SIMULTANEAS) ou falhas de autenticação "
        + "(MUITAS_FALHAS_AUTENTICACAO). Respeite Retry-After.",
        headers = @Header(name = "Retry-After", description = "Segundos até tentar de novo"),
        content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
public class ApiPublicaAnaliseController {

    private final ApiPublicaService service;

    public ApiPublicaAnaliseController(ApiPublicaService service) {
        this.service = service;
    }

    @Operation(summary = "Envia uma mercadoria para análise fiscal",
            description = """
                    Cria a análise e responde 202 com o id; o processamento (IA → JEV, se ligada → validação na NCM
                    vigente) segue em segundo plano. Consulte GET /api/v1/analises/{id} até `finalizada` = true.
                    Envie Idempotency-Key (ex.: um UUID por produto/tentativa): repetir a mesma chave com o mesmo corpo
                    devolve a mesma análise (cabeçalho Idempotent-Replayed: true), sem nova chamada à IA e sem consumir
                    cota; com corpo diferente, 409 IDEMPOTENCIA_CONFLITO. Escopo: ANALISES_CRIAR.""")
    @ApiResponse(responseCode = "202", description = "Análise criada (ou repetição idempotente)",
            headers = {
                    @Header(name = "Location", description = "URL da análise"),
                    @Header(name = "Idempotent-Replayed", description = "true quando a resposta é de uma repetição"),
                    @Header(name = "X-Request-Id", description = "Identificador de rastreio da requisição")})
    @ApiResponse(responseCode = "400", description = "Dados inválidos (DADOS_INVALIDOS, JSON_INVALIDO, "
            + "IDEMPOTENCY_KEY_INVALIDA); o campo `campos` traz campo → mensagem",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "409", description = "Idempotency-Key reutilizada com outro corpo (IDEMPOTENCIA_CONFLITO)",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "503", description = "Fila de processamento cheia (SERVICO_OCUPADO)",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @PostMapping("/analises")
    public ResponseEntity<AnalisePublica> criar(
            @Parameter(in = ParameterIn.HEADER, description = "Chave de idempotência (1–100 caracteres [A-Za-z0-9_.:-]).",
                    example = "3b2f8f0e-6a8e-4a43-9a1f-4f3c2d1b0a99")
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody SolicitacaoAnalise pedido) {
        ApiPublicaService.Criacao c = service.criar(IntegradorAutenticado.atual(), idempotencyKey, pedido);
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .location(URI.create("/api/v1/analises/" + c.analise().id()))
                .header("Idempotent-Replayed", String.valueOf(c.repetida()))
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(c.analise());
    }

    @Operation(summary = "Consulta status e resultado de uma análise",
            description = "Só enxerga análises da empresa da chave; id de outra empresa ou inexistente = 404 "
                    + "ANALISE_NAO_ENCONTRADA. Escopo: ANALISES_LER.")
    @ApiResponse(responseCode = "200", description = "Análise")
    @ApiResponse(responseCode = "404", description = "ANALISE_NAO_ENCONTRADA",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @GetMapping("/analises/{id}")
    public ResponseEntity<AnalisePublica> consultar(@PathVariable String id) {
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(service.consultar(IntegradorAutenticado.atual(), id));
    }

    @Operation(summary = "Lista as análises da empresa da chave (mais recentes primeiro)",
            description = "Filtro opcional por referenciaExterna (igualdade). Escopo: ANALISES_LER.")
    @ApiResponse(responseCode = "200", description = "Página de análises")
    @GetMapping("/analises")
    public ResponseEntity<PaginaPublica<ResumoAnalise>> listar(
            @RequestParam(required = false) String referenciaExterna,
            @Parameter(description = "Página (0..10000)", example = "0") @RequestParam(required = false) Integer pagina,
            @Parameter(description = "Tamanho (1..100)", example = "20") @RequestParam(required = false) Integer tamanho) {
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(service.listar(IntegradorAutenticado.atual(), referenciaExterna, pagina, tamanho));
    }

    @Operation(summary = "Limites e consumo da chave de API usada",
            description = "Empresa vinculada, escopos, limites e análises criadas hoje. Escopo: ANALISES_LER.")
    @ApiResponse(responseCode = "200", description = "Consumo")
    @GetMapping("/uso")
    public ResponseEntity<UsoChave> uso() {
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(service.uso(IntegradorAutenticado.atual()));
    }
}
