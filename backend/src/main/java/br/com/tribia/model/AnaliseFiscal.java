package br.com.tribia.model;

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
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Análise fiscal de uma mercadoria (Inteligência Fiscal): a pessoa descreve o produto e o sistema sugere a NCM,
 * com alternativas, fundamentação e verificações. É sugestão para revisão humana, nunca classificação definitiva.
 *
 * Histórico, anexos e resultado são documentos (listas e objetos aninhados) que só são lidos inteiros: ficam em JSON,
 * montados pelo serviço. Os arquivos anexados não são guardados, só nome, tamanho e tipo.
 */
@Entity
@Table(indexes = @Index(name = "idx_analise_fiscal_cliente", columnList = "cliente_id, criadaEm"))
public class AnaliseFiscal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cliente_id")
    private Cliente cliente;

    @Column(nullable = false, length = 200)
    private String mercadoria;

    @Column(nullable = false, length = 4000)
    private String descricao;

    @Column(length = 2000)
    private String composicao;

    @Column(length = 2000)
    private String finalidade;

    @Column(length = 2000)
    private String caracteristicas;

    @Column(length = 8)
    private String ncmAtual;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private StatusAnalise status;

    @Column(length = 8)
    private String ncmSugerida;

    @Column(length = 2000)
    private String mensagem;

    @Column(nullable = false)
    private Instant criadaEm;

    @Column(nullable = false)
    private Instant atualizadaEm;

    /** [{status, em}] */
    @Lob
    private String historicoJson;

    /** [{nome, tamanho, tipo}] */
    @Lob
    private String anexosJson;

    /** resultado, fundamentação, alternativas, validação e fontes */
    @Lob
    private String resultadoJson;

    protected AnaliseFiscal() {
    }

    public AnaliseFiscal(Cliente cliente, String mercadoria, String descricao, String composicao, String finalidade,
                         String caracteristicas, String ncmAtual, String anexosJson, Instant agora) {
        this.cliente = cliente;
        this.mercadoria = mercadoria;
        this.descricao = descricao;
        this.composicao = composicao;
        this.finalidade = finalidade;
        this.caracteristicas = caracteristicas;
        this.ncmAtual = ncmAtual;
        this.anexosJson = anexosJson;
        this.status = StatusAnalise.AGUARDANDO;
        this.criadaEm = agora;
        this.atualizadaEm = agora;
    }

    public void mudarStatus(StatusAnalise novo, String historicoJson, Instant agora) {
        this.status = novo;
        this.historicoJson = historicoJson;
        this.atualizadaEm = agora;
    }

    public void concluir(String ncmSugerida, String resultadoJson, String mensagem) {
        this.ncmSugerida = ncmSugerida;
        this.resultadoJson = resultadoJson;
        this.mensagem = mensagem;
    }

    public Long getId() {
        return id;
    }

    public Cliente getCliente() {
        return cliente;
    }

    public String getMercadoria() {
        return mercadoria;
    }

    public String getDescricao() {
        return descricao;
    }

    public String getComposicao() {
        return composicao;
    }

    public String getFinalidade() {
        return finalidade;
    }

    public String getCaracteristicas() {
        return caracteristicas;
    }

    public String getNcmAtual() {
        return ncmAtual;
    }

    public StatusAnalise getStatus() {
        return status;
    }

    public String getNcmSugerida() {
        return ncmSugerida;
    }

    public String getMensagem() {
        return mensagem;
    }

    public Instant getCriadaEm() {
        return criadaEm;
    }

    public Instant getAtualizadaEm() {
        return atualizadaEm;
    }

    public String getHistoricoJson() {
        return historicoJson;
    }

    public String getAnexosJson() {
        return anexosJson;
    }

    public String getResultadoJson() {
        return resultadoJson;
    }
}
