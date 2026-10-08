package br.com.tribia.controller;

import br.com.tribia.dto.ClienteDto;
import br.com.tribia.dto.ClienteForm;
import br.com.tribia.dto.UsuarioDto;
import br.com.tribia.security.AcessoService;
import br.com.tribia.service.ClienteService;
import br.com.tribia.service.UsuarioService;
import io.swagger.v3.oas.annotations.Operation;
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
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Empresas (clientes do escritório). Leitura conforme o acesso do usuário; alterações só pelo ADMIN. */
@RestController
@RequestMapping("/api/clientes")
@Tag(name = "Clientes")
public class ClienteController {

    private final ClienteService service;
    private final UsuarioService usuarios;
    private final AcessoService acesso;

    public ClienteController(ClienteService service, UsuarioService usuarios, AcessoService acesso) {
        this.service = service;
        this.usuarios = usuarios;
        this.acesso = acesso;
    }

    @Operation(summary = "Empresas visíveis: todas (ADMIN, inclusive desativadas) ou só a do usuário")
    @GetMapping
    public List<ClienteDto> listar() {
        return acesso.clientesVisiveis().stream().map(ClienteDto::de).toList();
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
}
