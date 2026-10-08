package br.com.tribia.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Cache GLOBAL de classificações por NCM + descrição normalizada, compartilhado entre clientes (de propósito:
 * quanto mais clientes, menos chamadas à IA). Alimentado pelo seed (classificacoes.json), pela IA e pela revisão.
 */
@Entity
public class ClassificacaoCache {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** NCM + "|" + descrição normalizada. Ver {@link br.com.tribia.util.ChaveClassificacao}. */
    @Column(nullable = false, unique = true, length = 520)
    private String chave;

    @Column(length = 8)
    private String ncm;

    @Column(nullable = false, length = 3)
    private String cst;

    @Column(nullable = false, length = 6)
    private String cClassTrib;

    @Column(length = 1000)
    private String justificativa;

    @Column(nullable = false, precision = 3, scale = 2)
    private BigDecimal confianca;

    /** Origem da entrada do cache: SEED, IA ou MANUAL. */
    @Column(nullable = false, length = 10)
    private String fonte;

    /** Revisada por pessoa (seed ou correção manual): itens que vêm dela já nascem aceitos. */
    @Column(nullable = false)
    private boolean validada;

    @Column(nullable = false)
    private Instant atualizadaEm;

    protected ClassificacaoCache() {
    }

    public ClassificacaoCache(String chave, String ncm) {
        this.chave = chave;
        this.ncm = ncm;
    }

    public void definir(String cst, String cClassTrib, String justificativa, BigDecimal confianca, String fonte,
                        boolean validada) {
        this.cst = cst;
        this.cClassTrib = cClassTrib;
        this.justificativa = justificativa;
        this.confianca = confianca;
        this.fonte = fonte;
        this.validada = validada;
        this.atualizadaEm = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getChave() {
        return chave;
    }

    public String getNcm() {
        return ncm;
    }

    public String getCst() {
        return cst;
    }

    public String getCClassTrib() {
        return cClassTrib;
    }

    public String getJustificativa() {
        return justificativa;
    }

    public BigDecimal getConfianca() {
        return confianca;
    }

    public String getFonte() {
        return fonte;
    }

    public boolean isValidada() {
        return validada;
    }
}
