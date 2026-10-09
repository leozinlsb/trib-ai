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
import java.util.Set;

/**
 * Chave de API de um integrador externo (ERP, sistema contábil), presa a UMA empresa: quem usa a chave nunca escolhe
 * a empresa, ela vem daqui. A chave completa só existe na resposta da criação; aqui fica o prefixo (público, serve
 * para achar a linha e aparecer em listagens e logs) e o SHA-256 da chave inteira.
 *
 * Limites nulos usam os padrões de tribia.api-publica.*.
 */
@Entity
@Table(name = "chave_api", indexes = @Index(name = "idx_chave_api_cliente", columnList = "cliente_id"))
public class ChaveApi {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 16)
    private String prefixo;

    /** SHA-256 (hex) da chave completa. */
    @Column(nullable = false, length = 64)
    private String hashSegredo;

    @Column(nullable = false, length = 100)
    private String nomeIntegrador;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cliente_id")
    private Cliente cliente;

    /** EscopoApi separados por vírgula. */
    @Column(nullable = false, length = 200)
    private String escopos;

    @Column(nullable = false)
    private Instant criadaEm;

    @Column(nullable = false, length = 200)
    private String criadaPor;

    private Instant expiraEm;

    private Instant revogadaEm;

    @Column(length = 200)
    private String revogadaPor;

    private Instant ultimoUsoEm;

    private Integer requisicoesPorMinuto;

    private Integer cotaDiariaAnalises;

    private Integer maxAnalisesSimultaneas;

    protected ChaveApi() {
    }

    public ChaveApi(String prefixo, String hashSegredo, String nomeIntegrador, Cliente cliente, Set<EscopoApi> escopos,
                    Instant criadaEm, String criadaPor, Instant expiraEm, Integer requisicoesPorMinuto,
                    Integer cotaDiariaAnalises, Integer maxAnalisesSimultaneas) {
        this.prefixo = prefixo;
        this.hashSegredo = hashSegredo;
        this.nomeIntegrador = nomeIntegrador;
        this.cliente = cliente;
        this.escopos = EscopoApi.juntar(escopos);
        this.criadaEm = criadaEm;
        this.criadaPor = criadaPor;
        this.expiraEm = expiraEm;
        this.requisicoesPorMinuto = requisicoesPorMinuto;
        this.cotaDiariaAnalises = cotaDiariaAnalises;
        this.maxAnalisesSimultaneas = maxAnalisesSimultaneas;
    }

    public void revogar(Instant quando, String quem) {
        if (revogadaEm == null) {
            revogadaEm = quando;
            revogadaPor = quem;
        }
    }

    public boolean revogada() {
        return revogadaEm != null;
    }

    public boolean expirada(Instant agora) {
        return expiraEm != null && !agora.isBefore(expiraEm);
    }

    public Long getId() {
        return id;
    }

    public String getPrefixo() {
        return prefixo;
    }

    public String getHashSegredo() {
        return hashSegredo;
    }

    public String getNomeIntegrador() {
        return nomeIntegrador;
    }

    public Cliente getCliente() {
        return cliente;
    }

    public Set<EscopoApi> getEscopos() {
        return EscopoApi.separar(escopos);
    }

    public Instant getCriadaEm() {
        return criadaEm;
    }

    public String getCriadaPor() {
        return criadaPor;
    }

    public Instant getExpiraEm() {
        return expiraEm;
    }

    public Instant getRevogadaEm() {
        return revogadaEm;
    }

    public String getRevogadaPor() {
        return revogadaPor;
    }

    public Instant getUltimoUsoEm() {
        return ultimoUsoEm;
    }

    public Integer getRequisicoesPorMinuto() {
        return requisicoesPorMinuto;
    }

    public Integer getCotaDiariaAnalises() {
        return cotaDiariaAnalises;
    }

    public Integer getMaxAnalisesSimultaneas() {
        return maxAnalisesSimultaneas;
    }
}
