package br.com.tribia.apipublica.web;

import br.com.tribia.apipublica.dto.ApiPublicaFase2Dtos.RespostaClassificacao;
import br.com.tribia.apipublica.dto.ApiPublicaFase2Dtos.RespostaSimulacao;
import br.com.tribia.apipublica.dto.ApiPublicaFase2Dtos.SolicitacaoClassificacao;
import br.com.tribia.apipublica.dto.ApiPublicaFase2Dtos.SolicitacaoSimulacao;
import br.com.tribia.apipublica.seguranca.IntegradorAutenticado;
import br.com.tribia.apipublica.servico.ApiPublicaFase2Service;
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
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

/**
 * API pública v1, fase 2: classificador (assíncrono: 202 + consulta) e calculadora (síncrona: não chama a IA) sem
 * nota. A empresa é sempre a da chave. Classificação = sugestão; cálculo = projeção pendente de validação fiscal.
 */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "API pública v1 — Classificador e calculadora",
        description = "Classifique produtos (CST/cClassTrib) e simule CBS/IBS/IS de 2027 sem enviar nota.")
@ApiResponse(responseCode = "401", description = "Chave ausente, inválida, revogada ou expirada",
        content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
@ApiResponse(responseCode = "403", description = "Chave sem o escopo (ESCOPO_INSUFICIENTE) ou empresa desativada",
        content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
@ApiResponse(responseCode = "400", description = "DADOS_INVALIDOS (campo → mensagem em `campos`)",
        content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
public class ApiPublicaFase2Controller {

    private final ApiPublicaFase2Service service;

    public ApiPublicaFase2Controller(ApiPublicaFase2Service service) {
        this.service = service;
    }

    @Operation(summary = "Classifica produtos avulsos (até 50) no regime da reforma: CST e cClassTrib",
            description = """
                    Assíncrono: responde 202 com o id. O que o cache da empresa (ou o catálogo oficial) já conhece sai
                    na hora; o resto vai para a IA em segundo plano (restrita à tabela oficial de cClassTrib; benefício
                    de anexo só com NCM na lista oficial; códigos que dependem do comprador nunca são sugeridos).
                    Consulte GET /api/v1/classificacoes/{id} até finalizada = true. Se tudo vier do cache, a resposta
                    já vem CONCLUIDA. Produtos enviados à IA contam na cota diária de itens da chave (compartilhada com
                    as notas); se não couberem, ficam SEM_CLASSIFICACAO com aviso. Envie Idempotency-Key para repetir com
                    segurança. Resposta: sugestão, não classificação definitiva. Escopo: CLASSIFICAR.""")
    @ApiResponse(responseCode = "202", description = "Pedido recebido (ou repetição idempotente)",
            headers = {@Header(name = "Location", description = "URL do pedido"),
                    @Header(name = "Idempotent-Replayed", description = "true quando a resposta é de uma repetição")})
    @ApiResponse(responseCode = "409", description = "IDEMPOTENCIA_CONFLITO",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "429", description = "COTA_DIARIA_ITENS_IA_EXCEDIDA, LIMITE_CLASSIFICACOES_SIMULTANEAS "
            + "ou limite por minuto",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @PostMapping("/classificacoes")
    public ResponseEntity<RespostaClassificacao> classificar(
            @Parameter(in = ParameterIn.HEADER, description = "Chave de idempotência (1–100 caracteres [A-Za-z0-9_.:-]).")
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody SolicitacaoClassificacao pedido) {
        ApiPublicaFase2Service.Pedido p = service.classificar(IntegradorAutenticado.atual(), idempotencyKey, pedido);
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .location(URI.create("/api/v1/classificacoes/" + p.resposta().id()))
                .header("Idempotent-Replayed", String.valueOf(p.repetida()))
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(p.resposta());
    }

    @Operation(summary = "Consulta um pedido de classificação",
            description = "Só pedidos da empresa da chave; de outra empresa ou inexistente = 404 "
                    + "CLASSIFICACAO_NAO_ENCONTRADA. Escopo: CLASSIFICAR.")
    @GetMapping("/classificacoes/{id}")
    public ResponseEntity<RespostaClassificacao> consultar(@PathVariable String id) {
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(service.consultar(IntegradorAutenticado.atual(), id));
    }

    @Operation(summary = "Simula CBS/IBS/IS de 2027 para itens avulsos (até 100), sem gravar nada",
            description = """
                    Mesmas regras do cálculo das notas: base de 2027 sem ICMS/PIS/Cofins informados, Imposto Seletivo
                    só para fabricante na venda, calculadora oficial da Receita com plano B simplificado (aviso).
                    VENDA devolve débito; COMPRA devolve crédito (o IS não gera crédito). Cenário opcional de
                    alíquota da CBS. Resposta: projeção pendente de validação fiscal. Escopo: CALCULAR.""")
    @ApiResponse(responseCode = "200", description = "Tributos por item e totais")
    @ApiResponse(responseCode = "503", description = "CALCULADORA_INDISPONIVEL (modo só oficial e calculadora fora do ar)",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @PostMapping("/calculos/simular")
    public ResponseEntity<RespostaSimulacao> simular(@Valid @RequestBody SolicitacaoSimulacao pedido) {
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(service.simular(IntegradorAutenticado.atual(), pedido));
    }
}
