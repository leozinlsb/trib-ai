package br.com.tribia.controller;

import br.com.tribia.dto.UsuarioDto;
import br.com.tribia.security.AcessoService;
import br.com.tribia.security.UsuarioLogado;
import br.com.tribia.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Login por sessão. O logout (POST /api/auth/logout) é tratado pelo Spring Security (SecurityConfig). */
@RestController
@RequestMapping("/api/auth")
@Tag(name = "Autenticação")
public class AuthController {

    private final AuthService authService;
    private final AcessoService acesso;
    private final SecurityContextRepository contextos;

    public AuthController(AuthService authService, AcessoService acesso, SecurityContextRepository contextos) {
        this.authService = authService;
        this.acesso = acesso;
        this.contextos = contextos;
    }

    @Operation(summary = "Entra no sistema", description = "Cria a sessão (cookie HttpOnly). Exige o header X-XSRF-TOKEN.")
    @PostMapping("/login")
    public UsuarioDto login(@Valid @RequestBody UsuarioDto.Login form, HttpServletRequest req, HttpServletResponse res) {
        UsuarioLogado u = authService.autenticar(form.email(), form.senha());

        // novo id de sessão após o login (proteção contra fixação de sessão)
        req.getSession(true);
        req.changeSessionId();
        SecurityContext ctx = SecurityContextHolder.createEmptyContext();
        UsuarioLogado principal = u.semSenha();
        ctx.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities()));
        SecurityContextHolder.setContext(ctx);
        contextos.saveContext(ctx, req, res);

        return authService.dados(u.id());
    }

    @Operation(summary = "Usuário da sessão atual (204 se não houver sessão)")
    @GetMapping("/me")
    public ResponseEntity<UsuarioDto> me() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof UsuarioLogado)) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(authService.dados(acesso.atual().id()));
    }

    @Operation(summary = "Só grava o cookie XSRF-TOKEN (antes do primeiro POST)")
    @GetMapping("/csrf")
    public ResponseEntity<Void> csrf() {
        return ResponseEntity.noContent().build();
    }
}
