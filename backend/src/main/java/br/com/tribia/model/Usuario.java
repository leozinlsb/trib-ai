package br.com.tribia.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;

import java.time.Instant;

/**
 * Pessoa que entra no sistema. ADMIN não tem empresa; EMPRESA pertence a exatamente uma.
 * A senha fica só como hash BCrypt.
 */
@Entity
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String nome;

    /** Sempre em minúsculas. */
    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false, length = 100)
    private String senhaHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Papel papel;

    /** Empresa do usuário (null para ADMIN). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cliente_id")
    private Cliente cliente;

    @Column(nullable = false)
    private Instant criadoEm;

    protected Usuario() {
    }

    public static Usuario admin(String nome, String email, String senhaHash) {
        return novo(nome, email, senhaHash, Papel.ADMIN, null);
    }

    public static Usuario daEmpresa(String nome, String email, String senhaHash, Cliente cliente) {
        return novo(nome, email, senhaHash, Papel.EMPRESA, cliente);
    }

    private static Usuario novo(String nome, String email, String senhaHash, Papel papel, Cliente cliente) {
        Usuario u = new Usuario();
        u.nome = nome;
        u.email = email.toLowerCase();
        u.senhaHash = senhaHash;
        u.papel = papel;
        u.cliente = cliente;
        u.criadoEm = Instant.now();
        return u;
    }

    public Long getId() {
        return id;
    }

    public String getNome() {
        return nome;
    }

    public String getEmail() {
        return email;
    }

    public String getSenhaHash() {
        return senhaHash;
    }

    public Papel getPapel() {
        return papel;
    }

    public Cliente getCliente() {
        return cliente;
    }

    public Instant getCriadoEm() {
        return criadoEm;
    }
}
