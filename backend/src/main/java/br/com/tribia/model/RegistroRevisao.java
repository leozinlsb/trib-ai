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
 * Trilha de auditoria da revisão: o que mudou em um item, quando, o que era antes e por quê.
 * Sem login no MVP, o autor é o escritório (tribia.revisao.autor).
 */
@Entity
public class RegistroRevisao {

    public enum Acao {
        ACEITE, CORRECAO, CREDITAVEL
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "item_id")
    private Item item;

    @Column(nullable = false)
    private Instant quando;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private Acao acao;

    @Column(length = 120)
    private String autor;

    /** Antes e depois, em texto curto: "200/200032 (CACHE, não aceita)" ou "creditável: sim". */
    @Column(length = 200)
    private String antes;

    @Column(length = 200)
    private String depois;

    @Column(length = 1000)
    private String justificativa;

    /** Item em que a pessoa agiu, quando este recebeu a mudança por ser idêntico (null = foi o próprio). */
    private Long aplicadoAPartirDoItem;

    protected RegistroRevisao() {
    }

    public RegistroRevisao(Item item, Acao acao, String autor, String antes, String depois, String justificativa,
                           Long aplicadoAPartirDoItem) {
        this.item = item;
        this.quando = Instant.now();
        this.acao = acao;
        this.autor = autor;
        this.antes = antes;
        this.depois = depois;
        this.justificativa = justificativa;
        this.aplicadoAPartirDoItem = aplicadoAPartirDoItem;
    }

    public Long getId() {
        return id;
    }

    public Item getItem() {
        return item;
    }

    public Instant getQuando() {
        return quando;
    }

    public Acao getAcao() {
        return acao;
    }

    public String getAutor() {
        return autor;
    }

    public String getAntes() {
        return antes;
    }

    public String getDepois() {
        return depois;
    }

    public String getJustificativa() {
        return justificativa;
    }

    public Long getAplicadoAPartirDoItem() {
        return aplicadoAPartirDoItem;
    }
}
