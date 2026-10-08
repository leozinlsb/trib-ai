package br.com.tribia.service;

import br.com.tribia.dto.UsuarioDto;
import br.com.tribia.exception.ApiException;
import br.com.tribia.exception.RecursoNaoEncontradoException;
import br.com.tribia.model.Papel;
import br.com.tribia.model.Usuario;
import br.com.tribia.repository.UsuarioRepository;
import br.com.tribia.security.AcessoService;
import br.com.tribia.security.LimiteTentativasLogin;
import br.com.tribia.security.UsuarioLogado;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AuthService {

    private final UsuarioRepository repository;
    private final PasswordEncoder encoder;
    /** Para gastar o mesmo tempo quando o e-mail não existe (não revela quais e-mails estão cadastrados). */
    private final String hashFicticio;
    private final LimiteTentativasLogin limite;

    public AuthService(UsuarioRepository repository, PasswordEncoder encoder, LimiteTentativasLogin limite) {
        this.repository = repository;
        this.encoder = encoder;
        this.limite = limite;
        this.hashFicticio = encoder.encode("senha-que-nao-existe");
    }

    /**
     * @throws ApiException 401 com mensagem genérica; 429 depois de muitas falhas seguidas no mesmo e-mail;
     *                      403 se a empresa do usuário estiver desativada
     */
    public UsuarioLogado autenticar(String email, String senha) {
        limite.verificar(email);
        Usuario u = repository.buscarPorEmail(email.trim()).orElse(null);
        if (u == null) {
            encoder.matches(senha, hashFicticio);
            limite.falhou(email);
            throw credenciaisInvalidas();
        }
        if (!encoder.matches(senha, u.getSenhaHash())) {
            limite.falhou(email);
            throw credenciaisInvalidas();
        }
        limite.acertou(email);
        if (u.getPapel() == Papel.EMPRESA && (u.getCliente() == null || !u.getCliente().isAtivo())) {
            throw AcessoService.empresaDesativada();
        }
        return UsuarioLogado.de(u);
    }

    public UsuarioDto dados(Long usuarioId) {
        return repository.findById(usuarioId)
                .map(UsuarioDto::de)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Usuário não encontrado"));
    }

    private static ApiException credenciaisInvalidas() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "Não autenticado", "E-mail ou senha incorretos.");
    }
}
