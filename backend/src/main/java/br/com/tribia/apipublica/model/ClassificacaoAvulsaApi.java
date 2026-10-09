package br.com.tribia.apipublica.model;

import br.com.tribia.model.Cliente;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Registro de uma classificação de produtos avulsos pela API pública (POST /api/v1/classificacoes). Serve para a cota
 * diária de itens enviados à IA (somada com os envios de NF-e) e para auditoria do consumo. Não guarda os produtos.
 */
@Entity
@Table(name = "classificacao_avulsa_api",
        indexes = @Index(name = "idx_classificacao_avulsa_api_chave_criado", columnList = "chave_id, criadoEm"))
public class ClassificacaoAvulsaApi {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "chave_id")
    private ChaveApi chave;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cliente_id")
    private Cliente cliente;

    @Column(nullable = false)
    private int produtos;

    /** Produtos distintos enviados à IA (contam na cota diária). */
    @Column(nullable = false)
    private int itensIa;

    @Column(nullable = false)
    private Instant criadoEm;

    protected ClassificacaoAvulsaApi() {
    }

    public ClassificacaoAvulsaApi(ChaveApi chave, Cliente cliente, int produtos, int itensIa, Instant criadoEm) {
        this.chave = chave;
        this.cliente = cliente;
        this.produtos = produtos;
        this.itensIa = itensIa;
        this.criadoEm = criadoEm;
    }

    public Long getId() {
        return id;
    }

    public int getProdutos() {
        return produtos;
    }

    public int getItensIa() {
        return itensIa;
    }

    public Instant getCriadoEm() {
        return criadoEm;
    }
}
