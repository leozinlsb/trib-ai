package br.com.tribia.apipublica.model;

import br.com.tribia.model.AnaliseFiscal;
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
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

/**
 * Pedido de análise recebido pela API pública. Aponta para a {@link AnaliseFiscal} do motor (a mesma da plataforma) e
 * guarda o que só interessa à integração: o identificador público (UUID, não sequencial), a chave que pediu, a
 * Idempotency-Key com o hash do corpo e a referência do sistema externo.
 *
 * A empresa é copiada da chave na criação e é o filtro de toda consulta. A restrição única (chave, Idempotency-Key)
 * é a última barreira contra duplicidade em requisições simultâneas.
 */
@Entity
@Table(name = "solicitacao_api",
        uniqueConstraints = @UniqueConstraint(name = "uk_solicitacao_api_idempotencia",
                columnNames = {"chave_id", "idempotency_key"}),
        indexes = {
                @Index(name = "idx_solicitacao_api_chave_criada", columnList = "chave_id, criadaEm"),
                @Index(name = "idx_solicitacao_api_cliente_ref", columnList = "cliente_id, referenciaExterna")
        })
public class SolicitacaoApi {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 36)
    private String publicoId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "chave_id")
    private ChaveApi chave;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cliente_id")
    private Cliente cliente;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "analise_id", unique = true)
    private AnaliseFiscal analise;

    @Column(name = "idempotency_key", length = 100)
    private String idempotencyKey;

    /** SHA-256 (hex) do corpo normalizado. */
    @Column(nullable = false, length = 64)
    private String hashPayload;

    @Column(length = 100)
    private String referenciaExterna;

    @Column(nullable = false)
    private Instant criadaEm;

    protected SolicitacaoApi() {
    }

    public SolicitacaoApi(String publicoId, ChaveApi chave, Cliente cliente, AnaliseFiscal analise,
                          String idempotencyKey, String hashPayload, String referenciaExterna, Instant criadaEm) {
        this.publicoId = publicoId;
        this.chave = chave;
        this.cliente = cliente;
        this.analise = analise;
        this.idempotencyKey = idempotencyKey;
        this.hashPayload = hashPayload;
        this.referenciaExterna = referenciaExterna;
        this.criadaEm = criadaEm;
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

    public AnaliseFiscal getAnalise() {
        return analise;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public String getHashPayload() {
        return hashPayload;
    }

    public String getReferenciaExterna() {
        return referenciaExterna;
    }

    public Instant getCriadaEm() {
        return criadaEm;
    }
}
