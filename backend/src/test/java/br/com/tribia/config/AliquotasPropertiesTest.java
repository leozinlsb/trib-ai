package br.com.tribia.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/** Garante que application.properties preenche todas as alíquotas (nada fica null em produção). */
@SpringBootTest(properties = "tribia.seed.enabled=false")
class AliquotasPropertiesTest {

    @Autowired
    AliquotasProperties aliquotas;

    @Test
    void carregaAliquotasDeHojeEDe2027() {
        assertThat(aliquotas.hoje().pisNaoCumulativo()).isEqualByComparingTo("1.65");
        assertThat(aliquotas.hoje().cofinsNaoCumulativo()).isEqualByComparingTo("7.60");
        assertThat(aliquotas.hoje().cstComCredito()).containsExactlyInAnyOrder("01", "02", "03");

        assertThat(aliquotas.ano2027().cbs()).isEqualByComparingTo("9.43");
        assertThat(aliquotas.ano2027().ibsUf()).isEqualByComparingTo("0.05");
        assertThat(aliquotas.ano2027().ibsMun()).isEqualByComparingTo("0.05");
        assertThat(aliquotas.ano2027().cbsEstimativa()).isTrue();
    }
}
