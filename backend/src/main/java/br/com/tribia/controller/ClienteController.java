package br.com.tribia.controller;

import br.com.tribia.dto.ClienteDto;
import br.com.tribia.service.ClienteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/clientes")
@Tag(name = "Clientes")
public class ClienteController {

    private final ClienteService service;

    public ClienteController(ClienteService service) {
        this.service = service;
    }

    // TODO etapa 5: incluir indicadores resumidos de cada cliente (tela inicial)
    @Operation(summary = "Lista os clientes do escritório")
    @GetMapping
    public List<ClienteDto> listar() {
        return service.listar().stream().map(ClienteDto::de).toList();
    }

    @Operation(summary = "Dados de um cliente")
    @GetMapping("/{id}")
    public ClienteDto buscar(@PathVariable Long id) {
        return ClienteDto.de(service.buscar(id));
    }
}
