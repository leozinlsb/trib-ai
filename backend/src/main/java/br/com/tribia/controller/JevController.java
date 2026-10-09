package br.com.tribia.controller;

import br.com.tribia.service.fiscal.TesteConexaoJev;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Configuração e teste de conexão da JEV AI (somente ADMIN; a chave nunca aparece). */
@RestController
@Tag(name = "JEV AI")
public class JevController {

    private final TesteConexaoJev teste;

    public JevController(TesteConexaoJev teste) {
        this.teste = teste;
    }

    @Operation(summary = "Estado da configuração da JEV AI (não chama a API)")
    @GetMapping("/api/admin/jev/status")
    public TesteConexaoJev.Status status() {
        return teste.status();
    }

    @Operation(summary = "Teste de conexão com mercadoria sintética (chamada COBRADA; exige confirmarCusto=true)",
            description = "Lista os modelos da conta e faz uma avaliação com duas candidatas sintéticas.")
    @PostMapping("/api/admin/jev/teste")
    public TesteConexaoJev.Resultado testar(@RequestParam(defaultValue = "false") boolean confirmarCusto) {
        return teste.testar(confirmarCusto);
    }
}
