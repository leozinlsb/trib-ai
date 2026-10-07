package br.com.tribia.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CnpjUtilTest {

    @Test
    void cnpjsDosClientesDeDemoSaoValidos() {
        assertThat(CnpjUtil.valido("10.433.218/0001-93")).isTrue();
        assertThat(CnpjUtil.valido("45723174000110")).isTrue();
        assertThat(CnpjUtil.valido("31592846000191")).isTrue();
    }

    @Test
    void rejeitaDigitoErradoTamanhoErradoERepetidos() {
        assertThat(CnpjUtil.valido("10433218000194")).isFalse();
        assertThat(CnpjUtil.valido("1043321800019")).isFalse();
        assertThat(CnpjUtil.valido("11111111111111")).isFalse();
        assertThat(CnpjUtil.valido(null)).isFalse();
    }

    @Test
    void completaDigitosEFormata() {
        assertThat(CnpjUtil.completarDigitos("104332180001")).isEqualTo("10433218000193");
        assertThat(CnpjUtil.formatar("10433218000193")).isEqualTo("10.433.218/0001-93");
    }
}
