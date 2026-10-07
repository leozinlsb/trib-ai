package br.com.tribia.model;

import br.com.tribia.service.nfe.NfeLida;
import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

/**
 * NF-e importada para um cliente. A mesma chave pode existir em clientes diferentes
 * (a venda de um é a compra do outro), mas não duas vezes no mesmo cliente.
 */
@Entity
@Table(uniqueConstraints = @UniqueConstraint(name = "uk_nota_cliente_chave", columnNames = {"cliente_id", "chave"}))
public class Nota {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cliente_id")
    private Cliente cliente;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private TipoNota tipo;

    @Column(nullable = false, length = 44)
    private String chave;

    private Long numero;

    private Integer serie;

    @Column(nullable = false)
    private LocalDate dataEmissao;

    /** AAAA-MM da data de emissão. */
    @Column(nullable = false, length = 7)
    private String competencia;

    @Column(length = 14)
    private String emitenteCnpj;

    private String emitenteNome;

    /** CNPJ ou CPF do destinatário. */
    @Column(length = 14)
    private String destinatarioDocumento;

    private String destinatarioNome;

    /** Cliente final (nas saídas) ou fornecedor (nas entradas). CNPJ ou CPF, só dígitos. */
    @Column(length = 14)
    private String contraparteCnpj;

    private String contraparteNome;

    @Column(precision = 15, scale = 2)
    private BigDecimal valorProdutos;

    @Column(precision = 15, scale = 2)
    private BigDecimal valorTotal;

    @Column(nullable = false)
    private Instant importadaEm;

    @OneToMany(mappedBy = "nota", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("nItem")
    private List<Item> itens = new ArrayList<>();

    protected Nota() {
    }

    public static Nota de(Cliente cliente, TipoNota tipo, NfeLida lida) {
        Nota n = new Nota();
        n.cliente = cliente;
        n.tipo = tipo;
        n.chave = lida.chave();
        n.numero = lida.numero();
        n.serie = lida.serie();
        n.dataEmissao = lida.dataEmissao();
        n.competencia = YearMonth.from(lida.dataEmissao()).toString();
        n.emitenteCnpj = lida.emitente().documento();
        n.emitenteNome = lida.emitente().nome();
        if (lida.destinatario() != null) {
            n.destinatarioDocumento = lida.destinatario().documento();
            n.destinatarioNome = lida.destinatario().nome();
        }
        NfeLida.Participante contraparte = tipo == TipoNota.SAIDA ? lida.destinatario() : lida.emitente();
        if (contraparte != null) {
            n.contraparteCnpj = contraparte.documento();
            n.contraparteNome = contraparte.nome();
        }
        n.valorProdutos = lida.valorProdutos();
        n.valorTotal = lida.valorTotal();
        n.importadaEm = Instant.now();
        lida.itens().forEach(i -> n.itens.add(Item.de(n, i)));
        return n;
    }

    public Long getId() {
        return id;
    }

    public Cliente getCliente() {
        return cliente;
    }

    public TipoNota getTipo() {
        return tipo;
    }

    public String getChave() {
        return chave;
    }

    public Long getNumero() {
        return numero;
    }

    public Integer getSerie() {
        return serie;
    }

    public LocalDate getDataEmissao() {
        return dataEmissao;
    }

    public String getCompetencia() {
        return competencia;
    }

    public String getEmitenteCnpj() {
        return emitenteCnpj;
    }

    public String getEmitenteNome() {
        return emitenteNome;
    }

    public String getDestinatarioDocumento() {
        return destinatarioDocumento;
    }

    public String getDestinatarioNome() {
        return destinatarioNome;
    }

    public String getContraparteCnpj() {
        return contraparteCnpj;
    }

    public String getContraparteNome() {
        return contraparteNome;
    }

    public BigDecimal getValorProdutos() {
        return valorProdutos;
    }

    public BigDecimal getValorTotal() {
        return valorTotal;
    }

    public Instant getImportadaEm() {
        return importadaEm;
    }

    public List<Item> getItens() {
        return itens;
    }
}
