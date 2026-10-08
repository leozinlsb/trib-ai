package br.com.tribia.service;

import br.com.tribia.dto.UsuarioDto;
import br.com.tribia.exception.ApiException;
import br.com.tribia.exception.RecursoNaoEncontradoException;
import br.com.tribia.model.Cliente;
import br.com.tribia.model.Papel;
import br.com.tribia.model.Usuario;
import br.com.tribia.repository.UsuarioRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Acessos das empresas, gerenciados pelo administrador. */
@Service
@Transactional(readOnly = true)
public class UsuarioService {

    private final UsuarioRepository repository;
    private final PasswordEncoder encoder;

    public UsuarioService(UsuarioRepository repository, PasswordEncoder encoder) {
        this.repository = repository;
        this.encoder = encoder;
    }

    public List<UsuarioDto> daEmpresa(Long clienteId) {
        return repository.findByClienteIdOrderByNome(clienteId).stream().map(UsuarioDto::de).toList();
    }

    @Transactional
    public UsuarioDto criarDaEmpresa(Cliente cliente, UsuarioDto.Novo form) {
        String email = form.email().trim().toLowerCase();
        if (repository.existsByEmailIgnoreCase(email)) {
            throw new ApiException(HttpStatus.CONFLICT, "Conflito", "Já existe um usuário com o e-mail " + email + ".");
        }
        Usuario u = Usuario.daEmpresa(form.nome().trim(), email, encoder.encode(form.senha()), cliente);
        return UsuarioDto.de(repository.save(u));
    }

    /** Remove o acesso de um usuário de empresa (administradores não são removidos por aqui). */
    @Transactional
    public void remover(Long id) {
        Usuario u = repository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Usuário " + id + " não encontrado"));
        if (u.getPapel() == Papel.ADMIN) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Acesso negado", "Administradores não podem ser removidos.");
        }
        repository.delete(u);
    }
}
