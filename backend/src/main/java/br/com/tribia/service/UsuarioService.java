package br.com.tribia.service;

import br.com.tribia.dto.UsuarioDto;
import br.com.tribia.exception.ApiException;
import br.com.tribia.exception.RecursoNaoEncontradoException;
import br.com.tribia.model.Cliente;
import br.com.tribia.model.Papel;
import br.com.tribia.model.Usuario;
import br.com.tribia.repository.UsuarioRepository;
import br.com.tribia.security.AcessoService;
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
    private final AcessoService acesso;

    public UsuarioService(UsuarioRepository repository, PasswordEncoder encoder, AcessoService acesso) {
        this.repository = repository;
        this.encoder = encoder;
        this.acesso = acesso;
    }

    public List<UsuarioDto> daEmpresa(Long clienteId) {
        acesso.exigirAdmin();
        acesso.clienteAcessivel(clienteId);
        return repository.findByClienteIdOrderByNome(clienteId).stream().map(UsuarioDto::de).toList();
    }

    @Transactional
    public UsuarioDto criarDaEmpresa(Cliente cliente, UsuarioDto.Novo form) {
        acesso.exigirAdmin();
        cliente = acesso.clienteAcessivel(cliente.getId());
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
        acesso.exigirAdmin();
        Usuario u = repository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Usuário " + id + " não encontrado"));
        if (u.getPapel() == Papel.ADMIN) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Acesso negado", "Administradores não podem ser removidos.");
        }
        repository.delete(u);
    }
}
