package br.com.tribia.service.tabelas;

import br.com.tribia.model.RegimeTributario;
import br.com.tribia.service.apuracao.ParametrosClassificacao;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TabelasOficiaisTest {

    static final TabelaCClassTrib TABELA = new TabelaCClassTrib();
    static final TabelaImpostoSeletivo IS = new TabelaImpostoSeletivo();

    @Test
    void carregaATabelaOficialCompleta() {
        assertThat(TABELA.todos()).hasSize(142);
        assertThat(TABELA.buscar("200003")).get()
                .satisfies(c -> {
                    assertThat(c.cst()).isEqualTo("200");
                    assertThat(c.nome()).contains("Anexo I");
                    assertThat(c.reducaoCbs()).isEqualByComparingTo("100");
                    assertThat(c.aceitaNfe()).isTrue();
                });
    }

    @Test
    void validaCstECodigoJuntos() {
        assertThat(TABELA.validoParaNfe("200", "200003")).isTrue();
        assertThat(TABELA.validoParaNfe("000", "000001")).isTrue();
        assertThat(TABELA.validoParaNfe("000", "200003")).as("CST não confere").isFalse();
        assertThat(TABELA.validoParaNfe("200", "999999")).as("código inexistente").isFalse();
        assertThat(TABELA.validoParaNfe("000", "000002")).as("só NFS-e (exploração de via)").isFalse();
    }

    @Test
    void derivaORegimeEADescricaoDaReducao() {
        assertThat(TABELA.buscar("000001").orElseThrow().regime()).isEqualTo(RegimeTributario.INTEGRAL);
        assertThat(TABELA.buscar("200003").orElseThrow().regime()).isEqualTo(RegimeTributario.ALIQUOTA_ZERO);
        var medicamento = TABELA.buscar("200032").orElseThrow();
        assertThat(medicamento.regime()).isEqualTo(RegimeTributario.REDUZIDA);
        assertThat(medicamento.descricaoRegime()).isEqualTo("Redução de 60%");
        assertThat(TABELA.buscar("410004").orElseThrow().regime()).as("exportação").isEqualTo(RegimeTributario.SEM_INCIDENCIA);
    }

    @Test
    void parametrosParaOCalculoSimplificado() {
        ParametrosClassificacao p = TABELA.parametros("200034", BigDecimal.ZERO);
        assertThat(p.reducaoCbs()).isEqualByComparingTo("60");
        assertThat(p.reducaoIbs()).isEqualByComparingTo("60");

        assertThat(TABELA.parametros("410004", BigDecimal.ZERO).reducaoCbs())
                .as("sem incidência vira redução total").isEqualByComparingTo("100");

        String aliquotaFixa = TABELA.todos().stream().filter(c -> c.regime() == RegimeTributario.OUTRO)
                .findFirst().orElseThrow().codigo();
        assertThatThrownBy(() -> TABELA.parametros(aliquotaFixa, BigDecimal.ZERO))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("calculadora oficial");
        assertThatThrownBy(() -> TABELA.parametros("999999", BigDecimal.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void impostoSeletivoPorNcmEVigencia() {
        assertThat(IS.quantidadeDeRegras()).isGreaterThan(300);
        assertThat(IS.aliquota("22021000", LocalDate.of(2027, 1, 15))).get().satisfies(a -> assertThat(a).isEqualByComparingTo("10"));
        assertThat(IS.aliquota("22021000", LocalDate.of(2026, 10, 7))).as("IS começa em 2027").isEmpty();
        assertThat(IS.aliquota("34025000", LocalDate.of(2027, 1, 15))).isEmpty();
        assertThat(IS.aliquota(null, LocalDate.of(2027, 1, 15))).isEmpty();
    }
}
