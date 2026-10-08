package br.com.tribia.model;

import br.com.tribia.service.nfe.NfeLida;
import br.com.tribia.util.CnpjUtil;
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

    /** Sentido da mercadoria em relação ao cliente. Derivado de {@link #operacao}. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private TipoNota tipo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Operacao operacao;

    /** finNFe: 1 normal, 2 complementar, 4 devolução. */
    private Integer finalidade;

    /** CRT do emitente (1, 2 e 4 = Simples Nacional/MEI). */
    private Integer emitenteCrt;

    /** UF e município (IBGE) de destino da mercadoria: onde o IBS é devido. */
    @Column(length = 2)
    private String ufDestino;

    @Column(length = 7)
    private String municipioDestino;

    /**
     * Pagamento ao fornecedor confirmado. A LC 214 (art. 47) condiciona o crédito de 2027 à extinção do débito do
     * fornecedor; sem a confirmação, a compra não gera crédito na simulação. Padrão: true.
     */
    @Column(nullable = false)
    private boolean pagamentoConfirmado = true;

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

    public static Nota de(Cliente cliente, Operacao operacao, NfeLida lida) {
        Nota n = new Nota();
        n.cliente = cliente;
        n.operacao = operacao;
        n.tipo = operacao.tipo();
        n.finalidade = lida.finalidade();
        n.emitenteCrt = lida.crtEmitente();
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
        // contraparte = a outra parte da nota (quem não é o cliente)
        boolean emitidaPeloCliente = CnpjUtil.somenteDigitos(cliente.getCnpj()).equals(lida.emitente().documento());
        NfeLida.Participante contraparte = emitidaPeloCliente ? lida.destinatario() : lida.emitente();
        if (contraparte != null) {
            n.contraparteCnpj = contraparte.documento();
            n.contraparteNome = contraparte.nome();
        }
        // destino da mercadoria: quem a recebe (cliente nas entradas; contraparte nas saídas)
        NfeLida.Participante destino = operacao.tipo() == TipoNota.SAIDA ? contraparte : null;
        n.ufDestino = destino != null && destino.uf() != null ? destino.uf() : cliente.getUf();
        n.municipioDestino = destino != null && destino.codigoMunicipio() != null
                ? destino.codigoMunicipio() : cliente.getCodigoMunicipio();
        n.valorProdutos = lida.valorProdutos();
        n.valorTotal = lida.valorTotal();
        n.importadaEm = Instant.now();
        lida.itens().forEach(i -> n.itens.add(Item.de(n, i)));
        return n;
    }

    public Long getId() {
        return id;
    }

    public Operacao getOperacao() {
        return operacao;
    }

    public Integer getFinalidade() {
        return finalidade;
    }

    public Integer getEmitenteCrt() {
        return emitenteCrt;
    }

    /** Emitente do Simples Nacional (CRT 1, 2 ou 4). */
    public boolean emitenteDoSimples() {
        return emitenteCrt != null && (emitenteCrt == 1 || emitenteCrt == 2 || emitenteCrt == 4);
    }

    public String getUfDestino() {
        return ufDestino;
    }

    public String getMunicipioDestino() {
        return municipioDestino;
    }

    public boolean isPagamentoConfirmado() {
        return pagamentoConfirmado;
    }

    public void setPagamentoConfirmado(boolean pagamentoConfirmado) {
        this.pagamentoConfirmado = pagamentoConfirmado;
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
