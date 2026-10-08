package br.com.tribia.controller;

import br.com.tribia.dto.ClienteDto;
import br.com.tribia.dto.ClienteForm;
import br.com.tribia.dto.ClienteListaDto;
import br.com.tribia.dto.DashboardDto;
import br.com.tribia.dto.UsuarioDto;
import br.com.tribia.security.AcessoService;
import br.com.tribia.service.ClienteService;
import br.com.tribia.service.DashboardService;
import br.com.tribia.service.UsuarioService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Empresas (clientes do escritório). Leitura conforme o acesso do usuário; alterações só pelo ADMIN. */
@RestController
@RequestMapping("/api/clientes")
@Tag(name = "Clientes")
public class ClienteController {

    private final ClienteService service;
    private final DashboardService dashboardService;
    private final UsuarioService usuarios;
    private final AcessoService acesso;

    public ClienteController(ClienteService service, DashboardService dashboardService, UsuarioService usuarios, AcessoService acesso) {
        this.service = service;
        this.dashboardService = dashboardService;
        this.usuarios = usuarios;
        this.acesso = acesso;
    }

    @Operation(summary = "Lista os clientes do escritório, com indicadores resumidos (tela inicial)")
    @GetMapping
    public List<ClienteListaDto> listar() {
        return dashboardService.listarComIndicadores();

    }

    @Operation(summary = "Dados de uma empresa")
    @GetMapping("/{id}")
    public ClienteDto buscar(@PathVariable Long id) {
        return ClienteDto.de(acesso.clienteAcessivel(id));
    }

    @Operation(summary = "Cadastra empresa (ADMIN)")
    @PostMapping
    public ResponseEntity<ClienteDto> criar(@Valid @RequestBody ClienteForm form) {
        acesso.exigirAdmin();
        return ResponseEntity.status(HttpStatus.CREATED).body(ClienteDto.de(service.criar(form)));
    }

    @Operation(summary = "Edita empresa (ADMIN). O CNPJ só muda se a empresa ainda não tiver notas.")
    @PutMapping("/{id}")
    public ClienteDto atualizar(@PathVariable Long id, @Valid @RequestBody ClienteForm form) {
        acesso.exigirAdmin();
        return ClienteDto.de(service.atualizar(id, form));
    }

    @Operation(summary = "Desativa empresa (ADMIN)", description = "Exclusão lógica: notas e acessos são mantidos e "
            + "a empresa pode ser reativada. Os usuários da empresa perdem o acesso enquanto ela estiver desativada.")
    @DeleteMapping("/{id}")
    public ClienteDto desativar(@PathVariable Long id) {
        acesso.exigirAdmin();
        return ClienteDto.de(service.desativar(id));
    }

    @Operation(summary = "Reativa empresa (ADMIN)")
    @PostMapping("/{id}/reativar")
    public ClienteDto reativar(@PathVariable Long id) {
        acesso.exigirAdmin();
        return ClienteDto.de(service.reativar(id));
    }

    @Operation(summary = "Usuários com acesso à empresa (ADMIN)")
    @GetMapping("/{id}/usuarios")
    public List<UsuarioDto> usuarios(@PathVariable Long id) {
        acesso.exigirAdmin();
        acesso.clienteAcessivel(id);
        return usuarios.daEmpresa(id);
    }

    @Operation(summary = "Cria acesso para a empresa (ADMIN)")
    @PostMapping("/{id}/usuarios")
    public ResponseEntity<UsuarioDto> criarUsuario(@PathVariable Long id, @Valid @RequestBody UsuarioDto.Novo form) {
        acesso.exigirAdmin();
        return ResponseEntity.status(HttpStatus.CREATED).body(usuarios.criarDaEmpresa(acesso.clienteAcessivel(id), form));
    }

    @Operation(summary = "Painel do cliente: indicadores e comparativo hoje x 2027, já agregados",
            description = "Usa os cálculos gravados. Para outro cenário de CBS, rode antes POST /api/clientes/{id}/calcular?cbs=")
    @GetMapping("/{id}/dashboard")
    public DashboardDto dashboard(@PathVariable Long id,
                                  @Parameter(description = "Competência inicial AAAA-MM (opcional)")
                                  @RequestParam(required = false) String de,
                                  @Parameter(description = "Competência final AAAA-MM (opcional)")
                                  @RequestParam(required = false) String ate,
                                  @Parameter(description = "Ranking topItens por PRODUTO (padrão) ou por NCM")
                                  @RequestParam(defaultValue = "PRODUTO") DashboardDto.Agrupamento agrupar,
                                  @Parameter(description = "Tamanho do topItens (1 a 50)")
                                  @RequestParam(defaultValue = "10") int limiteItens,
                                  @Parameter(description = "Tamanho do topFornecedores (1 a 50)")
                                  @RequestParam(defaultValue = "5") int limiteFornecedores) {
        return dashboardService.dashboard(id, de, ate, agrupar, limiteItens, limiteFornecedores);
    }
}
