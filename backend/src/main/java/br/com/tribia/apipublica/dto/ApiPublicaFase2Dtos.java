package br.com.tribia.apipublica.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Contrato público v1, fase 2: classificação de produtos avulsos (sem nota) e simulação do cálculo de 2027 (sem
 * gravar nada). Mesmas regras da plataforma, que ainda aguardam validação profissional: toda resposta sai marcada
 * como sugestão (classificação) ou projeção (cálculo).
 */
public final class ApiPublicaFase2Dtos {

    private ApiPublicaFase2Dtos() {
    }

    /** Valor de "natureza" da classificação avulsa. */
    public static final String NATUREZA_SUGESTAO = "SUGESTAO_AUTOMATICA";

    // ---------------- classificação ----------------

    @Schema(description = "Produtos para classificar no regime da reforma (CST e cClassTrib de IBS/CBS).")
    public record SolicitacaoClassificacao(
            @NotEmpty(message = "Envie ao menos um produto.") @Size(max = 50, message = "No máximo 50 produtos por chamada.")
            List<@Valid @NotNull(message = "Produto vazio.") ProdutoClassificar> produtos) {
    }

    public record ProdutoClassificar(
            @Schema(description = "Identificador do produto no sistema de origem; volta na resposta", example = "SKU-123")
            @Size(max = 100, message = "referencia com até 100 caracteres.") String referencia,
            @Schema(example = "22021000", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotBlank(message = "Informe o NCM.") @Pattern(regexp = "^\\d{4}\\.?\\d{2}\\.?\\d{2}$", message = "NCM com 8 dígitos.")
            String ncm,
            @Schema(example = "REFRIGERANTE COLA 2L", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotBlank(message = "Informe a descrição.") @Size(min = 3, max = 500, message = "Descrição com 3 a 500 caracteres.")
            String descricao,
            @Size(max = 6, message = "unidade com até 6 caracteres.") String unidade,
            @DecimalMin(value = "0", message = "valorUnitario não pode ser negativo.") BigDecimal valorUnitario) {
    }

    @Schema(description = """
            Pedido de classificação. EM_PROCESSAMENTO: produtos que o cache não resolveu estão na IA (consulte
            GET /api/v1/classificacoes/{id} até finalizada = true). CONCLUIDA: produtos com sugestão (ou
            SEM_CLASSIFICACAO). FALHOU: erro no processamento (ver erro.mensagem).""")
    public record RespostaClassificacao(
            @Schema(description = "Id público (UUID) do pedido") String id,
            @Schema(description = "EM_PROCESSAMENTO, CONCLUIDA ou FALHOU") String status,
            boolean finalizada,
            Instant recebidaEm,
            Instant finalizadaEm,
            @Schema(description = "Sempre SUGESTAO_AUTOMATICA: confirme na plataforma antes de usar em documento fiscal")
            String natureza,
            @Schema(description = "Na ordem dos produtos enviados; vazio enquanto EM_PROCESSAMENTO")
            List<ProdutoClassificado> produtos,
            @Schema(description = "Produtos distintos reservados para a IA (contam na cota diária)")
            int itensClassificadosPorIa,
            ApiPublicaNotasDtos.Erro erro,
            List<String> avisos) {
    }

    public record ProdutoClassificado(
            String referencia, String ncm, String descricao,
            @Schema(description = "CONFIRMADA, PENDENTE_REVISAO ou SEM_CLASSIFICACAO (mesma regra das notas)")
            String situacao,
            String cst, String cClassTrib, String nomeCClassTrib, String regime, String descricaoRegime,
            @Schema(description = "CACHE, IA ou REGRA") String origem,
            BigDecimal confianca, String justificativa) {
    }

    // ---------------- simulação de cálculo ----------------

    @Schema(description = "Itens para calcular CBS/IBS/IS de 2027 sem nota. Nada é gravado.")
    public record SolicitacaoSimulacao(
            @Schema(description = "VENDA (débito) ou COMPRA (crédito)", example = "VENDA",
                    requiredMode = Schema.RequiredMode.REQUIRED)
            @NotBlank(message = "Informe a operação (VENDA ou COMPRA).")
            @Pattern(regexp = "^(VENDA|COMPRA)$", message = "operacao deve ser VENDA ou COMPRA.") String operacao,
            @Schema(description = "UF de destino; vazio = UF da empresa", example = "SP")
            @Pattern(regexp = "^$|^[A-Z]{2}$", message = "ufDestino com 2 letras maiúsculas.") String ufDestino,
            @Schema(description = "Código IBGE (7 dígitos) do município de destino; vazio = o da empresa", example = "3550308")
            @Pattern(regexp = "^$|^\\d{7}$", message = "municipioDestino com 7 dígitos (IBGE).") String municipioDestino,
            @Schema(description = "Cenário: alíquota da CBS em %; vazio = a configurada", example = "8.8")
            @DecimalMin(value = "0.01", message = "aliquotaCbs entre 0,01 e 30.")
            @DecimalMax(value = "30", message = "aliquotaCbs entre 0,01 e 30.") BigDecimal aliquotaCbs,
            @NotEmpty(message = "Envie ao menos um item.") @Size(max = 100, message = "No máximo 100 itens por chamada.")
            List<@Valid @NotNull(message = "Item vazio.") ItemSimular> itens) {
    }

    public record ItemSimular(
            @Size(max = 100, message = "referencia com até 100 caracteres.") String referencia,
            @Schema(example = "22021000")
            @Pattern(regexp = "^$|^\\d{4}\\.?\\d{2}\\.?\\d{2}$", message = "NCM com 8 dígitos.") String ncm,
            @Schema(description = "CST de IBS/CBS; vazio = o do cClassTrib na tabela oficial", example = "000")
            @Pattern(regexp = "^$|^\\d{3}$", message = "cst com 3 dígitos.") String cst,
            @Schema(example = "000001", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotBlank(message = "Informe o cClassTrib.") @Pattern(regexp = "^\\d{6}$", message = "cClassTrib com 6 dígitos.")
            String cClassTrib,
            @DecimalMin(value = "0", inclusive = false, message = "quantidade deve ser positiva.") BigDecimal quantidade,
            @Size(max = 6, message = "unidade com até 6 caracteres.") String unidade,
            @Schema(description = "Valor da operação do item (como o vProd líquido da nota)", example = "959.04",
                    requiredMode = Schema.RequiredMode.REQUIRED)
            @NotNull(message = "Informe o valor.") @DecimalMin(value = "0", inclusive = false, message = "valor deve ser positivo.")
            BigDecimal valor,
            @Schema(description = "ICMS do item; sai da base de 2027 (LC 214, art. 12, § 2º)", example = "172.63")
            @DecimalMin(value = "0", message = "icms não pode ser negativo.") BigDecimal icms,
            @DecimalMin(value = "0", message = "pis não pode ser negativo.") BigDecimal pis,
            @DecimalMin(value = "0", message = "cofins não pode ser negativo.") BigDecimal cofins) {
    }

    public record RespostaSimulacao(
            @Schema(description = "Sempre PROJECAO_PENDENTE_VALIDACAO") String natureza,
            String operacao,
            @Schema(description = "CALCULADORA (oficial da Receita) ou SIMPLIFICADA") String origemCalculo,
            AliquotasNominais aliquotasNominais,
            List<ItemSimulado> itens,
            Totais totais,
            List<String> avisos) {
    }

    @Schema(description = "Alíquotas nominais (%) usadas; a CBS de 2027 ainda é estimativa")
    public record AliquotasNominais(BigDecimal cbs, BigDecimal ibsUf, BigDecimal ibsMun) {
    }

    public record ItemSimulado(
            String referencia, String ncm, String cst, String cClassTrib, String regime, String descricaoRegime,
            @Schema(description = "Base de 2027: valor menos ICMS, PIS e Cofins informados") BigDecimal base,
            BigDecimal cbs, BigDecimal ibsUf, BigDecimal ibsMun, BigDecimal impostoSeletivo,
            boolean sujeitoImpostoSeletivo,
            @Schema(description = "Alíquotas efetivas aplicadas (%), já com reduções") BigDecimal aliquotaCbs,
            BigDecimal aliquotaIbsUf, BigDecimal aliquotaIbsMun, BigDecimal reducaoCbs, BigDecimal reducaoIbs,
            @Schema(description = "VENDA: CBS + IBS + IS a recolher") BigDecimal debito,
            @Schema(description = "COMPRA: CBS + IBS a creditar (IS não gera crédito)") BigDecimal credito) {
    }

    public record Totais(BigDecimal base, BigDecimal cbs, BigDecimal ibs, BigDecimal impostoSeletivo, BigDecimal debito,
                         BigDecimal credito) {
    }
}
