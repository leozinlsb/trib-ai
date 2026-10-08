package br.com.tribia.controller;

import br.com.tribia.security.AcessoService;
import br.com.tribia.service.UsuarioService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/usuarios")
@Tag(name = "Usuários")
public class UsuarioController {

    private final UsuarioService service;
    private final AcessoService acesso;

    public UsuarioController(UsuarioService service, AcessoService acesso) {
        this.service = service;
        this.acesso = acesso;
    }

    @Operation(summary = "Remove o acesso de um usuário de empresa (ADMIN)")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> remover(@PathVariable Long id) {
        acesso.exigirAdmin();
        service.remover(id);
        return ResponseEntity.noContent().build();
    }
}
