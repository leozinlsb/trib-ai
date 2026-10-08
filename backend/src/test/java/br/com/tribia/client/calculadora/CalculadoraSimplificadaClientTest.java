package br.com.tribia.client.calculadora;

import br.com.tribia.Fixtures;
import br.com.tribia.client.calculadora.OperacaoCalculo.ItemCalculo;
import br.com.tribia.service.apuracao.RegrasApuracao;
import br.com.tribia.service.tabelas.TabelaCClassTrib;
import br.com.tribia.service.tabelas.TabelaImpostoSeletivo;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Mesma operação das respostas gravadas da calculadora oficial: os valores têm de bater centavo por centavo. */
class CalculadoraSimplificadaClientTest {

    final CalculadoraSimplificadaClient client = new CalculadoraSimplificadaClient(
            new RegrasApuracao(Fixtures.ALIQUOTAS), new TabelaCClassTrib(), new TabelaImpostoSeletivo());

    @Test
    void bateComACalculadoraOficial() {
        ResultadoCalculo r = client.calcular(CalculadoraOficialClientTest.operacao("34025000"));

        assertThat(r.origem()).isEqualTo(OrigemCalculo.SIMPLIFICADA);
        assertThat(r.simulado()).isTrue();
        // valores da resposta real gravada em calculadora/resposta-regime-geral-2027.json
        assertThat(r.itens()).extracting(i -> i.tributos().vCbs().toPlainString())
                .containsExactly("94.30", "37.72", "45.22", "49.74");
        assertThat(r.itens()).extracting(i -> i.tributos().vIbsUf().toPlainString())
                .containsExactly("0.50", "0.20", "0.24", "0.26");
        assertThat(r.itens()).extracting(i -> i.tributos().vIs().toPlainString())
                .containsExactly("0.00", "0.00", "0.00", "47.95");
        assertThat(r.itens().get(1).aliquotas().reducaoCbs()).isEqualByComparingTo("60");
        assertThat(r.itens().get(0).aliquotas().reducaoCbs()).isNull();
        assertThat(r.itens().get(2).aliquotas().pIs()).as("revenda: no campo do IS, mas sem cobrança")
                .isEqualByComparingTo("10");
    }

    @Test
    void codigoForaDoCalculoSimplificadoEhRecusado() {
        var op = CalculadoraOficialClientTest.operacao("34025000");
        var comCodigoInvalido = new OperacaoCalculo(op.id(), op.dataFatoGerador(), op.codigoMunicipio(), op.uf(),
                List.of(new ItemCalculo(1, "34025000", op.itens().get(0).quantidade(), "UN",
                        op.itens().get(0).baseCalculo(), "200", "999999", null)), op.aliquotas());

        assertThatThrownBy(() -> client.calcular(comCodigoInvalido))
                .isInstanceOfSatisfying(CalculadoraException.class,
                        e -> assertThat(e.getTipo()).isEqualTo(CalculadoraException.Tipo.REJEITADA))
                .hasMessageContaining("999999");
    }
}
