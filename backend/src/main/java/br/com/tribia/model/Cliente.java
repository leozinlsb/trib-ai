package br.com.tribia.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import org.hibernate.annotations.ColumnDefault;

/**
 * Cliente do escritório de contabilidade (na interface: "empresa"). Os de demonstração vêm do data.sql;
 * o administrador cadastra os demais. Remover = desativar ({@link #ativo}): as notas são preservadas.
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

    private String email;

    @Column(length = 30)
    private String telefone;

    private String responsavel;

    @Column(length = 2000)
    private String observacoes;

    /** false = desativada: some das listas de trabalho, não recebe notas e os usuários dela perdem o acesso. */
    @Column(nullable = false)
    @ColumnDefault("true")
    private boolean ativo = true;

    protected Cliente() {
    }

    public static Cliente nova(String cnpj, Dados dados) {
        Cliente c = new Cliente();
        c.cnpj = cnpj;
        c.atualizar(dados);
        return c;
    }

    /** Dados cadastrais editáveis (o CNPJ é tratado à parte: as notas são vinculadas por ele). */
    public record Dados(String razaoSocial, String nomeFantasia, Regime regime, String setor, String uf,
                        String municipio, String codigoMunicipio, String email, String telefone,
                        String responsavel, String observacoes) {
    }

    public void atualizar(Dados d) {
        this.razaoSocial = d.razaoSocial();
        this.nomeFantasia = d.nomeFantasia();
        this.regime = d.regime();
        this.setor = d.setor();
        this.uf = d.uf();
        this.municipio = d.municipio();
        this.codigoMunicipio = d.codigoMunicipio();
        this.email = d.email();
        this.telefone = d.telefone();
        this.responsavel = d.responsavel();
        this.observacoes = d.observacoes();
    }

    public void alterarCnpj(String cnpj) {
        this.cnpj = cnpj;
    }

    public void desativar() {
        this.ativo = false;
    }

    public void reativar() {
        this.ativo = true;
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

    public String getEmail() {
        return email;
    }

    public String getTelefone() {
        return telefone;
    }

    public String getResponsavel() {
        return responsavel;
    }

    public String getObservacoes() {
        return observacoes;
    }

    public boolean isAtivo() {
        return ativo;
    }
}
