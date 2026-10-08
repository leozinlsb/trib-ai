package br.com.tribia.model;

import br.com.tribia.client.calculadora.OrigemCalculo;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Resultado do cálculo de um item: tributos de 2027 (vCbs, vIbs*, vIs) e o que conta na apuração
 * hoje e em 2027 ({@link #impostoHoje} e {@link #imposto2027}, já como débito ou crédito conforme {@link #natureza}).
 */
@Entity
public class Calculo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "item_id", unique = true)
    private Item item;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Natureza natureza;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private OrigemCalculo origemValores;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal vCbs;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal vIbsUf;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal vIbsMun;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal vIs;

    @Column(precision = 7, scale = 4)
    private BigDecimal pCbs;

    @Column(precision = 7, scale = 4)
    private BigDecimal pIbsUf;

    @Column(precision = 7, scale = 4)
    private BigDecimal pIbsMun;

    @Column(precision = 7, scale = 4)
    private BigDecimal reducaoCbs;

    @Column(precision = 7, scale = 4)
    private BigDecimal reducaoIbs;

    @Column(precision = 7, scale = 4)
    private BigDecimal pIs;

    /** O NCM está no campo do Imposto Seletivo (mesmo que o cliente, como revendedor, não pague IS). */
    @Column(nullable = false)
    private boolean sujeitoIs;

    /** PIS + Cofins de hoje: débito (saída) ou crédito (entrada). */
    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal impostoHoje;

    /** CBS + IBS (+ IS na saída) de 2027: débito (saída) ou crédito (entrada). */
    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal imposto2027;

    /** Alíquotas informadas por nós (CBS de 2027 ainda não é oficial). */
    @Column(nullable = false)
    private boolean simulado;

    @Column(nullable = false)
    private Instant calculadoEm;

    protected Calculo() {
    }

    public Calculo(Item item, Natureza natureza, OrigemCalculo origemValores, BigDecimal vCbs, BigDecimal vIbsUf,
                   BigDecimal vIbsMun, BigDecimal vIs, BigDecimal pCbs, BigDecimal pIbsUf, BigDecimal pIbsMun,
                   BigDecimal reducaoCbs, BigDecimal reducaoIbs, BigDecimal pIs, boolean sujeitoIs,
                   BigDecimal impostoHoje, BigDecimal imposto2027, boolean simulado) {
        this.item = item;
        this.natureza = natureza;
        this.origemValores = origemValores;
        this.vCbs = vCbs;
        this.vIbsUf = vIbsUf;
        this.vIbsMun = vIbsMun;
        this.vIs = vIs;
        this.pCbs = pCbs;
        this.pIbsUf = pIbsUf;
        this.pIbsMun = pIbsMun;
        this.reducaoCbs = reducaoCbs;
        this.reducaoIbs = reducaoIbs;
        this.pIs = pIs;
        this.sujeitoIs = sujeitoIs;
        this.impostoHoje = impostoHoje;
        this.imposto2027 = imposto2027;
        this.simulado = simulado;
        this.calculadoEm = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Item getItem() {
        return item;
    }

    public Natureza getNatureza() {
        return natureza;
    }

    public OrigemCalculo getOrigemValores() {
        return origemValores;
    }

    public BigDecimal getVCbs() {
        return vCbs;
    }

    public BigDecimal getVIbsUf() {
        return vIbsUf;
    }

    public BigDecimal getVIbsMun() {
        return vIbsMun;
    }

    public BigDecimal getVIs() {
        return vIs;
    }

    public BigDecimal getPCbs() {
        return pCbs;
    }

    public BigDecimal getPIbsUf() {
        return pIbsUf;
    }

    public BigDecimal getPIbsMun() {
        return pIbsMun;
    }

    public BigDecimal getReducaoCbs() {
        return reducaoCbs;
    }

    public BigDecimal getReducaoIbs() {
        return reducaoIbs;
    }

    public BigDecimal getPIs() {
        return pIs;
    }

    public boolean isSujeitoIs() {
        return sujeitoIs;
    }

    public BigDecimal getImpostoHoje() {
        return impostoHoje;
    }

    public BigDecimal getImposto2027() {
        return imposto2027;
    }

    public boolean isSimulado() {
        return simulado;
    }

    public Instant getCalculadoEm() {
        return calculadoEm;
    }
}
