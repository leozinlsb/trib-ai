package br.com.tribia.model;

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

/** CST + cClassTrib de um item, sempre validados contra a tabela oficial antes de gravar. */
@Entity
public class Classificacao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "item_id", unique = true)
    private Item item;

    @Column(nullable = false, length = 3)
    private String cst;

    @Column(nullable = false, length = 6)
    private String cClassTrib;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RegimeTributario regime;

    @Column(length = 1000)
    private String justificativa;

    /** 0 a 1. */
    @Column(nullable = false, precision = 3, scale = 2)
    private BigDecimal confianca;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private OrigemClassificacao origem;

    /** Aceita pelo usuário (ou de fonte confiável: XML, manual, cache validado). */
    @Column(nullable = false)
    private boolean aceita;

    /** Conferida por uma pessoa na revisão (aceita ou corrigida): sai da fila mesmo com confiança baixa. */
    @Column(nullable = false)
    private boolean revisada;

    @Column(nullable = false)
    private Instant atualizadaEm;

    protected Classificacao() {
    }

    public Classificacao(Item item) {
        this.item = item;
    }

    /** Substitui o conteúdo (reclassificação). */
    public void definir(String cst, String cClassTrib, RegimeTributario regime, String justificativa,
                        BigDecimal confianca, OrigemClassificacao origem, boolean aceita) {
        this.cst = cst;
        this.cClassTrib = cClassTrib;
        this.regime = regime;
        this.justificativa = justificativa;
        this.confianca = confianca;
        this.origem = origem;
        this.aceita = aceita;
        this.revisada = false;
        this.atualizadaEm = Instant.now();
    }

    /** Revisão: a pessoa confirma a classificação sugerida. */
    public void aceitarNaRevisao() {
        this.aceita = true;
        this.revisada = true;
        this.atualizadaEm = Instant.now();
    }

    /** Revisão: a pessoa informa outra classificação. */
    public void corrigirNaRevisao(String cst, String cClassTrib, RegimeTributario regime, String justificativa) {
        definir(cst, cClassTrib, regime, justificativa, BigDecimal.ONE, OrigemClassificacao.MANUAL, true);
        this.revisada = true;
    }

    public Long getId() {
        return id;
    }

    public Item getItem() {
        return item;
    }

    public String getCst() {
        return cst;
    }

    public String getCClassTrib() {
        return cClassTrib;
    }

    public RegimeTributario getRegime() {
        return regime;
    }

    public String getJustificativa() {
        return justificativa;
    }

    public BigDecimal getConfianca() {
        return confianca;
    }

    public OrigemClassificacao getOrigem() {
        return origem;
    }

    public boolean isAceita() {
        return aceita;
    }

    public boolean isRevisada() {
        return revisada;
    }

    public Instant getAtualizadaEm() {
        return atualizadaEm;
    }
}
