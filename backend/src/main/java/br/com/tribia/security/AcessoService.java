package br.com.tribia.security;

import br.com.tribia.exception.ApiException;
import br.com.tribia.exception.RecursoNaoEncontradoException;
import br.com.tribia.model.Cliente;
import br.com.tribia.repository.ClienteRepository;
import br.com.tribia.repository.UsuarioRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

/**
 * Regras de acesso por empresa, aplicadas em todo endpoint que lê ou altera dados de uma empresa.
 * <ul>
 *   <li>ADMIN: acessa todas as empresas, inclusive as desativadas.</li>
 *   <li>EMPRESA: só a própria (o id vem da sessão). Pedir outra devolve 404, o mesmo de uma empresa
 *       inexistente, para não revelar o que existe. Se a empresa dele for desativada, perde o acesso (403).</li>
 * </ul>
 */
@Service
@Transactional(readOnly = true)
public class AcessoService {

    private final ClienteRepository clientes;
    private final UsuarioRepository usuarios;

    public AcessoService(ClienteRepository clientes, UsuarioRepository usuarios) {
        this.clientes = clientes;
        this.usuarios = usuarios;
    }

    /** Usuário da requisição. Se ele foi removido depois do login, a sessão deixa de valer. */
    public UsuarioLogado atual() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof UsuarioLogado u)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Não autenticado", "Faça login para continuar.");
        }
        if (!usuarios.existsById(u.id())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Não autenticado", "Seu acesso foi removido. Faça login novamente.");
        }
        return u;
    }

    public UsuarioLogado exigirAdmin() {
        UsuarioLogado u = atual();
        if (!u.admin()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Acesso negado", "Operação restrita ao administrador.");
        }
        return u;
    }

    /** Empresa que o usuário pode ver; 404 se não existir ou não for dele. */
    public Cliente clienteAcessivel(Long clienteId) {
        UsuarioLogado u = atual();
        if (!u.admin() && !clienteId.equals(u.clienteId())) {
            throw naoEncontrado(clienteId);
        }
        Cliente c = clientes.findById(clienteId).orElseThrow(() -> naoEncontrado(clienteId));
        if (!u.admin() && !c.isAtivo()) {
            throw empresaDesativada();
        }
        return c;
    }

    /** Empresas visíveis: todas para o ADMIN, só a própria para EMPRESA. */
    public List<Cliente> clientesVisiveis() {
        UsuarioLogado u = atual();
        if (u.admin()) {
            return clientes.findAll().stream().sorted(Comparator.comparing(Cliente::getId)).toList();
        }
        return List.of(clienteAcessivel(u.clienteId()));
    }

    /** Para notas: quem não pode ver a empresa recebe "nota não encontrada". */
    public void exigirAcessoNota(Long notaId, Long clienteIdDaNota) {
        try {
            clienteAcessivel(clienteIdDaNota);
        } catch (RecursoNaoEncontradoException e) {
            throw new RecursoNaoEncontradoException("Nota " + notaId + " não encontrada");
        }
    }

    public static ApiException empresaDesativada() {
        return new ApiException(HttpStatus.FORBIDDEN, "Empresa desativada",
                "O acesso desta empresa está desativado. Fale com o escritório.");
    }

    private static RecursoNaoEncontradoException naoEncontrado(Long clienteId) {
        return new RecursoNaoEncontradoException("Cliente " + clienteId + " não encontrado");
    }
}
