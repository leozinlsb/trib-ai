package br.com.tribia.dto.fiscal;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;
import java.util.List;

/**
 * Tudo o que a análise produz, como o front espera (frontend/docs/inteligencia-fiscal-api.md). Guardado em JSON na
 * AnaliseFiscal e devolvido no detalhe.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ResultadoAnaliseFiscal(Resultado resultado, Fundamentacao fundamentacao, List<Alternativa> alternativas,
                                     Validacao validacao, List<Fonte> fontes, List<RevisaoHumana> revisoes) {

    public ResultadoAnaliseFiscal(Resultado resultado, Fundamentacao fundamentacao, List<Alternativa> alternativas,
                                  Validacao validacao, List<Fonte> fontes) {
        this(resultado, fundamentacao, alternativas, validacao, fontes, null);
    }

    /** Nova revisão registrada no fim da lista; o resultado automático continua igual (evidência). */
    public ResultadoAnaliseFiscal comRevisao(RevisaoHumana r) {
        List<RevisaoHumana> todas = new java.util.ArrayList<>(revisoes == null ? List.of() : revisoes);
        todas.add(r);
        return new ResultadoAnaliseFiscal(resultado, fundamentacao, alternativas, validacao, fontes, List.copyOf(todas));
    }

    /**
     * Decisão de uma pessoa sobre a sugestão: ACEITA (mantém a NCM sugerida) ou ALTERADA (escolheu outra). É registro
     * interno de revisão, não parecer nem decisão da Receita Federal.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record RevisaoHumana(String decisao, String ncm, String ncmSugerida, String observacao, String revisadaPor,
                                String revisadaEm) {
    }

    public enum SituacaoValidacao {
        VALIDADO_VERIFICACOES, PENDENTE_REVISAO, INFORMACOES_INSUFICIENTES, INCONSISTENCIA
    }

    public enum ResultadoVerificacao {
        OK, ALERTA, FALHA, NAO_REALIZADA
    }

    public record Resultado(String ncm, String descricaoOficial, SituacaoValidacao situacaoValidacao,
                            String analisadaEm) {
    }

    public record Fundamentacao(List<String> caracteristicas, List<String> motivos, List<String> regrasConsideradas,
                                List<String> observacoes, List<String> limitacoes) {
    }

    /** Pontuação de compatibilidade da JEV AI: valor, escala e o que ela mede (não é probabilidade de acerto). */
    public record Pontuacao(BigDecimal valor, String escala, String significado) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Alternativa(String ncm, String descricao, String avaliacao, Pontuacao pontuacao) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Verificacao(String nome, ResultadoVerificacao resultado, String detalhe) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Vigencia(String inicio, String fim) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Validacao(SituacaoValidacao situacao, String situacaoNcm, Vigencia vigencia,
                            List<Verificacao> verificacoes, List<String> regrasAplicaveis, List<String> divergencias,
                            List<String> pendencias) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Fonte(String titulo, String identificacao, String versao, String trecho, String url) {
    }
}
