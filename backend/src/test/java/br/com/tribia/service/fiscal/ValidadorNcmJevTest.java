package br.com.tribia.service.fiscal;

import br.com.tribia.dto.fiscal.ResultadoAnaliseFiscal.Pontuacao;
import br.com.tribia.dto.fiscal.ResultadoAnaliseFiscal.ResultadoVerificacao;
import br.com.tribia.dto.fiscal.ResultadoAnaliseFiscal.SituacaoValidacao;
import br.com.tribia.dto.fiscal.ResultadoAnaliseFiscal.Verificacao;
import br.com.tribia.service.fiscal.PesquisaNcmIa.Candidata;
import br.com.tribia.service.tabelas.TabelaCClassTrib;
import br.com.tribia.service.tabelas.TabelaNcmAplicavel;
import br.com.tribia.service.tabelas.TabelaNcmVigente;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Gemini x JEV: a pontuação só sinaliza; discordância ou nota baixa mandam para revisão, concordância não confirma. */
class ValidadorNcmJevTest {

    static final ValidadorNcm V = new ValidadorNcm(new TabelaNcmAplicavel(), new TabelaCClassTrib(),
            new TabelaNcmVigente(new ObjectMapper()), new BigDecimal("0.70"), new BigDecimal("0.10"), 120,
            new BigDecimal("0.50"), new BigDecimal("0.20"));
    static final LocalDate HOJE = LocalDate.of(2026, 10, 8);
    static final List<Candidata> SABONETE = List.of(
            new Candidata("34011190", "Sabões de toucador", List.of(), "", new BigDecimal("0.90")),
            new Candidata("34011900", "Outros sabões", List.of(), "", new BigDecimal("0.50")));

    static Pontuacao p(String v) {
        return new Pontuacao(new BigDecimal(v), "0 a 1", "teste");
    }

    static Verificacao jev(ValidadorNcm.Resultado r) {
        return r.validacao().verificacoes().stream().filter(v -> v.nome().equals("Avaliação da JEV AI")).findFirst().orElse(null);
    }

    @Test
    void semJevNaoHaVerificacaoDaJev() {
        var r = V.validar(SABONETE, "34011190", HOJE, Map.of());
        assertThat(jev(r)).isNull();
        assertThat(r.validacao().situacao()).isEqualTo(SituacaoValidacao.VALIDADO_VERIFICACOES);
    }

    @Test
    void concordanciaNaoPromoveNemConfirmaAClassificacao() {
        var r = V.validar(SABONETE, "34011190", HOJE, Map.of("34011190", p("0.91"), "34011900", p("0.30")));
        assertThat(jev(r).resultado()).isEqualTo(ResultadoVerificacao.OK);
        assertThat(jev(r).detalhe()).contains("não confirma a classificação fiscal");
        assertThat(r.validacao().situacao()).isEqualTo(SituacaoValidacao.VALIDADO_VERIFICACOES);
    }

    @Test
    void divergenciaEntreGeminiEJevViraAlertaEPendenciaComAsDuasNotas() {
        var r = V.validar(SABONETE, "34011190", HOJE, Map.of("34011190", p("0.40"), "34011900", p("0.85")));
        assertThat(jev(r).resultado()).isEqualTo(ResultadoVerificacao.ALERTA);
        assertThat(r.validacao().divergencias()).anyMatch(d -> d.contains("3401.19.00") && d.contains("0,85") && d.contains("0,40"));
        assertThat(r.validacao().pendencias()).anyMatch(d -> d.contains("melhor avaliada pela JEV"));
        assertThat(r.validacao().situacao()).isEqualTo(SituacaoValidacao.PENDENTE_REVISAO);
    }

    @Test
    void diferencaPequenaNaoEhDivergenciaMasNotaBaixaEhAlerta() {
        var r = V.validar(SABONETE, "34011190", HOJE, Map.of("34011190", p("0.35"), "34011900", p("0.45")));
        assertThat(jev(r).resultado()).isEqualTo(ResultadoVerificacao.ALERTA);
        assertThat(jev(r).detalhe()).contains("pouco compatível");
        assertThat(r.validacao().divergencias()).noneMatch(d -> d.contains("JEV"));
    }

    @Test
    void sugestaoSemNotaDaJevFicaComoNaoRealizada() {
        var r = V.validar(SABONETE, "34011190", HOJE, Map.of("34011900", p("0.85")));
        assertThat(jev(r).resultado()).isEqualTo(ResultadoVerificacao.NAO_REALIZADA);
    }
}
