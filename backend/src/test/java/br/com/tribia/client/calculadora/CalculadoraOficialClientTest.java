package br.com.tribia.client.calculadora;

import br.com.tribia.Fixtures;
import br.com.tribia.client.calculadora.OperacaoCalculo.ImpostoSeletivo;
import br.com.tribia.client.calculadora.OperacaoCalculo.ItemCalculo;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.net.SocketTimeoutException;
import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * As respostas em src/test/resources/calculadora/ foram gravadas da Calculadora RTC 1.5.4 real
 * (mesma operação de {@link #operacao(String)}).
 */
class CalculadoraOficialClientTest {

    static final String URL = "http://calculadora.teste/api/calculadora/regime-geral";
    static final MediaType PROBLEM_JSON = MediaType.parseMediaType("application/problem+json");

    MockRestServiceServer servidor;
    CalculadoraOficialClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://calculadora.teste/api");
        servidor = MockRestServiceServer.bindTo(builder).build();
        client = new CalculadoraOficialClient(builder.build(), Fixtures.ALIQUOTAS, new ObjectMapper());
    }

    @Test
    void enviaOperacaoComAliquotasNominaisDe2027EImpostoSeletivo() {
        servidor.expect(requestTo(URL))
                .andExpect(method(org.springframework.http.HttpMethod.POST))
                .andExpect(jsonPath("$.municipio").value(3550308))
                .andExpect(jsonPath("$.uf").value("SP"))
                .andExpect(jsonPath("$.dataHoraEmissao").value("2027-01-15T10:00:00-03:00"))
                .andExpect(jsonPath("$.itens[0].ncm").value("34025000"))
                .andExpect(jsonPath("$.itens[0].cst").value("000"))
                .andExpect(jsonPath("$.itens[0].cClassTrib").value("000001"))
                .andExpect(jsonPath("$.itens[0].baseCalculo").value(1000.00))
                .andExpect(jsonPath("$.itens[0].aliquotasNominais.cbs").value(9.43))
                .andExpect(jsonPath("$.itens[0].aliquotasNominais.ibsEstadual").value(0.05))
                .andExpect(jsonPath("$.itens[0].aliquotasNominais.ibsMunicipal").value(0.05))
                .andExpect(jsonPath("$.itens[0].impostoSeletivo").doesNotExist())
                .andExpect(jsonPath("$.itens[2].impostoSeletivo.cst").value("200"))
                .andExpect(jsonPath("$.itens[2].impostoSeletivo.cClassTrib").value("200007"))
                .andExpect(jsonPath("$.itens[2].impostoSeletivo.baseCalculo").value(479.52))
                .andRespond(withSuccess(Fixtures.recurso("calculadora/resposta-regime-geral-2027.json"),
                        MediaType.APPLICATION_JSON));

        client.calcular(operacao("34025000"));

        servidor.verify();
    }

    @Test
    void converteARespostaRealDaCalculadora() {
        servidor.expect(requestTo(URL)).andRespond(withSuccess(
                Fixtures.recurso("calculadora/resposta-regime-geral-2027.json"), MediaType.APPLICATION_JSON));

        ResultadoCalculo r = client.calcular(operacao("34025000"));

        assertThat(r.origem()).isEqualTo(OrigemCalculo.CALCULADORA);
        assertThat(r.simulado()).as("alíquotas nominais informadas por nós").isTrue();
        assertThat(r.avisos()).isEmpty();
        assertThat(r.itens()).hasSize(4);

        var integral = r.itens().get(0);
        assertThat(integral.numero()).isEqualTo(1);
        assertThat(integral.tributos().vCbs()).isEqualByComparingTo("94.30");
        assertThat(integral.tributos().vIbsUf()).isEqualByComparingTo("0.50");
        assertThat(integral.tributos().vIbsMun()).isEqualByComparingTo("0.50");
        assertThat(integral.tributos().vIs()).isEqualByComparingTo("0");
        assertThat(integral.aliquotas().pCbs()).isEqualByComparingTo("9.43");
        assertThat(integral.aliquotas().reducaoCbs()).isNull();

        var medicamento = r.itens().get(1);
        assertThat(medicamento.tributos().vCbs()).isEqualByComparingTo("37.72");
        assertThat(medicamento.aliquotas().reducaoCbs()).isEqualByComparingTo("60");
        assertThat(medicamento.aliquotas().reducaoIbs()).isEqualByComparingTo("60");

        var refrigeranteRevenda = r.itens().get(2);
        assertThat(refrigeranteRevenda.tributos().vIs()).as("IS monofásico: revenda não paga").isZero();
        assertThat(refrigeranteRevenda.tributos().vCbs()).isEqualByComparingTo("45.22");

        var refrigeranteFabricante = r.itens().get(3);
        assertThat(refrigeranteFabricante.tributos().vIs()).isEqualByComparingTo("47.95");
        assertThat(refrigeranteFabricante.aliquotas().pIs()).isEqualByComparingTo("10");
        assertThat(refrigeranteFabricante.tributos().vCbs()).as("IS entra na base").isEqualByComparingTo("49.74");
    }

    @Test
    void ncmDesconhecidoPelaCalculadoraEhReenviadoSemNcmComAviso() {
        servidor.expect(requestTo(URL))
                .andExpect(jsonPath("$.itens[0].ncm").value("34022000"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND).contentType(PROBLEM_JSON)
                        .body(Fixtures.recurso("calculadora/erro-ncm-nao-encontrada.json")));
        servidor.expect(requestTo(URL))
                .andExpect(jsonPath("$.itens[0].ncm").doesNotExist())
                .andExpect(jsonPath("$.itens[1].ncm").value("30049069"))
                .andRespond(withSuccess(Fixtures.recurso("calculadora/resposta-regime-geral-2027.json"),
                        MediaType.APPLICATION_JSON));

        ResultadoCalculo r = client.calcular(operacao("34022000"));

        servidor.verify();
        assertThat(r.itens()).hasSize(4);
        assertThat(r.avisos()).singleElement().asString().contains("NCM 34022000 não consta na tabela oficial");
    }

    @Test
    void classificacaoRecusadaViraErroRejeitadaComODetalheDaCalculadora() {
        servidor.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.NOT_FOUND).contentType(PROBLEM_JSON)
                .body(Fixtures.recurso("calculadora/erro-classificacao-nao-encontrada.json")));

        assertThatThrownBy(() -> client.calcular(operacao("34025000")))
                .isInstanceOfSatisfying(CalculadoraException.class,
                        e -> assertThat(e.getTipo()).isEqualTo(CalculadoraException.Tipo.REJEITADA))
                .hasMessageContaining("Classificação tributária não encontrada para código 999999");
    }

    @Test
    void calculadoraForaDoArViraErroIndisponivel() {
        servidor.expect(requestTo(URL)).andRespond(req -> {
            throw new SocketTimeoutException("Read timed out");
        });

        assertThatThrownBy(() -> client.calcular(operacao("34025000")))
                .isInstanceOfSatisfying(CalculadoraException.class,
                        e -> assertThat(e.getTipo()).isEqualTo(CalculadoraException.Tipo.INDISPONIVEL))
                .hasMessageContaining("não respondeu");
    }

    @Test
    void respostaComQuantidadeDeItensDiferenteEhRecusada() {
        servidor.expect(requestTo(URL)).andRespond(withSuccess("{\"objetos\":[]}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.calcular(operacao("34025000")))
                .isInstanceOfSatisfying(CalculadoraException.class,
                        e -> assertThat(e.getTipo()).isEqualTo(CalculadoraException.Tipo.INDISPONIVEL));
    }

    /** Mesma operação usada para gravar as respostas reais. */
    static OperacaoCalculo operacao(String ncmDetergente) {
        return new OperacaoCalculo("tribia-teste", OffsetDateTime.parse("2027-01-15T10:00:00-03:00"), "3550308", "SP",
                List.of(
                        new ItemCalculo(1, ncmDetergente, bd("1"), "UN", bd("1000.00"), "000", "000001", null),
                        new ItemCalculo(2, "30049069", bd("1"), "UN", bd("1000.00"), "200", "200032", null),
                        new ItemCalculo(3, "22021000", bd("48"), "UN", bd("479.52"), "000", "000001",
                                ImpostoSeletivo.REVENDA),
                        new ItemCalculo(4, "22021000", bd("48"), "UN", bd("479.52"), "000", "000001",
                                new ImpostoSeletivo("000", "000001"))));
    }

    static BigDecimal bd(String v) {
        return new BigDecimal(v);
    }
}
