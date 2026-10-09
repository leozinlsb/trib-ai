package br.com.tribia.apipublica.model;

import br.com.tribia.model.Cliente;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

/**
 * Pedido de classificação de produtos avulsos pela API pública (POST /api/v1/classificacoes). Assíncrono: guarda os
 * produtos enviados, a situação e, ao terminar, o resultado e os avisos. Os itens reservados para a IA contam na cota
 * diária da chave (somados com os envios de NF-e).
 *
 * A empresa é copiada da chave e é o filtro de toda consulta. A restrição única (chave, Idempotency-Key) é a última
 * barreira contra duplicidade.
 */
@Entity
@Table(name = "classificacao_avulsa_api",
        uniqueConstraints = @UniqueConstraint(name = "uk_classificacao_avulsa_api_idempotencia",
                columnNames = {"chave_id", "idempotency_key"}),
        indexes = @Index(name = "idx_classificacao_avulsa_api_chave_criado", columnList = "chave_id, criadoEm"))
public class ClassificacaoAvulsaApi {

    public enum Status {
        /** Produtos que o cache não resolveu estão na IA, em segundo plano. */
        EM_PROCESSAMENTO,
        CONCLUIDA,
        FALHOU
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, length = 36)
    private String publicoId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "chave_id")
    private ChaveApi chave;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cliente_id")
    private Cliente cliente;

    @Column(name = "idempotency_key", length = 100)
    private String idempotencyKey;

    /** SHA-256 (hex) dos produtos normalizados. */
    @Column(length = 64)
    private String hashPayload;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private Status status;

    @Column(nullable = false)
    private int produtos;

    /** Produtos distintos reservados para a IA na criação (contam na cota diária mesmo que a IA falhe). */
    @Column(nullable = false)
    private int itensIa;

    /** Produtos recebidos (lista JSON), para processar em segundo plano e retomar após reinício. */
    @Lob
    private String produtosJson;

    /** Resultado público (lista JSON), quando CONCLUIDA. */
    @Lob
    private String resultadoJson;

    @Lob
    private String avisosJson;

    @Column(length = 1000)
    private String mensagem;

    @Column(nullable = false)
    private Instant criadoEm;

    private Instant finalizadoEm;

    protected ClassificacaoAvulsaApi() {
    }

    public ClassificacaoAvulsaApi(String publicoId, ChaveApi chave, Cliente cliente, String idempotencyKey,
                                  String hashPayload, int produtos, int itensIa, String produtosJson, Instant criadoEm) {
        this.publicoId = publicoId;
        this.chave = chave;
        this.cliente = cliente;
        this.idempotencyKey = idempotencyKey;
        this.hashPayload = hashPayload;
        this.produtos = produtos;
        this.itensIa = itensIa;
        this.produtosJson = produtosJson;
        this.status = Status.EM_PROCESSAMENTO;
        this.criadoEm = criadoEm;
    }

    public void concluir(String resultadoJson, String avisosJson, Instant quando) {
        this.status = Status.CONCLUIDA;
        this.resultadoJson = resultadoJson;
        this.avisosJson = avisosJson;
        this.mensagem = null;
        this.finalizadoEm = quando;
    }

    public void falhar(String mensagem, Instant quando) {
        this.status = Status.FALHOU;
        this.mensagem = mensagem;
        this.finalizadoEm = quando;
    }

    public Long getId() {
        return id;
    }

    public String getPublicoId() {
        return publicoId;
    }

    public ChaveApi getChave() {
        return chave;
    }

    public Cliente getCliente() {
        return cliente;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public String getHashPayload() {
        return hashPayload;
    }

    public Status getStatus() {
        return status;
    }

    public int getProdutos() {
        return produtos;
    }

    public int getItensIa() {
        return itensIa;
    }

    public String getProdutosJson() {
        return produtosJson;
    }

    public String getResultadoJson() {
        return resultadoJson;
    }

    public String getAvisosJson() {
        return avisosJson;
    }

    public String getMensagem() {
        return mensagem;
    }

    public Instant getCriadoEm() {
        return criadoEm;
    }

    public Instant getFinalizadoEm() {
        return finalizadoEm;
    }
}
