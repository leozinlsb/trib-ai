package br.com.tribia.controller;

import br.com.tribia.dto.RelatorioDto;
import br.com.tribia.security.AcessoService;
import br.com.tribia.service.RelatorioService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Relatórios")
public class RelatorioController {

    private final RelatorioService service;
    private final AcessoService acesso;

    public RelatorioController(RelatorioService service, AcessoService acesso) {
        this.service = service;
        this.acesso = acesso;
    }

    @Operation(summary = "Relatório da empresa no período",
            description = "Gerado a partir das notas importadas: resumo, apuração de PIS/Cofins de hoje, competências, "
                    + "documentos, contrapartes e verificações automáticas. de/ate no formato AAAA-MM (opcionais).")
    @GetMapping("/api/clientes/{clienteId}/relatorio")
    public RelatorioDto gerar(@PathVariable Long clienteId,
                              @RequestParam(required = false) String de,
                              @RequestParam(required = false) String ate) {
        return service.gerar(acesso.clienteAcessivel(clienteId), de, ate);
    }
}
