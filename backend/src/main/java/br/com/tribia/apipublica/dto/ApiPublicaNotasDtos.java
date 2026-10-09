package br.com.tribia.apipublica.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Contrato público v1 de notas fiscais e comparativo (fase 1: expõe o que a plataforma já faz). Independente dos DTOs
 * internos. Todo valor de 2027 é projeção pendente de validação fiscal ({@link #NATUREZA_PROJECAO}).
 */
public final class ApiPublicaNotasDtos {

    private ApiPublicaNotasDtos() {
    }

    /** Valor de "natureza" de toda resposta com valores de 2027. */
    public static final String NATUREZA_PROJECAO = "PROJECAO_PENDENTE_VALIDACAO";

    // ---------------- entrada ----------------

    @Schema(description = "NF-e (modelo 55) da empresa da chave: o XML completo como texto.")
    public record EnvioNota(
            @Schema(description = "Identificador do documento no sistema de origem. Opcional; volta nas respostas e "
                    + "filtra a listagem.", example = "ERP-NF-000123", maxLength = 100)
            @Size(max = 100, message = "referenciaExterna com até 100 caracteres.")
            @Pattern(regexp = "^[A-Za-z0-9._:/#-]*$", message = "referenciaExterna aceita letras, números e . _ : / # -")
            String referenciaExterna,
            @Schema(description = "XML da NF-e (nfeProc ou NFe), como texto UTF-8.", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotBlank(message = "Envie o XML da nota.")
            @Size(max = 2_000_000, message = "xml com até 2.000.000 caracteres.")
            String xml) {
    }

    // ---------------- saída ----------------

    @Schema(description = """
            Processamento da nota. EM_PROCESSAMENTO: classificação (XML, cache ou IA) e cálculo de 2027 em curso.
            CONCLUIDA: classificação e cálculo terminados (itens podem aguardar revisão humana: ver
            itensPendentesRevisao). FALHOU: erro depois da importação (ver erro.mensagem); a nota continua na
            plataforma.""")
    public enum StatusNota {
        EM_PROCESSAMENTO, CONCLUIDA, FALHOU
    }

    @Schema(description = """
            Situação da classificação do item. CONFIRMADA: veio do XML, foi aceita/revisada por uma pessoa ou tem
            confiança suficiente. PENDENTE_REVISAO: sugestão automática (IA, cache, regra) que uma pessoa precisa
            conferir na plataforma. SEM_CLASSIFICACAO: não houve evidência para sugerir; fica fora do cálculo.""")
    public enum SituacaoClassificacao {
        CONFIRMADA, PENDENTE_REVISAO, SEM_CLASSIFICACAO
    }

    public record NotaPublica(
            @Schema(description = "Id público (UUID) do envio") String id,
            String referenciaExterna,
            StatusNota status,
            boolean finalizada,
            Instant recebidaEm,
            Instant finalizadaEm,
            @Schema(description = "Sempre PROJECAO_PENDENTE_VALIDACAO: os valores de 2027 não são apuração definitiva")
            String natureza,
            Documento documento,
            @Schema(description = "Itens com classificação e cálculo; vazio enquanto EM_PROCESSAMENTO")
            List<ItemNota> itens,
            @Schema(description = "Imposto da nota: hoje (PIS/Cofins) x 2027 (CBS/IBS/IS); null enquanto EM_PROCESSAMENTO")
            Comparativo comparativo,
            int itensPendentesRevisao,
            @Schema(description = "Itens desta nota que foram para a IA (contam na cota diária da chave)")
            int itensClassificadosPorIa,
            Erro erro,
            List<String> avisos) {
    }

    public record Documento(String chaveAcesso, Long numero, Integer serie, LocalDate dataEmissao, String competencia,
                            @Schema(description = "VENDA, COMPRA, DEVOLUCAO_DE_VENDA ou DEVOLUCAO_DE_COMPRA") String operacao,
                            @Schema(description = "DEBITO (vendas) ou CREDITO (compras)") String natureza,
                            String contraparteDocumento, String contraparteNome, BigDecimal valorTotal) {
    }

    public record ItemNota(int nItem, String codigo, String descricao, String ncm, String cfop, BigDecimal quantidade,
                           BigDecimal valorTotal, ClassificacaoItem classificacao, CalculoItem calculo) {
    }

    public record ClassificacaoItem(
            SituacaoClassificacao situacao,
            String cst, String cClassTrib, String nomeCClassTrib,
            @Schema(description = "INTEGRAL, REDUZIDA, ALIQUOTA_ZERO, SEM_INCIDENCIA ou OUTRO") String regime,
            String descricaoRegime,
            @Schema(description = "XML, CACHE, IA, REGRA ou MANUAL") String origem,
            BigDecimal confianca, String justificativa) {
    }

    @Schema(description = "Valores com sinal: devoluções estornam (negativos). 2027 = projeção.")
    public record CalculoItem(BigDecimal impostoHoje, BigDecimal imposto2027, BigDecimal cbs, BigDecimal ibsUf,
                              BigDecimal ibsMun, BigDecimal impostoSeletivo, BigDecimal aliquotaCbs,
                              boolean sujeitoImpostoSeletivo,
                              @Schema(description = "CALCULADORA (oficial da Receita) ou SIMPLIFICADA") String origem) {
    }

    public record Apuracao(BigDecimal debito, BigDecimal credito, BigDecimal liquido) {
    }

    public record Comparativo(Apuracao hoje, Apuracao ano2027,
                              @Schema(description = "(líquido 2027 - líquido hoje) / líquido hoje, em %; null sem imposto hoje")
                              BigDecimal variacaoPct) {
    }

    public record Erro(String codigo, String mensagem) {
    }

    public record ResumoNota(String id, String referenciaExterna, StatusNota status, Instant recebidaEm,
                             String chaveAcesso, Long numero, String competencia, String operacao,
                             BigDecimal valorTotal) {
    }

    @Schema(description = "Comparativo hoje x 2027 da empresa da chave (todas as notas: site e API).")
    public record ComparativoEmpresa(
            String natureza,
            String de, String ate,
            Indicadores indicadores,
            Comparativo comparativo,
            List<Mes> porMes,
            List<String> avisos) {
    }

    public record Indicadores(BigDecimal faturamento, BigDecimal compras, BigDecimal liquidoHoje,
                              BigDecimal liquido2027, BigDecimal variacaoPct, BigDecimal credito2027,
                              boolean saldoCredor, int itensPendentesRevisao) {
    }

    public record Mes(String competencia, BigDecimal faturamento, BigDecimal liquidoHoje, BigDecimal liquido2027) {
    }
}
