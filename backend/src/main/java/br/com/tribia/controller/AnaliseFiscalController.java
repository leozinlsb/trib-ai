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
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
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
}
