package br.com.tribia.seed;

import br.com.tribia.Fixtures;
import br.com.tribia.client.calculadora.CalculadoraOficialClient;
import br.com.tribia.client.calculadora.CalculadoraSimplificadaClient;
import br.com.tribia.client.calculadora.OperacaoCalculo;
import br.com.tribia.client.calculadora.OperacaoCalculo.ImpostoSeletivo;
import br.com.tribia.client.calculadora.OperacaoCalculo.ItemCalculo;
import br.com.tribia.client.calculadora.ResultadoCalculo;
import br.com.tribia.service.apuracao.RegrasApuracao;
import br.com.tribia.service.tabelas.TabelaCClassTrib;
import br.com.tribia.service.tabelas.TabelaImpostoSeletivo;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Garante que o plano B (simplificada) dá os mesmos valores da calculadora oficial para TODAS as classificações
 * usadas no seed. Só roda com a calculadora no ar (http://localhost:8080/api).
 */
@EnabledIf("br.com.tribia.client.calculadora.CalculadoraOficialContratoTest#calculadoraNoAr")
class SimplificadaVsOficialContratoTest {

    @Test
    void mesmosValoresParaCadaClassificacaoDoSeed() {
        TabelaImpostoSeletivo tabelaIs = new TabelaImpostoSeletivo();
        var oficial = new CalculadoraOficialClient(RestClient.builder().baseUrl("http://localhost:8080/api").build(),
                new ObjectMapper());
        var simplificada = new CalculadoraSimplificadaClient(new RegrasApuracao(Fixtures.ALIQUOTAS),
                new TabelaCClassTrib(), tabelaIs);

        List<ItemCalculo> itens = new ArrayList<>();
        int n = 1;
        for (var e : ClassificacoesSeed.POR_NCM.entrySet()) {
            String ncm = e.getKey();
            boolean sujeitoIs = tabelaIs.aliquota(ncm, LocalDate.of(2027, 1, 15)).isPresent();
            itens.add(new ItemCalculo(n++, ncm, BigDecimal.TEN, "UN", new BigDecimal("1234.56"), e.getValue().cst(),
                    e.getValue().cClassTrib(), sujeitoIs ? ImpostoSeletivo.REVENDA : null));
        }
        var op = new OperacaoCalculo("contrato-seed", OffsetDateTime.parse("2027-01-15T10:00:00-03:00"), "3550308",
                "SP", itens, new OperacaoCalculo.AliquotasNominais(new BigDecimal("9.43"), new BigDecimal("0.05"),
                new BigDecimal("0.05")));

        ResultadoCalculo o = oficial.calcular(op);
        ResultadoCalculo s = simplificada.calcular(op);

        assertThat(o.avisos()).as("nenhum NCM do seed pode ser desconhecido pela calculadora").isEmpty();
        for (int i = 0; i < itens.size(); i++) {
            assertThat(s.itens().get(i).tributos())
                    .as("NCM %s / %s", itens.get(i).ncm(), itens.get(i).cClassTrib())
                    .isEqualTo(o.itens().get(i).tributos());
        }
    }
}
