package br.com.tribia.apipublica.web;

import br.com.tribia.apipublica.dto.ApiPublicaDtos.PaginaPublica;
import br.com.tribia.apipublica.dto.ApiPublicaNotasDtos.ComparativoEmpresa;
import br.com.tribia.apipublica.dto.ApiPublicaNotasDtos.EnvioNota;
import br.com.tribia.apipublica.dto.ApiPublicaNotasDtos.NotaPublica;
import br.com.tribia.apipublica.dto.ApiPublicaNotasDtos.ResumoNota;
import br.com.tribia.apipublica.seguranca.IntegradorAutenticado;
import br.com.tribia.apipublica.servico.ApiPublicaNotasService;
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
 * API pública v1, fase 1: NF-e e comparativo. O ERP envia o XML; o TribIA importa, classifica (XML, cache ou IA) e
 * calcula 2027 com os mesmos serviços do site. A empresa é sempre a da chave. Valores de 2027 são projeção.
 */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "API pública v1 — Notas fiscais e comparativo",
        description = "Envie o XML da NF-e, receba o id (202) e consulte até finalizada = true. Valores de 2027 são "
                + "projeção pendente de validação fiscal.")
@ApiResponse(responseCode = "401", description = "Chave ausente, inválida, revogada ou expirada",
        content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
@ApiResponse(responseCode = "403", description = "Chave sem o escopo (ESCOPO_INSUFICIENTE) ou empresa desativada",
        content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
@ApiResponse(responseCode = "429", description = "Limite por minuto, cota diária de itens para a IA "
        + "(COTA_DIARIA_ITENS_IA_EXCEDIDA) ou notas simultâneas (LIMITE_NOTAS_SIMULTANEAS). Respeite Retry-After.",
        content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
public class ApiPublicaNotasController {

    private final ApiPublicaNotasService service;

    public ApiPublicaNotasController(ApiPublicaNotasService service) {
        this.service = service;
    }

    @Operation(summary = "Envia uma NF-e (XML) para classificação e cálculo de 2027",
            description = """
                    Importa a nota na hora (XML inválido: 422 NOTA_INVALIDA; já importada: 409 NOTA_JA_IMPORTADA) e
                    responde 202 com o id. Classificação dos itens (XML, cache ou IA) e cálculo seguem em segundo plano:
                    consulte GET /api/v1/notas/{id} até finalizada = true. Itens enviados à IA contam na cota diária
                    da chave; se não couberem, a nota é calculada com o que o XML e o cache resolveram e o resto fica
                    para revisão na plataforma (aviso na resposta). Envie Idempotency-Key para reenviar com segurança.
                    Escopo: NOTAS_ENVIAR.""")
    @ApiResponse(responseCode = "202", description = "Nota recebida (ou repetição idempotente)",
            headers = {@Header(name = "Location", description = "URL da nota"),
                    @Header(name = "Idempotent-Replayed", description = "true quando a resposta é de uma repetição")})
    @ApiResponse(responseCode = "409", description = "NOTA_JA_IMPORTADA ou IDEMPOTENCIA_CONFLITO",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "422", description = "NOTA_INVALIDA: XML ilegível, nota de outra empresa, modelo ou "
            + "finalidade fora do escopo",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @PostMapping("/notas")
    public ResponseEntity<NotaPublica> enviar(
            @Parameter(in = ParameterIn.HEADER, description = "Chave de idempotência (1–100 caracteres [A-Za-z0-9_.:-]).")
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody EnvioNota pedido) {
        ApiPublicaNotasService.Envio e = service.enviar(IntegradorAutenticado.atual(), idempotencyKey, pedido);
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .location(URI.create("/api/v1/notas/" + e.nota().id()))
                .header("Idempotent-Replayed", String.valueOf(e.repetida()))
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(e.nota());
    }

    @Operation(summary = "Consulta a nota: itens classificados, cálculo de 2027 e comparativo",
            description = "Só notas da empresa da chave; de outra empresa ou inexistente = 404 NOTA_NAO_ENCONTRADA. "
                    + "Escopo: NOTAS_LER.")
    @GetMapping("/notas/{id}")
    public ResponseEntity<NotaPublica> consultar(@PathVariable String id) {
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(service.consultar(IntegradorAutenticado.atual(), id));
    }

    @Operation(summary = "Lista as notas enviadas pela API para a empresa da chave (mais recentes primeiro)",
            description = "Filtro opcional por referenciaExterna (igualdade). Escopo: NOTAS_LER.")
    @GetMapping("/notas")
    public ResponseEntity<PaginaPublica<ResumoNota>> listar(
            @RequestParam(required = false) String referenciaExterna,
            @Parameter(description = "Página (0..10000)") @RequestParam(required = false) Integer pagina,
            @Parameter(description = "Tamanho (1..100)") @RequestParam(required = false) Integer tamanho) {
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(service.listar(IntegradorAutenticado.atual(), referenciaExterna, pagina, tamanho));
    }

    @Operation(summary = "Comparativo hoje x 2027 da empresa da chave (todas as notas, do site e da API)",
            description = "Competências AAAA-MM opcionais (de/ate). Mesmos números do painel da plataforma. "
                    + "Escopo: NOTAS_LER.")
    @GetMapping("/comparativo")
    public ResponseEntity<ComparativoEmpresa> comparativo(
            @Parameter(description = "Competência inicial AAAA-MM", example = "2026-08") @RequestParam(required = false) String de,
            @Parameter(description = "Competência final AAAA-MM", example = "2026-10") @RequestParam(required = false) String ate) {
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(service.comparativo(IntegradorAutenticado.atual(), de, ate));
    }
}
