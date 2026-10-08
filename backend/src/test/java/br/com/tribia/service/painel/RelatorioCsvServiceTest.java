package br.com.tribia.service.painel;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class RelatorioCsvServiceTest {

    @Test
    void escapaSeparadorAspasEQuebraDeLinha() {
        assertThat(RelatorioCsvService.escapar("ARROZ TIPO 1")).isEqualTo("ARROZ TIPO 1");
        assertThat(RelatorioCsvService.escapar("A;B")).isEqualTo("\"A;B\"");
        assertThat(RelatorioCsvService.escapar("TOALHA \"KIT\"")).isEqualTo("\"TOALHA \"\"KIT\"\"\"");
        assertThat(RelatorioCsvService.escapar("linha\nquebrada")).isEqualTo("\"linha\nquebrada\"");
        assertThat(RelatorioCsvService.escapar(null)).isEmpty();
    }

    @Test
    void numerosComVirgulaDecimalSemSeparadorDeMilhar() {
        assertThat(RelatorioCsvService.num(new BigDecimal("15915.32"))).isEqualTo("15915,32");
        assertThat(RelatorioCsvService.num(new BigDecimal("-20.33"))).isEqualTo("-20,33");
        assertThat(RelatorioCsvService.num(null)).isEmpty();
    }
}
