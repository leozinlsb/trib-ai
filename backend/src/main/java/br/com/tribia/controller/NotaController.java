package br.com.tribia.controller;

import br.com.tribia.dto.NotaDetalheDto;
import br.com.tribia.dto.NotaResumoDto;
import br.com.tribia.dto.UploadNotasDto;
import br.com.tribia.exception.ApiException;
import br.com.tribia.exception.NotaRejeitadaException;
import br.com.tribia.model.TipoNota;
import br.com.tribia.service.ClienteService;
import br.com.tribia.service.NotaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@RestController
@Tag(name = "Notas")
public class NotaController {

    private final NotaService notaService;
    private final ClienteService clienteService;

    public NotaController(NotaService notaService, ClienteService clienteService) {
        this.notaService = notaService;
        this.clienteService = clienteService;
    }

    @Operation(summary = "Upload de um ou mais XMLs de NF-e para o cliente",
            description = "Detecta ENTRADA (cliente é o destinatário) ou SAIDA (cliente é o emitente). "
                    + "Cada arquivo é processado de forma independente. Responde 201 se ao menos um foi importado; "
                    + "se nenhum foi, responde com o erro do arquivo (422 ou 409) ou 422 com a lista de rejeições.")
    @PostMapping(path = "/api/clientes/{clienteId}/notas", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> upload(@PathVariable Long clienteId,
                                    @RequestParam("arquivos") List<MultipartFile> arquivos) {
        clienteService.buscar(clienteId); // 404 antes de processar qualquer arquivo
        if (arquivos.isEmpty()) {
            throw ApiException.requisicaoInvalida("Envie ao menos um arquivo XML no campo 'arquivos'.");
        }

        List<NotaResumoDto> importadas = new ArrayList<>();
        List<UploadNotasDto.Rejeicao> rejeitadas = new ArrayList<>();
        NotaRejeitadaException ultimoErro = null;
        for (MultipartFile arquivo : arquivos) {
            try {
                importadas.add(NotaResumoDto.de(notaService.importar(clienteId, ler(arquivo))));
            } catch (NotaRejeitadaException e) {
                ultimoErro = e;
                rejeitadas.add(new UploadNotasDto.Rejeicao(
                        arquivo.getOriginalFilename(), e.getStatus().value(), e.getMessage()));
            }
        }

        if (!importadas.isEmpty()) {
            return ResponseEntity.status(HttpStatus.CREATED).body(new UploadNotasDto(importadas, rejeitadas));
        }
        if (arquivos.size() == 1) {
            throw ultimoErro;
        }
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY,
                "Nenhuma das " + arquivos.size() + " notas foi importada.");
        pd.setTitle("Nota rejeitada");
        pd.setProperty("rejeitadas", rejeitadas);
        return ResponseEntity.unprocessableEntity().body(pd);
    }

    @Operation(summary = "Notas do cliente, com filtros opcionais")
    @GetMapping("/api/clientes/{clienteId}/notas")
    public List<NotaResumoDto> listar(@PathVariable Long clienteId,
                                      @RequestParam(required = false) TipoNota tipo,
                                      @RequestParam(required = false) String competencia) {
        return notaService.listar(clienteId, tipo, competencia);
    }

    @Operation(summary = "Nota com itens")
    @GetMapping("/api/notas/{id}")
    public NotaDetalheDto detalhar(@PathVariable Long id) {
        return notaService.detalhar(id);
    }

    private static byte[] ler(MultipartFile arquivo) {
        try {
            return arquivo.getBytes();
        } catch (IOException e) {
            throw NotaRejeitadaException.invalida("Não foi possível ler o arquivo enviado.");
        }
    }
}
