package br.com.tribia.controller;

import br.com.tribia.model.Cliente;
import br.com.tribia.service.ClienteService;
import br.com.tribia.service.painel.RelatorioCsvService;
import br.com.tribia.service.painel.RelatorioCsvService.Formato;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;

@RestController
@Tag(name = "Relatórios")
public class RelatorioController {

    private static final MediaType CSV = new MediaType("text", "csv", StandardCharsets.UTF_8);

    private final RelatorioCsvService relatorio;
    private final ClienteService clienteService;

    public RelatorioController(RelatorioCsvService relatorio, ClienteService clienteService) {
        this.relatorio = relatorio;
        this.clienteService = clienteService;
    }

    @Operation(summary = "Relatório do cliente em CSV: itens, débitos, créditos e líquido hoje x 2027",
            description = "formato=EXCEL_BR (padrão: ';', vírgula decimal, BOM) ou PADRAO (',', ponto decimal, sem BOM)")
    @GetMapping("/api/clientes/{id}/relatorio.csv")
    public ResponseEntity<byte[]> doCliente(@PathVariable Long id,
                                            @RequestParam(required = false) String de,
                                            @RequestParam(required = false) String ate,
                                            @RequestParam(defaultValue = "EXCEL_BR") Formato formato) {
        byte[] csv = relatorio.doCliente(id, de, ate, formato);
        Cliente c = clienteService.buscar(id);
        String periodo = (de == null ? "" : "-" + de) + (ate == null ? "" : "-" + ate);
        return arquivo(csv, "tribia-" + c.getCnpj() + periodo + ".csv");
    }

    @Operation(summary = "Itens da nota em CSV, com classificação e cálculo",
            description = "formato=EXCEL_BR (padrão) ou PADRAO")
    @GetMapping("/api/notas/{id}/export.csv")
    public ResponseEntity<byte[]> daNota(@PathVariable Long id,
                                         @RequestParam(defaultValue = "EXCEL_BR") Formato formato) {
        return arquivo(relatorio.daNota(id, formato), "tribia-nota-" + id + ".csv");
    }

    private static ResponseEntity<byte[]> arquivo(byte[] conteudo, String nome) {
        return ResponseEntity.ok()
                .contentType(CSV)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(nome).build().toString())
                .body(conteudo);
    }
}
