package br.com.tribia.model;

import br.com.tribia.service.nfe.ItemLido;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;

import java.math.BigDecimal;

/** Item (det) de uma {@link Nota}. */
@Entity
public class Item {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "nota_id")
    private Nota nota;

    @Column(nullable = false)
    private Integer nItem;

    private String codigo;

    @Column(length = 500)
    private String descricao;

    @Column(length = 8)
    private String ncm;

    @Column(length = 4)
    private String cfop;

    private String unidade;

    @Column(precision = 19, scale = 4)
    private BigDecimal quantidade;

    @Column(precision = 21, scale = 10)
    private BigDecimal valorUnitario;

    @Column(precision = 15, scale = 2)
    private BigDecimal valorTotal;

    @Column(precision = 15, scale = 2)
    private BigDecimal vIcms;

    @Column(precision = 15, scale = 2)
    private BigDecimal vPis;

    @Column(precision = 15, scale = 2)
    private BigDecimal vCofins;

    /** CST do PIS (o da Cofins é igual na prática). */
    @Column(length = 2)
    private String cstPisCofins;

    /** Bens de uso e consumo pessoal não dão crédito. Editável na revisão. */
    @Column(nullable = false)
    private boolean creditavel = true;

    /** Grupo IBS/CBS já destacado na nota; null quando ausente. */
    @Embedded
    private IbsCbsDestacado ibsCbsDestacado;

    protected Item() {
    }

    static Item de(Nota nota, ItemLido lido) {
        Item i = new Item();
        i.nota = nota;
        i.nItem = lido.nItem();
        i.codigo = lido.codigo();
        i.descricao = lido.descricao();
        i.ncm = lido.ncm();
        i.cfop = lido.cfop();
        i.unidade = lido.unidade();
        i.quantidade = lido.quantidade();
        i.valorUnitario = lido.valorUnitario();
        i.valorTotal = lido.valorTotal();
        i.vIcms = lido.vIcms();
        i.vPis = lido.vPis();
        i.vCofins = lido.vCofins();
        i.cstPisCofins = lido.cstPis() != null ? lido.cstPis() : lido.cstCofins();
        i.ibsCbsDestacado = lido.ibsCbs();
        return i;
    }

    public Long getId() {
        return id;
    }

    public Nota getNota() {
        return nota;
    }

    public Integer getNItem() {
        return nItem;
    }

    public String getCodigo() {
        return codigo;
    }

    public String getDescricao() {
        return descricao;
    }

    public String getNcm() {
        return ncm;
    }

    public String getCfop() {
        return cfop;
    }

    public String getUnidade() {
        return unidade;
    }

    public BigDecimal getQuantidade() {
        return quantidade;
    }

    public BigDecimal getValorUnitario() {
        return valorUnitario;
    }

    public BigDecimal getValorTotal() {
        return valorTotal;
    }

    public BigDecimal getVIcms() {
        return vIcms;
    }

    public BigDecimal getVPis() {
        return vPis;
    }

    public BigDecimal getVCofins() {
        return vCofins;
    }

    public String getCstPisCofins() {
        return cstPisCofins;
    }

    public boolean isCreditavel() {
        return creditavel;
    }

    public IbsCbsDestacado getIbsCbsDestacado() {
        return ibsCbsDestacado;
    }
}
