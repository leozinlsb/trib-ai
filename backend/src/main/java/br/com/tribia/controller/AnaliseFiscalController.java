package br.com.tribia.controller;

import br.com.tribia.dto.fiscal.AnaliseFiscalDtos.AnaliseDetalhe;
import br.com.tribia.dto.fiscal.AnaliseFiscalDtos.AnaliseResumo;
import br.com.tribia.dto.fiscal.AnaliseFiscalDtos.Indicadores;
import br.com.tribia.dto.fiscal.AnaliseFiscalDtos.Pagina;
import br.com.tribia.dto.fiscal.MercadoriaEntradaDto;
import br.com.tribia.service.fiscal.AnaliseFiscalService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/** Inteligência Fiscal: sugestão de NCM de mercadorias (contrato em frontend/docs/inteligencia-fiscal-api.md). */
@RestController
@Tag(name = "Inteligência Fiscal")
public class AnaliseFiscalController {

    private final AnaliseFiscalService service;

    public AnaliseFiscalController(AnaliseFiscalService service) {
        this.service = service;
    }

    @Operation(summary = "Histórico paginado das análises da empresa")
    @GetMapping("/api/clientes/{clienteId}/analises-fiscais")
    public Pagina<AnaliseResumo> listar(@PathVariable Long clienteId,
                                        @RequestParam(required = false) String q,
                                        @RequestParam(required = false) String status,
                                        @RequestParam(required = false) String de,
                                        @RequestParam(required = false) String ate,
                                        @RequestParam(required = false) Integer pagina,
                                        @RequestParam(required = false) Integer tamanho) {
        return service.listar(clienteId, q, status, de, ate, pagina, tamanho);
    }

    @Operation(summary = "Contagem das análises da empresa por situação")
    @GetMapping("/api/clientes/{clienteId}/analises-fiscais/indicadores")
    public Indicadores indicadores(@PathVariable Long clienteId) {
        return service.indicadores(clienteId);
    }

    @Operation(summary = "Inicia uma análise (multipart: parte \"dados\" em JSON + \"arquivos\" opcionais)",
            description = "Responde 202 com a análise em AGUARDANDO; o processamento segue em segundo plano.")
    @PostMapping(value = "/api/clientes/{clienteId}/analises-fiscais", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.ACCEPTED)
    public AnaliseResumo iniciar(@PathVariable Long clienteId,
                                 @Valid @RequestPart("dados") MercadoriaEntradaDto dados,
                                 @RequestPart(value = "arquivos", required = false) List<MultipartFile> arquivos) {
        return service.iniciar(clienteId, dados, arquivos);
    }

    @Operation(summary = "Detalhe da análise (o front consulta enquanto estiver em andamento)")
    @GetMapping("/api/analises-fiscais/{id}")
    public AnaliseDetalhe detalhar(@PathVariable Long id) {
        return service.detalhar(id);
    }

    @Operation(summary = "Registra a revisão humana da análise (NCM decidida e justificativa)",
            description = "Só para análises com resultado. Trocar a sugestão exige observação; a NCM decidida precisa "
                    + "constar da NCM vigente. É registro interno, não decisão da Receita Federal.")
    @PutMapping(value = "/api/analises-fiscais/{id}/revisao", consumes = MediaType.APPLICATION_JSON_VALUE)
    public AnaliseDetalhe revisar(@PathVariable Long id, @RequestBody AnaliseFiscalService.RevisaoForm form) {
        return service.revisar(id, form);
    }

    @Operation(summary = "Relatório da análise em PDF",
            description = "Disponível quando a análise tem resultado (concluída ou aguardando revisão); senão 409.")
    @GetMapping(value = "/api/analises-fiscais/{id}/relatorio", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> relatorio(@PathVariable Long id) {
        AnaliseFiscalService.RelatorioPdf r = service.relatorio(id);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(r.nomeArquivo()).build().toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(r.conteudo());
    }
}
