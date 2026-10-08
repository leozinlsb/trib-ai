package br.com.tribia.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

/**
 * Cliente do escritório de contabilidade. Não há telas de cadastro:
 * os clientes são carregados na inicialização (data.sql).
 */
@Entity
public class Cliente {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Somente dígitos (14). */
    @Column(nullable = false, unique = true, length = 14)
    private String cnpj;

    @Column(nullable = false)
    private String razaoSocial;

    private String nomeFantasia;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Regime regime;

    private String setor;

    @Column(length = 2)
    private String uf;

    private String municipio;

    /** Código IBGE do município (7 dígitos). A calculadora oficial usa para o IBS municipal. */
    @Column(length = 7)
    private String codigoMunicipio;

    /**
     * Fabrica (ou importa) o que vende. O Imposto Seletivo é monofásico: incide no primeiro fornecimento, pelo
     * fabricante (CST 000 / 000001); quem só revende usa CST 200 / 200007 e não paga IS.
     */
    @Column(nullable = false)
    private boolean fabricante;

    protected Cliente() {
    }

    public Long getId() {
        return id;
    }

    public String getCnpj() {
        return cnpj;
    }

    public String getRazaoSocial() {
        return razaoSocial;
    }

    public String getNomeFantasia() {
        return nomeFantasia;
    }

    public Regime getRegime() {
        return regime;
    }

    public String getSetor() {
        return setor;
    }

    public String getUf() {
        return uf;
    }

    public String getMunicipio() {
        return municipio;
    }

    public String getCodigoMunicipio() {
        return codigoMunicipio;
    }

    public boolean isFabricante() {
        return fabricante;
    }
}
