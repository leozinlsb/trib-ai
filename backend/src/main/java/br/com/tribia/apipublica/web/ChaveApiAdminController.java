package br.com.tribia.apipublica.web;

import br.com.tribia.apipublica.dto.ChaveApiDtos.ChaveCriada;
import br.com.tribia.apipublica.dto.ChaveApiDtos.ChaveResumo;
import br.com.tribia.apipublica.dto.ChaveApiDtos.CriarChaveForm;
import br.com.tribia.apipublica.servico.ChaveApiService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Chaves da API pública: só ADMIN, pela sessão da plataforma (cookie + CSRF). Não há emissão pela própria API pública. */
@RestController
@RequestMapping("/api/admin/chaves-api")
@Tag(name = "Administração — chaves da API pública")
public class ChaveApiAdminController {

    private final ChaveApiService service;

    public ChaveApiAdminController(ChaveApiService service) {
        this.service = service;
    }

    @Operation(summary = "Emite uma chave para um integrador, presa a uma empresa (ADMIN)",
            description = "A chave completa vem só nesta resposta. Guarde-a num cofre de segredos do integrador.")
    @PostMapping
    public ResponseEntity<ChaveCriada> criar(@Valid @RequestBody CriarChaveForm form) {
        return ResponseEntity.status(HttpStatus.CREATED).header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(service.criar(form));
    }

    @Operation(summary = "Lista as chaves (sem segredo), opcionalmente de uma empresa (ADMIN)")
    @GetMapping
    public List<ChaveResumo> listar(@RequestParam(required = false) Long clienteId) {
        return service.listar(clienteId);
    }

    @Operation(summary = "Revoga uma chave definitivamente (ADMIN)")
    @PostMapping("/{id}/revogar")
    public ChaveResumo revogar(@PathVariable Long id) {
        return service.revogar(id);
    }
}
