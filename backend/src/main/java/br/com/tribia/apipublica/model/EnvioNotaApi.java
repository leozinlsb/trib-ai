package br.com.tribia.apipublica.model;

import br.com.tribia.model.Cliente;
import br.com.tribia.model.Nota;
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
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

/**
 * NF-e recebida pela API pública. Aponta para a {@link Nota} da plataforma (a mesma que aparece no site da empresa)
 * e guarda o que só interessa à integração: id público (UUID), chave que enviou, Idempotency-Key com o hash do
 * corpo, referência do sistema externo, situação do processamento e quantos itens foram reservados para a IA (cota).
 *
 * A empresa é copiada da chave e é o filtro de toda consulta. A restrição única (chave, Idempotency-Key) é a última
 * barreira contra duplicidade em requisições simultâneas.
 */
@Entity
@Table(name = "envio_nota_api",
        uniqueConstraints = @UniqueConstraint(name = "uk_envio_nota_api_idempotencia",
                columnNames = {"chave_id", "idempotency_key"}),
        indexes = {
                @Index(name = "idx_envio_nota_api_chave_criado", columnList = "chave_id, criadoEm"),
                @Index(name = "idx_envio_nota_api_cliente_ref", columnList = "cliente_id, referenciaExterna")
        })
public class EnvioNotaApi {

    public enum Status {
        /** Importada; classificação (IA, se preciso) e cálculo de 2027 em segundo plano. */
        EM_PROCESSAMENTO,
        /** Classificação e cálculo terminados (itens podem continuar aguardando revisão humana). */
        CONCLUIDA,
        /** O processamento falhou depois da importação; a nota continua na plataforma. */
        FALHOU
    }

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
    @JoinColumn(name = "nota_id", unique = true)
    private Nota nota;

    @Column(name = "idempotency_key", length = 100)
    private String idempotencyKey;

    /** SHA-256 (hex) do corpo normalizado (XML + referência). */
    @Column(nullable = false, length = 64)
    private String hashPayload;

    @Column(length = 100)
    private String referenciaExterna;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status;

    /** Itens reservados para a IA na criação (contam na cota diária mesmo que a IA falhe). */
    @Column(nullable = false)
    private int itensIa;

    /** false quando a cota do dia não comportava os itens: classifica só por XML/cache e avisa. */
    @Column(nullable = false)
    private boolean usarIa;

    /** Avisos do processamento (lista JSON). */
    @Lob
    private String avisosJson;

    @Column(length = 1000)
    private String mensagem;

    @Column(nullable = false)
    private Instant criadoEm;

    private Instant finalizadoEm;

    protected EnvioNotaApi() {
    }

    public EnvioNotaApi(String publicoId, ChaveApi chave, Cliente cliente, Nota nota, String idempotencyKey,
                        String hashPayload, String referenciaExterna, int itensIa, boolean usarIa, Instant criadoEm) {
        this.publicoId = publicoId;
        this.chave = chave;
        this.cliente = cliente;
        this.nota = nota;
        this.idempotencyKey = idempotencyKey;
        this.hashPayload = hashPayload;
        this.referenciaExterna = referenciaExterna;
        this.itensIa = itensIa;
        this.usarIa = usarIa;
        this.status = Status.EM_PROCESSAMENTO;
        this.criadoEm = criadoEm;
    }

    public void concluir(String avisosJson, Instant quando) {
        this.status = Status.CONCLUIDA;
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

    public Nota getNota() {
        return nota;
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

    public Status getStatus() {
        return status;
    }

    public int getItensIa() {
        return itensIa;
    }

    public boolean isUsarIa() {
        return usarIa;
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
