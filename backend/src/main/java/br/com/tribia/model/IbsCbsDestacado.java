package br.com.tribia.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.math.BigDecimal;

/**
 * Grupo IBS/CBS já destacado no item da NF-e (obrigatório no regime regular desde 03/08/2026).
 * Quando o item não traz o grupo, o objeto fica null no {@link Item}.
 *
 * TODO conferir os nomes exatos das tags na NT 2025.002 (layout RTC) antes da demo.
 * Caminhos usados hoje: imposto/IBSCBS/{CST, cClassTrib, gIBSCBS/{vBC, gIBSUF/{pIBSUF, vIBSUF},
 * gIBSMun/{pIBSMun, vIBSMun}, vIBS, gCBS/{pCBS, vCBS}}}.
 */
@Embeddable
public class IbsCbsDestacado {

    @Column(name = "ibscbs_cst", length = 3)
    private String cst;

    @Column(name = "ibscbs_cclasstrib", length = 6)
    private String cClassTrib;

    @Column(name = "ibscbs_vbc", precision = 15, scale = 2)
    private BigDecimal vBc;

    @Column(name = "ibscbs_pibsuf", precision = 7, scale = 4)
    private BigDecimal pIbsUf;

    @Column(name = "ibscbs_vibsuf", precision = 15, scale = 2)
    private BigDecimal vIbsUf;

    @Column(name = "ibscbs_pibsmun", precision = 7, scale = 4)
    private BigDecimal pIbsMun;

    @Column(name = "ibscbs_vibsmun", precision = 15, scale = 2)
    private BigDecimal vIbsMun;

    @Column(name = "ibscbs_vibs", precision = 15, scale = 2)
    private BigDecimal vIbs;

    @Column(name = "ibscbs_pcbs", precision = 7, scale = 4)
    private BigDecimal pCbs;

    @Column(name = "ibscbs_vcbs", precision = 15, scale = 2)
    private BigDecimal vCbs;

    protected IbsCbsDestacado() {
    }

    public IbsCbsDestacado(String cst, String cClassTrib, BigDecimal vBc,
                           BigDecimal pIbsUf, BigDecimal vIbsUf, BigDecimal pIbsMun, BigDecimal vIbsMun,
                           BigDecimal vIbs, BigDecimal pCbs, BigDecimal vCbs) {
        this.cst = cst;
        this.cClassTrib = cClassTrib;
        this.vBc = vBc;
        this.pIbsUf = pIbsUf;
        this.vIbsUf = vIbsUf;
        this.pIbsMun = pIbsMun;
        this.vIbsMun = vIbsMun;
        this.vIbs = vIbs;
        this.pCbs = pCbs;
        this.vCbs = vCbs;
    }

    public String getCst() {
        return cst;
    }

    public String getCClassTrib() {
        return cClassTrib;
    }

    public BigDecimal getVBc() {
        return vBc;
    }

    public BigDecimal getPIbsUf() {
        return pIbsUf;
    }

    public BigDecimal getVIbsUf() {
        return vIbsUf;
    }

    public BigDecimal getPIbsMun() {
        return pIbsMun;
    }

    public BigDecimal getVIbsMun() {
        return vIbsMun;
    }

    public BigDecimal getVIbs() {
        return vIbs;
    }

    public BigDecimal getPCbs() {
        return pCbs;
    }

    public BigDecimal getVCbs() {
        return vCbs;
    }
}
