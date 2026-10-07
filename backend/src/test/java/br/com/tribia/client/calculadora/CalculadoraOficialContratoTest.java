package br.com.tribia.client.calculadora;

import br.com.tribia.Fixtures;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contrato com a Calculadora RTC REAL em http://localhost:8080/api.
 * Só roda quando ela está no ar (ver README); senão aparece como pulado.
 */
@EnabledIf("calculadoraNoAr")
class CalculadoraOficialContratoTest {

    final CalculadoraOficialClient client = new CalculadoraOficialClient(
            RestClient.builder().baseUrl("http://localhost:8080/api").build(), Fixtures.ALIQUOTAS, new ObjectMapper());

    @Test
    void calculaNaCalculadoraRealOsMesmosValoresDoMotorSimplificado() {
        ResultadoCalculo r = client.calcular(CalculadoraOficialClientTest.operacao("34025000"));

        assertThat(r.origem()).isEqualTo(OrigemCalculo.CALCULADORA);
        assertThat(r.itens()).extracting(i -> i.tributos().vCbs().toPlainString())
                .containsExactly("94.30", "37.72", "45.22", "49.74");
        assertThat(r.itens().get(3).tributos().vIs()).isEqualByComparingTo("47.95");
    }

    @Test
    void ncmExtintoNaoDerrubaOCalculo() {
        ResultadoCalculo r = client.calcular(CalculadoraOficialClientTest.operacao("34022000"));

        assertThat(r.itens()).hasSize(4);
        assertThat(r.itens().get(0).tributos().vCbs()).isEqualByComparingTo("94.30");
        assertThat(r.avisos()).hasSize(1);
    }

    static boolean calculadoraNoAr() {
        try (Socket s = new Socket()) {
            s.connect(new InetSocketAddress("localhost", 8080), 300);
            return true;
        } catch (IOException e) {
            return false;
        }
    }
}
