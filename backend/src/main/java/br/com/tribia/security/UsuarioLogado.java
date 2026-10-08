package br.com.tribia.security;

import br.com.tribia.model.Papel;
import br.com.tribia.model.Usuario;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/**
 * Principal guardado na sessão. A empresa do usuário vem daqui (do servidor), nunca de um parâmetro
 * enviado pelo navegador.
 *
 * @param senhaHash só preenchido ao carregar para autenticar; o principal da sessão vai sem ele
 * @param clienteId empresa do usuário; null para ADMIN
 */
public record UsuarioLogado(Long id, String nome, String email, String senhaHash, Papel papel, Long clienteId)
        implements UserDetails {

    public static UsuarioLogado de(Usuario u) {
        return new UsuarioLogado(u.getId(), u.getNome(), u.getEmail(), u.getSenhaHash(), u.getPapel(),
                u.getCliente() == null ? null : u.getCliente().getId());
    }

    public UsuarioLogado semSenha() {
        return new UsuarioLogado(id, nome, email, null, papel, clienteId);
    }

    public boolean admin() {
        return papel == Papel.ADMIN;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + papel.name()));
    }

    @Override
    public String getPassword() {
        return senhaHash;
    }

    @Override
    public String getUsername() {
        return email;
    }
}
