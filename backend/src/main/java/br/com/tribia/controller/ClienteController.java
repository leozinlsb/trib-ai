package br.com.tribia.controller;

import br.com.tribia.dto.ClienteDto;
import br.com.tribia.dto.ClienteListaDto;
import br.com.tribia.dto.DashboardDto;
import br.com.tribia.service.ClienteService;
import br.com.tribia.service.DashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/clientes")
@Tag(name = "Clientes")
public class ClienteController {

    private final ClienteService service;
    private final DashboardService dashboardService;

    public ClienteController(ClienteService service, DashboardService dashboardService) {
        this.service = service;
        this.dashboardService = dashboardService;
    }

    @Operation(summary = "Lista os clientes do escritório, com indicadores resumidos (tela inicial)")
    @GetMapping
    public List<ClienteListaDto> listar() {
        return dashboardService.listarComIndicadores();
    }

    @Operation(summary = "Dados de um cliente")
    @GetMapping("/{id}")
    public ClienteDto buscar(@PathVariable Long id) {
        return ClienteDto.de(service.buscar(id));
    }

    @Operation(summary = "Painel do cliente: indicadores e comparativo hoje x 2027, já agregados",
            description = "Usa os cálculos gravados. Para outro cenário de CBS, rode antes POST /api/clientes/{id}/calcular?cbs=")
    @GetMapping("/{id}/dashboard")
    public DashboardDto dashboard(@PathVariable Long id,
                                  @Parameter(description = "Competência inicial AAAA-MM (opcional)")
                                  @RequestParam(required = false) String de,
                                  @Parameter(description = "Competência final AAAA-MM (opcional)")
                                  @RequestParam(required = false) String ate) {
        return dashboardService.dashboard(id, de, ate);
    }
}
