package br.com.tribia.client.calculadora;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Contrato JSON de POST /calculadora/regime-geral da Calculadora RTC (versão 1.5.4).
 * Espelha OperacaoInput (entrada) e ROCDomain (saída) do código-fonte oficial; só os campos que usamos.
 * Na resposta, os valores vêm como texto ("94.30"), que o Jackson converte para BigDecimal.
 */
final class RegimeGeralApi {

    private RegimeGeralApi() {
    }

    // ---------------- entrada ----------------

    /**
     * @param dataHoraEmissao texto no formato yyyy-MM-dd'T'HH:mm:ssXXX, exigido pela calculadora. Vai como String
     *                        para não depender da configuração de datas do ObjectMapper de quem chama.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record Requisicao(String id, String versao, String dataHoraEmissao, Long municipio, String uf,
                      List<ItemRequisicao> itens) {

        static String data(OffsetDateTime d) {
            return d.truncatedTo(java.time.temporal.ChronoUnit.SECONDS)
                    .format(java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record ItemRequisicao(Integer numero, String ncm, BigDecimal quantidade, String unidade, String cst,
                          String cClassTrib, BigDecimal baseCalculo, ImpostoSeletivoRequisicao impostoSeletivo,
                          AliquotasNominais aliquotasNominais) {

        ItemRequisicao semNcm() {
            return new ItemRequisicao(numero, null, quantidade, unidade, cst, cClassTrib, baseCalculo,
                    impostoSeletivo, aliquotasNominais);
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record ImpostoSeletivoRequisicao(String cst, String cClassTrib, BigDecimal baseCalculo, String unidade,
                                     BigDecimal quantidade) {
    }

    /** Obrigatórias para fato gerador a partir de 01/01/2027 (a lei ainda não fixou as alíquotas). */
    record AliquotasNominais(BigDecimal cbs, BigDecimal ibsEstadual, BigDecimal ibsMunicipal) {
    }

    // ---------------- saída ----------------

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Resposta(List<Objeto> objetos) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Objeto(Integer nObj, TribCalc tribCalc, boolean calculoSimulado) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record TribCalc(@JsonProperty("IS") ImpostoSeletivo impostoSeletivo, @JsonProperty("IBSCBS") IbsCbs ibsCbs) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record IbsCbs(@JsonProperty("CST") String cst, String cClassTrib, GrupoIbsCbs gIBSCBS) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record GrupoIbsCbs(BigDecimal vBC, IbsUf gIBSUF, IbsMun gIBSMun, BigDecimal vIBS, Cbs gCBS) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record IbsUf(BigDecimal pIBSUF, Reducao gRed, BigDecimal vIBSUF) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record IbsMun(BigDecimal pIBSMun, Reducao gRed, BigDecimal vIBSMun) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Cbs(BigDecimal pCBS, Reducao gRed, BigDecimal vCBS) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Reducao(BigDecimal pRedAliq, BigDecimal pAliqEfet) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ImpostoSeletivo(@JsonProperty("CSTIS") String cst, String cClassTribIS, BigDecimal vBCIS,
                           BigDecimal pIS, BigDecimal vIS) {
    }

    /** Erro no formato ProblemDetail. "type" termina com o código do erro (ex.: .../ncm-nao-encontrada). */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record Problema(String type, String title, Integer status, String detail) {

        boolean eNcmNaoEncontrada() {
            return type != null && type.endsWith("/ncm-nao-encontrada");
        }
    }
}
