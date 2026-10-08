package br.com.tribia.service.classificacao;

import br.com.tribia.service.tabelas.TabelaCClassTrib;
import br.com.tribia.service.tabelas.TabelaNcmAplicavel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class ClassificadorPorRegraTest {
    private final TabelaCClassTrib tabela = new TabelaCClassTrib();
    private final String[] porAdquirente = {"200038", "515001", "200011"};
    private final ClassificadorPorRegra regras = new ClassificadorPorRegra(new TabelaNcmAplicavel(), tabela,
            new OpcoesClassificacao(tabela, porAdquirente));

    @Test
    void associacaoUnicaDaTabelaEIndicadaComBaixaConfiancaERevisao() {
        var sugestao = regras.sugerir("04012010").orElseThrow();
        assertThat(sugestao.cClassTrib()).isEqualTo("200003");
        assertThat(sugestao.confianca()).isEqualByComparingTo(new BigDecimal("0.40"));
        assertThat(sugestao.justificativa()).contains("Anexo I", "Confirme na revisão");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"X", "100630", "00000000", "34022000", "30049099", "22029900", "10063021", "07133319"})
    void semAssociacaoOuComAmbiguidadeNaoInventaTributacaoIntegral(String ncm) {
        assertThat(regras.sugerir(ncm)).isEmpty();
    }

    @Test
    void regraDependenteDeAdquirenteNaoPodeSerFallback() {
        TabelaNcmAplicavel ncm = mock(TabelaNcmAplicavel.class);
        when(ncm.regrasPara("15091000")).thenReturn(List.of(
                new TabelaNcmAplicavel.Regra("15", "200038", "IX", "21")));
        assertThat(new ClassificadorPorRegra(ncm, tabela, new OpcoesClassificacao(tabela, porAdquirente))
                .sugerir("15091000")).isEmpty();
    }

    @Test
    void variasLinhasDoMesmoCodigoNaoSaoAmbiguidade() {
        TabelaNcmAplicavel ncm = mock(TabelaNcmAplicavel.class);
        when(ncm.regrasPara("10063021")).thenReturn(List.of(
                new TabelaNcmAplicavel.Regra("100630", "200003", "I", "1"),
                new TabelaNcmAplicavel.Regra("1006", "200003", "I", "2")));
        assertThat(new ClassificadorPorRegra(ncm, tabela, new OpcoesClassificacao(tabela, porAdquirente))
                .sugerir("10063021")).isPresent();
    }
}
