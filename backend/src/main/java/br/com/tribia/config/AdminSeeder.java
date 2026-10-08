package br.com.tribia.config;

import br.com.tribia.model.Papel;
import br.com.tribia.model.Usuario;
import br.com.tribia.repository.UsuarioRepository;
import br.com.tribia.security.UsuarioLogado;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;

/**
 * Cria o administrador na inicialização, se não houver nenhum.
 * A senha vem de tribia.admin.senha (ex.: variável de ambiente TRIBIA_ADMIN_SENHA). Sem ela, uma senha
 * aleatória é gerada e mostrada uma vez no log. Nenhuma senha fica no código.
 */
@Component
@Order(0)
public class AdminSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminSeeder.class);
    private static final String ALFABETO = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789";

    private final UsuarioRepository usuarios;
    private final PasswordEncoder encoder;
    private final String nome;
    private final String email;
    private final String senha;

    public AdminSeeder(UsuarioRepository usuarios, PasswordEncoder encoder,
                       @Value("${tribia.admin.nome:Administrador}") String nome,
                       @Value("${tribia.admin.email:admin@tribia.local}") String email,
                       @Value("${tribia.admin.senha:}") String senha) {
        this.usuarios = usuarios;
        this.encoder = encoder;
        this.nome = nome;
        this.email = email;
        this.senha = senha;
    }

    @Override
    public void run(ApplicationArguments args) {
        garantirAdministrador();
    }

    /** Usado pelo seed antes de o servidor aceitar requisições; nunca cria sessão HTTP. */
    public UsuarioLogado principalInicializacao() {
        garantirAdministrador();
        return UsuarioLogado.de(usuarios.findFirstByPapelOrderByIdAsc(Papel.ADMIN).orElseThrow()).semSenha();
    }

    private void garantirAdministrador() {
        if (usuarios.existsByPapel(Papel.ADMIN)) {
            return;
        }
        String s = senha == null || senha.isBlank() ? gerar() : senha;
        usuarios.save(Usuario.admin(nome, email, encoder.encode(s)));
        if (senha == null || senha.isBlank()) {
            log.warn("Administrador criado: {} / senha gerada: {}  (defina TRIBIA_ADMIN_SENHA para fixar a senha)", email, s);
        } else {
            log.info("Administrador criado: {}", email);
        }
    }

    private static String gerar() {
        SecureRandom r = new SecureRandom();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 14; i++) {
            sb.append(ALFABETO.charAt(r.nextInt(ALFABETO.length())));
        }
        return sb.toString();
    }
}
