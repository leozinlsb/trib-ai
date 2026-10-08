package br.com.tribia.dto;

import br.com.tribia.model.Papel;
import br.com.tribia.model.Usuario;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/** Usuário sem dados sensíveis. clienteId/clienteNome: empresa do usuário (null para ADMIN). */
public record UsuarioDto(Long id, String nome, String email, Papel papel, Long clienteId, String clienteNome,
                         Instant criadoEm) {

    public static UsuarioDto de(Usuario u) {
        var c = u.getCliente();
        return new UsuarioDto(u.getId(), u.getNome(), u.getEmail(), u.getPapel(),
                c == null ? null : c.getId(),
                c == null ? null : (c.getNomeFantasia() != null ? c.getNomeFantasia() : c.getRazaoSocial()),
                u.getCriadoEm());
    }

    public record Login(
            @NotBlank(message = "Informe o e-mail.") String email,
            @NotBlank(message = "Informe a senha.") String senha) {
    }

    /** Acesso de uma empresa, criado pelo administrador. */
    public record Novo(
            @NotBlank(message = "Informe o nome.") @Size(max = 255) String nome,
            @NotBlank(message = "Informe o e-mail.") @Email(message = "E-mail inválido.") @Size(max = 255) String email,
            @NotBlank(message = "Informe a senha.")
            @Size(min = 8, max = 72, message = "A senha deve ter entre 8 e 72 caracteres.")
            @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$", message = "A senha deve ter letras e números.")
            String senha) {
    }
}
